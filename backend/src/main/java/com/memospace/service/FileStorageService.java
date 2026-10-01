package com.memospace.service;

import com.memospace.api.ApiException;
import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final long MAX_FILE_BYTES = 30L * 1024 * 1024;
    private static final long MAX_SOURCE_IMAGE_PIXELS = 50_000_000L;
    private final JdbcTemplate jdbc;
    private final PermissionService permission;
    private final String mode;
    private final Path localRoot;
    private final String bucket;
    private final MinioClient minio;
    private final long userQuotaBytes;
    private final long thumbnailMaxPixels;

    public FileStorageService(JdbcTemplate jdbc, PermissionService permission,
                              @Value("${app.storage.mode}") String mode,
                              @Value("${app.storage.local-path}") String localPath,
                              @Value("${app.storage.endpoint}") String endpoint,
                              @Value("${app.storage.access-key}") String accessKey,
                              @Value("${app.storage.secret-key}") String secretKey,
                              @Value("${app.storage.bucket}") String bucket,
                              @Value("${app.storage.user-quota-bytes:5368709120}") long userQuotaBytes,
                              @Value("${app.storage.thumbnail-max-pixels:921600}") long thumbnailMaxPixels) {
        this.jdbc = jdbc;
        this.permission = permission;
        this.mode = mode;
        this.localRoot = Path.of(localPath).toAbsolutePath().normalize();
        this.bucket = bucket;
        this.minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.userQuotaBytes = Math.max(MAX_FILE_BYTES, userQuotaBytes);
        this.thumbnailMaxPixels = Math.max(160_000L, thumbnailMaxPixels);
        ImageIO.setUseCache(false);
    }

    public Map<String, Object> upload(long userId, MultipartFile file) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择文件");
        if (file.getSize() > MAX_FILE_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "单个文件不能超过 30MB");
        ensureQuota(userId, Math.max(0, file.getSize()));
        Path originalTemp = null;
        Path thumbnailTemp = null;
        String originalKey = null;
        String thumbnailKey = null;
        try {
            originalTemp = Files.createTempFile("memospace-upload-", ".part");
            long actualSize = copyWithLimit(file.getInputStream(), originalTemp, MAX_FILE_BYTES);
            if (actualSize == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择文件");
            ensureQuota(userId, actualSize);
            String mime = detectMime(readHeader(originalTemp, 16));
            if (mime == null) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 JPEG、PNG、WebP、GIF、MP4 和 WebM");
            String ext = extension(mime);
            LocalDate today = LocalDate.now();
            originalKey = userId + "/" + today.getYear() + "/" + today.getMonthValue() + "/" + UUID.randomUUID() + ext;
            ImageResult image;
            try {
                image = createThumbnail(originalTemp, mime);
            } catch (ApiException ex) {
                throw ex;
            } catch (Exception ex) {
                // A valid supported upload must never be lost merely because a JVM image codec
                // cannot decode it. The original remains private and preview falls back to it.
                log.debug("Thumbnail generation skipped for {}", originalKey, ex);
                image = null;
            }
            if (image != null && image.thumbnail() != null) {
                thumbnailTemp = image.thumbnail();
                thumbnailKey = userId + "/thumbnails/" + today.getYear() + "/" + today.getMonthValue() + "/" + UUID.randomUUID() + ".jpg";
            }
            putObject(originalKey, mime, originalTemp, actualSize);
            if (thumbnailTemp != null) putObject(thumbnailKey, "image/jpeg", thumbnailTemp, Files.size(thumbnailTemp));
            String originalName = Path.of(file.getOriginalFilename() == null ? "memory" + ext : file.getOriginalFilename()).getFileName().toString();
            long thumbnailSize = thumbnailTemp == null ? 0 : Files.size(thumbnailTemp);
            Integer width = image == null ? null : image.width();
            Integer height = image == null ? null : image.height();
            long id = JdbcIds.insert(jdbc,
                    "INSERT INTO file_record(owner_id,object_key,original_name,mime_type,file_size,is_private,thumbnail_object_key,thumbnail_size,image_width,image_height) VALUES(?,?,?,?,?,TRUE,?,?,?,?)",
                    userId, originalKey, originalName, mime, actualSize, thumbnailKey, thumbnailSize, width, height);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("name", originalName);
            result.put("mimeType", mime);
            result.put("size", actualSize);
            result.put("width", width);
            result.put("height", height);
            result.put("hasThumbnail", thumbnailKey != null);
            result.put("contentUrl", "/api/files/" + id + "/content");
            result.put("thumbnailUrl", "/api/files/" + id + "/thumbnail");
            return result;
        } catch (ApiException ex) {
            removeQuietly(originalKey);
            removeQuietly(thumbnailKey);
            throw ex;
        } catch (Exception ex) {
            removeQuietly(originalKey);
            removeQuietly(thumbnailKey);
            log.error("Failed to store upload for user {}", userId, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "文件存储暂时不可用");
        } finally {
            deleteTemp(originalTemp);
            deleteTemp(thumbnailTemp);
        }
    }

    public StoredFile load(long userId, long fileId) { return load(userId, fileId, false); }
    public StoredFile loadThumbnail(long userId, long fileId) { return load(userId, fileId, true); }

    private StoredFile load(long userId, long fileId, boolean thumbnail) {
        Map<String, Object> file = requireAuthorizedFile(userId, fileId);
        Object thumbnailValue = file.get("thumbnail_object_key");
        boolean useThumbnail = thumbnail && thumbnailValue != null && !String.valueOf(thumbnailValue).isBlank();
        String key = useThumbnail ? String.valueOf(thumbnailValue) : String.valueOf(file.get("object_key"));
        String mime = useThumbnail ? "image/jpeg" : String.valueOf(file.get("mime_type"));
        String name = useThumbnail ? "preview-" + file.get("original_name") + ".jpg" : String.valueOf(file.get("original_name"));
        Number storedSize = (Number) file.get(useThumbnail ? "thumbnail_size" : "file_size");
        long size = storedSize == null ? 0 : storedSize.longValue();
        try {
            if ("minio".equalsIgnoreCase(mode)) {
                Resource resource = new InputStreamResource(minio.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build()));
                return new StoredFile(resource, mime, name, size);
            }
            Path path = safeLocalPath(key);
            if (!Files.exists(path)) {
                if (useThumbnail) return load(userId, fileId, false);
                throw new ApiException(HttpStatus.NOT_FOUND, "文件已丢失");
            }
            return new StoredFile(new FileSystemResource(path), mime, name, size);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            if (useThumbnail) return load(userId, fileId, false);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "无法读取媒体文件");
        }
    }

    private Map<String, Object> requireAuthorizedFile(long userId, long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM file_record WHERE id=?", fileId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "文件不存在");
        Map<String, Object> file = rows.get(0);
        long owner = ((Number) file.get("owner_id")).longValue();
        if (owner == userId) return file;
        List<Map<String, Object>> memories = jdbc.queryForList("SELECT mm.memory_id FROM memory_media mm WHERE mm.object_key=?", file.get("object_key"));
        boolean canViewMemory = memories.stream().anyMatch(m -> permission.canViewMemory(userId, ((Number) m.get("memory_id")).longValue()));
        Integer reminderLinks = jdbc.queryForObject("SELECT COUNT(*) FROM reminder r JOIN reminder_participant rp ON rp.reminder_id=r.id AND rp.user_id=? WHERE r.image_file_id=? AND rp.acceptance_status IN ('PENDING','ACCEPTED')", Integer.class, userId, fileId);
        String contentPath = "/api/files/" + fileId + "/content";
        Integer avatarLinks = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE avatar=?", Integer.class, contentPath);
        Integer appearanceLinks = jdbc.queryForObject("SELECT COUNT(*) FROM user_appearance WHERE user_id=? AND background_file_id=?", Integer.class, userId, fileId);
        Integer spaceLinks = jdbc.queryForObject("SELECT COUNT(*) FROM space s JOIN space_member sm ON sm.space_id=s.id WHERE sm.user_id=? AND s.background_file_id=?", Integer.class, userId, fileId);
        boolean canViewOther = (reminderLinks != null && reminderLinks > 0) || (avatarLinks != null && avatarLinks > 0) ||
                (appearanceLinks != null && appearanceLinks > 0) || (spaceLinks != null && spaceLinks > 0);
        if (!canViewMemory && !canViewOther) throw new ApiException(HttpStatus.FORBIDDEN, "你没有权限查看该私密文件");
        return file;
    }

    public StoredFile loadReportEvidence(long reportId, long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT fr.owner_id FROM content_report r JOIN memory_media mm ON r.target_type='MEMORY' AND mm.memory_id=r.target_id JOIN file_record fr ON fr.object_key=mm.object_key WHERE r.id=? AND fr.id=?", reportId, fileId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "该文件不属于这条举报证据");
        return load(((Number) rows.get(0).get("owner_id")).longValue(), fileId);
    }

    public List<StoredObject> objectsForMemory(long memoryId) {
        return jdbc.query("SELECT DISTINCT fr.id,fr.object_key,fr.thumbnail_object_key FROM memory_media mm JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=?",
                (rs, rowNum) -> new StoredObject(rs.getLong("id"), rs.getString("object_key"), rs.getString("thumbnail_object_key")), memoryId);
    }

    public void deleteIfUnreferenced(StoredObject object) {
        String contentPath = "/api/files/" + object.fileId() + "/content";
        Integer references = referenceCount(object.fileId(), object.objectKey(), contentPath);
        if (references != null && references > 0) return;
        if (jdbc.update("DELETE FROM file_record WHERE id=? AND object_key=?", object.fileId(), object.objectKey()) == 0) return;
        removeQuietly(object.objectKey());
        removeQuietly(object.thumbnailObjectKey());
    }

    public int cleanupUnreferencedUploads(int retentionHours, int limit) {
        List<Map<String, Object>> candidates = jdbc.queryForList("SELECT id,object_key,thumbnail_object_key FROM file_record WHERE created_at<? ORDER BY id LIMIT ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(Math.max(1, retentionHours))), Math.max(1, Math.min(1000, limit)));
        int removed = 0;
        for (Map<String, Object> row : candidates) {
            long fileId = ((Number) row.get("id")).longValue();
            String key = String.valueOf(row.get("object_key"));
            Integer references = referenceCount(fileId, key, "/api/files/" + fileId + "/content");
            if (references != null && references > 0) continue;
            if (jdbc.update("DELETE FROM file_record WHERE id=?", fileId) == 1) {
                removeQuietly(key);
                if (row.get("thumbnail_object_key") != null) removeQuietly(String.valueOf(row.get("thumbnail_object_key")));
                removed++;
            }
        }
        return removed;
    }

    public Map<String, Object> usage(long userId) {
        Map<String, Object> sums = jdbc.queryForMap("SELECT COUNT(*) AS file_count,COALESCE(SUM(file_size+thumbnail_size),0) AS used_bytes FROM file_record WHERE owner_id=?", userId);
        long used = ((Number) sums.get("used_bytes")).longValue();
        return Map.of("usedBytes", used, "quotaBytes", userQuotaBytes, "remainingBytes", Math.max(0, userQuotaBytes - used), "fileCount", ((Number) sums.get("file_count")).longValue());
    }

    public Map<String, Object> adminStats() {
        Map<String, Object> result = new LinkedHashMap<>(jdbc.queryForMap("SELECT COUNT(*) AS file_count,COUNT(DISTINCT owner_id) AS owner_count,COALESCE(SUM(file_size),0) AS original_bytes,COALESCE(SUM(thumbnail_size),0) AS thumbnail_bytes FROM file_record"));
        result.put("topUsers", jdbc.queryForList("SELECT f.owner_id,u.public_id,u.nickname,COUNT(*) AS file_count,COALESCE(SUM(f.file_size+f.thumbnail_size),0) AS used_bytes FROM file_record f JOIN user_account u ON u.id=f.owner_id GROUP BY f.owner_id,u.public_id,u.nickname ORDER BY used_bytes DESC LIMIT 20"));
        result.put("quotaBytesPerUser", userQuotaBytes);
        return result;
    }

    private Integer referenceCount(long fileId, String key, String contentPath) {
        return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM memory_media WHERE object_key=?) + (SELECT COUNT(*) FROM reminder WHERE image_file_id=?) + (SELECT COUNT(*) FROM user_appearance WHERE background_file_id=?) + (SELECT COUNT(*) FROM space WHERE background_file_id=?) + (SELECT COUNT(*) FROM user_account WHERE avatar=?)", Integer.class, key, fileId, fileId, fileId, contentPath);
    }

    private void ensureQuota(long userId, long incomingBytes) {
        Long used = jdbc.queryForObject("SELECT COALESCE(SUM(file_size+thumbnail_size),0) FROM file_record WHERE owner_id=?", Long.class, userId);
        if ((used == null ? 0 : used) + incomingBytes > userQuotaBytes) throw new ApiException(HttpStatus.INSUFFICIENT_STORAGE, "个人存储空间已满，请先删除不需要的内容");
    }

    private long copyWithLimit(InputStream input, Path target, long limit) throws Exception {
        long total = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = input; OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "单个文件不能超过 30MB");
                out.write(buffer, 0, read);
            }
        }
        return total;
    }

    private byte[] readHeader(Path path, int max) throws Exception {
        try (InputStream input = Files.newInputStream(path)) { return input.readNBytes(max); }
    }

    private ImageResult createThumbnail(Path source, String mime) throws Exception {
        if (!(mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/gif"))) return null;
        int width;
        int height;
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try { reader.setInput(input, true, true); width = reader.getWidth(0); height = reader.getHeight(0); }
            finally { reader.dispose(); }
        }
        if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_IMAGE_PIXELS) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "图片尺寸过大，请压缩后再上传");
        BufferedImage original = ImageIO.read(source.toFile());
        if (original == null) return new ImageResult(width, height, null);
        double scale = Math.min(1.0, Math.sqrt((double) thumbnailMaxPixels / ((double) width * height)));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(new Color(247, 244, 239));
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally { graphics.dispose(); }
        Path thumbnail = Files.createTempFile("memospace-thumb-", ".jpg");
        if (!ImageIO.write(output, "jpg", thumbnail.toFile())) { Files.deleteIfExists(thumbnail); return new ImageResult(width, height, null); }
        return new ImageResult(width, height, thumbnail);
    }

    private void putObject(String key, String mime, Path source, long size) throws Exception {
        if ("minio".equalsIgnoreCase(mode)) {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            try (InputStream input = Files.newInputStream(source)) { minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key).contentType(mime).stream(input, size, -1).build()); }
            return;
        }
        Path target = safeLocalPath(key);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void removeQuietly(String key) {
        if (key == null || key.isBlank()) return;
        try {
            if ("minio".equalsIgnoreCase(mode)) minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
            else Files.deleteIfExists(safeLocalPath(key));
        } catch (Exception ignored) { }
    }

    private void deleteTemp(Path path) { if (path != null) try { Files.deleteIfExists(path); } catch (Exception ignored) { } }
    private Path safeLocalPath(String key) {
        Path target = localRoot.resolve(key).normalize();
        if (!target.startsWith(localRoot)) throw new ApiException(HttpStatus.BAD_REQUEST, "非法文件路径");
        return target;
    }

    private String detectMime(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return "image/jpeg";
        if (b.length >= 8 && b[0] == (byte) 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47) return "image/png";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F') return "image/gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "video/mp4";
        if (b.length >= 4 && b[0] == 0x1a && b[1] == 0x45 && b[2] == (byte) 0xdf && b[3] == (byte) 0xa3) return "video/webm";
        return null;
    }

    private String extension(String mime) {
        return switch (mime) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "video/webm" -> ".webm";
            default -> ".mp4";
        };
    }

    private record ImageResult(int width, int height, Path thumbnail) {}
    public record StoredFile(Resource resource, String mimeType, String filename, long size) {}
    public record StoredObject(long fileId, String objectKey, String thumbnailObjectKey) {}
}
