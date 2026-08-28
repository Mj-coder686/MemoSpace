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

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {
    private final JdbcTemplate jdbc;
    private final PermissionService permission;
    private final String mode;
    private final Path localRoot;
    private final String bucket;
    private final MinioClient minio;

    public FileStorageService(JdbcTemplate jdbc, PermissionService permission,
                              @Value("${app.storage.mode}") String mode,
                              @Value("${app.storage.local-path}") String localPath,
                              @Value("${app.storage.endpoint}") String endpoint,
                              @Value("${app.storage.access-key}") String accessKey,
                              @Value("${app.storage.secret-key}") String secretKey,
                              @Value("${app.storage.bucket}") String bucket) {
        this.jdbc = jdbc;
        this.permission = permission;
        this.mode = mode;
        this.localRoot = Path.of(localPath).toAbsolutePath().normalize();
        this.bucket = bucket;
        this.minio = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    public Map<String, Object> upload(long userId, MultipartFile file) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "请选择文件");
        if (file.getSize() > 30L * 1024 * 1024) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "单个文件不能超过 30MB");
        try {
            byte[] bytes = file.getBytes();
            String mime = detectMime(bytes);
            if (mime == null) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 JPEG、PNG、WebP、GIF、MP4 和 WebM");
            String ext = extension(mime);
            LocalDate today = LocalDate.now();
            String key = userId + "/" + today.getYear() + "/" + today.getMonthValue() + "/" + UUID.randomUUID() + ext;
            if ("minio".equalsIgnoreCase(mode)) putMinio(key, mime, bytes);
            else putLocal(key, bytes);
            String original = Path.of(file.getOriginalFilename() == null ? "memory" + ext : file.getOriginalFilename()).getFileName().toString();
            long id = JdbcIds.insert(jdbc,
                    "INSERT INTO file_record(owner_id,object_key,original_name,mime_type,file_size,is_private) VALUES(?,?,?,?,?,TRUE)",
                    userId, key, original, mime, bytes.length);
            return Map.of("id", id, "name", original, "mimeType", mime, "size", bytes.length, "contentUrl", "/api/files/" + id + "/content");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "文件存储暂时不可用");
        }
    }

    public StoredFile load(long userId, long fileId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM file_record WHERE id=?", fileId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "文件不存在");
        Map<String, Object> file = rows.get(0);
        long owner = ((Number) file.get("owner_id")).longValue();
        if (owner != userId) {
            List<Map<String, Object>> memories = jdbc.queryForList("SELECT mm.memory_id FROM memory_media mm WHERE mm.object_key=?", file.get("object_key"));
            boolean canViewMemory = memories.stream()
                    .anyMatch(m -> permission.canViewMemory(userId, ((Number) m.get("memory_id")).longValue()));
            Integer reminderLinks = jdbc.queryForObject("SELECT COUNT(*) FROM reminder r " +
                            "JOIN reminder_participant rp ON rp.reminder_id=r.id AND rp.user_id=? " +
                            "WHERE r.image_file_id=? AND rp.acceptance_status IN ('PENDING','ACCEPTED')",
                    Integer.class, userId, fileId);
            boolean canViewReminder = reminderLinks != null && reminderLinks > 0;
            if (!canViewMemory && !canViewReminder) {
                throw new ApiException(HttpStatus.FORBIDDEN, "你没有权限查看该私密文件");
            }
        }
        String key = String.valueOf(file.get("object_key"));
        String mime = String.valueOf(file.get("mime_type"));
        String name = String.valueOf(file.get("original_name"));
        long size = ((Number) file.get("file_size")).longValue();
        try {
            if ("minio".equalsIgnoreCase(mode)) {
                Resource resource = new InputStreamResource(minio.getObject(GetObjectArgs.builder()
                        .bucket(bucket).object(key).build()));
                return new StoredFile(resource, mime, name, size);
            }
            Path path = safeLocalPath(key);
            if (!Files.exists(path)) throw new ApiException(HttpStatus.NOT_FOUND, "文件已丢失");
            return new StoredFile(new FileSystemResource(path), mime, name, size);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "无法读取媒体文件");
        }
    }

    private void putLocal(String key, byte[] bytes) throws Exception {
        Path target = safeLocalPath(key);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
    }

    private Path safeLocalPath(String key) {
        Path target = localRoot.resolve(key).normalize();
        if (!target.startsWith(localRoot)) throw new ApiException(HttpStatus.BAD_REQUEST, "非法文件路径");
        return target;
    }

    private void putMinio(String key, String mime, byte[] bytes) throws Exception {
        boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        minio.putObject(PutObjectArgs.builder().bucket(bucket).object(key).contentType(mime)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1).build());
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

    public record StoredFile(Resource resource, String mimeType, String filename, long size) {}
}
