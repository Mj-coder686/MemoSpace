package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MemoryService {
    private static final Set<String> TYPES = Set.of("PHOTO", "VIDEO", "TEXT", "EVENT", "LOCATION", "MIXED");
    private static final Set<String> VISIBILITIES = Set.of("PRIVATE", "RELATIONSHIP", "PUBLIC", "CUSTOM");
    private final JdbcTemplate jdbc;
    private final PermissionService permission;
    private final FeedCacheService feedCache;

    public MemoryService(JdbcTemplate jdbc, PermissionService permission, FeedCacheService feedCache) {
        this.jdbc = jdbc;
        this.permission = permission;
        this.feedCache = feedCache;
    }

    @Transactional
    public Map<String, Object> create(long userId, CreateCommand command) {
        String type = command.type().toUpperCase();
        String visibility = command.visibility().toUpperCase();
        if (!TYPES.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的记忆类型");
        if (!VISIBILITIES.contains(visibility)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的可见性");

        long personalSpace = jdbc.queryForObject("SELECT id FROM space WHERE owner_id=? AND space_type='PERSONAL'", Long.class, userId);
        LinkedHashSet<Long> spaceIds = new LinkedHashSet<>();
        spaceIds.add(personalSpace);
        if (command.spaceIds() != null) spaceIds.addAll(command.spaceIds());
        for (long spaceId : spaceIds) permission.requireUpload(userId, spaceId);
        if ("RELATIONSHIP".equals(visibility) && spaceIds.stream().noneMatch(this::isRelationshipSpace)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "关系成员可见的记忆至少需同步到一个关系空间");
        }

        long memoryId = JdbcIds.insert(jdbc,
                "INSERT INTO memory(creator_id,title,content,memory_type,occurred_at,location,latitude,longitude,visibility) VALUES(?,?,?,?,?,?,?,?,?)",
                userId, command.title().trim(), command.content(), type,
                command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt(),
                command.location(), command.latitude(), command.longitude(), visibility);
        for (long spaceId : spaceIds) {
            jdbc.update("INSERT INTO memory_space(memory_id,space_id,added_by) VALUES(?,?,?)", memoryId, spaceId, userId);
        }
        if (command.customViewerIds() != null && "CUSTOM".equals(visibility)) {
            command.customViewerIds().stream().filter(id -> id != userId).distinct()
                    .forEach(id -> jdbc.update("INSERT INTO memory_custom_viewer(memory_id,user_id) VALUES(?,?)", memoryId, id));
        }
        if (command.fileIds() != null) attachFiles(userId, memoryId, command.fileIds());
        if ("PUBLIC".equals(visibility)) {
            jdbc.update("INSERT INTO post(memory_id,creator_id,status) VALUES(?,?,'PUBLISHED')", memoryId, userId);
        }
        notifySpaceMembers(userId, memoryId, spaceIds);
        feedCache.invalidateAll();
        return detail(userId, memoryId);
    }

    public List<Map<String, Object>> mine(long userId, Long spaceId, String keyword) {
        if (spaceId != null) {
            permission.requireSpaceAccess(userId, spaceId);
            return jdbc.queryForList(summarySelect() +
                    " JOIN memory_space ms ON ms.memory_id=m.id WHERE ms.space_id=? AND (LOWER(m.title) LIKE LOWER(?) OR LOWER(COALESCE(m.content,'')) LIKE LOWER(?) OR LOWER(COALESCE(m.location,'')) LIKE LOWER(?)) ORDER BY m.occurred_at DESC",
                    spaceId, like(keyword), like(keyword), like(keyword));
        }
        return jdbc.queryForList(summarySelect() +
                " WHERE m.creator_id=? AND (LOWER(m.title) LIKE LOWER(?) OR LOWER(COALESCE(m.content,'')) LIKE LOWER(?) OR LOWER(COALESCE(m.location,'')) LIKE LOWER(?)) ORDER BY m.occurred_at DESC",
                userId, like(keyword), like(keyword), like(keyword));
    }

    public Map<String, Object> detail(long userId, long memoryId) {
        permission.requireView(userId, memoryId);
        List<Map<String, Object>> rows = jdbc.queryForList(summarySelect() + " WHERE m.id=?", memoryId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "记忆不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("spaces", jdbc.queryForList("SELECT s.id,s.name,s.space_type,s.status FROM memory_space ms JOIN space s ON s.id=ms.space_id WHERE ms.memory_id=?", memoryId));
        result.put("media", jdbc.queryForList("SELECT mm.id,fr.id AS file_id,mm.media_type,mm.file_size,mm.sort_order,fr.original_name,fr.mime_type FROM memory_media mm LEFT JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=? ORDER BY mm.sort_order", memoryId));
        result.put("comments", jdbc.queryForList("SELECT c.id,c.content,c.created_at,u.id AS user_id,u.nickname,u.avatar FROM comments c JOIN user_account u ON u.id=c.user_id WHERE c.memory_id=? ORDER BY c.created_at", memoryId));
        result.put("reactions", jdbc.queryForList("SELECT r.reaction_type,COUNT(*) AS count FROM reaction r WHERE r.memory_id=? GROUP BY r.reaction_type", memoryId));
        return result;
    }

    @Transactional
    public Map<String, Object> update(long userId, long memoryId, String title, String content, String visibility) {
        permission.requireEdit(userId, memoryId);
        String v = visibility == null ? null : visibility.toUpperCase();
        if (v != null && !VISIBILITIES.contains(v)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持的可见性");
        if (title != null && !title.isBlank()) jdbc.update("UPDATE memory SET title=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", title.trim(), memoryId);
        if (content != null) jdbc.update("UPDATE memory SET content=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", content, memoryId);
        if (v != null) {
            jdbc.update("UPDATE memory SET visibility=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", v, memoryId);
            if ("PUBLIC".equals(v)) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM post WHERE memory_id=?", Integer.class, memoryId) == 0)
                    jdbc.update("INSERT INTO post(memory_id,creator_id,status) VALUES(?,?,'PUBLISHED')", memoryId, userId);
                else jdbc.update("UPDATE post SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP WHERE memory_id=?", memoryId);
            } else jdbc.update("DELETE FROM post WHERE memory_id=?", memoryId);
        }
        feedCache.invalidateAll();
        return detail(userId, memoryId);
    }

    @Transactional
    public void delete(long userId, long memoryId) {
        permission.requireEdit(userId, memoryId);
        jdbc.update("DELETE FROM memory WHERE id=?", memoryId);
        feedCache.invalidateAll();
    }

    public List<Map<String, Object>> feed(long userId, String scope) {
        List<Map<String, Object>> cached = feedCache.get(userId, scope);
        if (cached != null) return cached;
        String following = "following".equalsIgnoreCase(scope)
                ? " AND EXISTS(SELECT 1 FROM user_follow f WHERE f.follower_id=? AND f.following_id=m.creator_id)" : "";
        String sql = summarySelect() + " JOIN post p ON p.memory_id=m.id WHERE m.visibility='PUBLIC' AND p.status='PUBLISHED'" + following + " ORDER BY p.published_at DESC LIMIT 60";
        List<Map<String, Object>> result = "following".equalsIgnoreCase(scope) ? jdbc.queryForList(sql, userId) : jdbc.queryForList(sql);
        feedCache.put(userId, scope, result);
        return result;
    }

    public Map<String, Object> home(long userId) {
        Map<String, Object> home = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        home.put("today", jdbc.queryForList(summarySelect() +
                " WHERE m.creator_id=? AND MONTH(m.occurred_at)=? AND DAYOFMONTH(m.occurred_at)=? AND YEAR(m.occurred_at)<? ORDER BY m.occurred_at DESC LIMIT 8",
                userId, today.getMonthValue(), today.getDayOfMonth(), today.getYear()));
        home.put("recent", jdbc.queryForList(summarySelect() + " WHERE m.creator_id=? ORDER BY m.occurred_at DESC LIMIT 6", userId));
        home.put("feed", feed(userId, "following"));
        home.put("stats", Map.of(
                "memories", count("SELECT COUNT(*) FROM memory WHERE creator_id=?", userId),
                "spaces", count("SELECT COUNT(*) FROM space_member WHERE user_id=?", userId),
                "places", count("SELECT COUNT(DISTINCT location) FROM memory WHERE creator_id=? AND location IS NOT NULL", userId)));
        return home;
    }

    public List<Map<String, Object>> calendar(long userId, int year, int month) {
        return jdbc.queryForList("SELECT CAST(occurred_at AS DATE) AS memory_date,COUNT(*) AS count,MIN(title) AS preview " +
                "FROM memory WHERE creator_id=? AND YEAR(occurred_at)=? AND MONTH(occurred_at)=? GROUP BY CAST(occurred_at AS DATE) ORDER BY memory_date",
                userId, year, month);
    }

    public List<Map<String, Object>> map(long userId) {
        return jdbc.queryForList("SELECT id,title,location,latitude,longitude,occurred_at FROM memory WHERE creator_id=? AND latitude IS NOT NULL AND longitude IS NOT NULL ORDER BY occurred_at DESC", userId);
    }

    private void attachFiles(long userId, long memoryId, List<Long> fileIds) {
        int order = 0;
        for (Long fileId : fileIds.stream().distinct().toList()) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM file_record WHERE id=? AND owner_id=?", fileId, userId);
            if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "上传的文件不存在或不属于你");
            Map<String, Object> file = rows.get(0);
            String mime = String.valueOf(file.get("mime_type"));
            jdbc.update("INSERT INTO memory_media(memory_id,media_type,object_key,file_size,sort_order) VALUES(?,?,?,?,?)",
                    memoryId, mime.startsWith("video/") ? "VIDEO" : "PHOTO", file.get("object_key"), file.get("file_size"), order++);
        }
    }

    private void notifySpaceMembers(long creator, long memoryId, Set<Long> spaceIds) {
        for (long spaceId : spaceIds) {
            jdbc.queryForList("SELECT user_id FROM space_member WHERE space_id=? AND user_id<>?", spaceId, creator)
                    .forEach(row -> jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) VALUES(?,?,'SPACE_MEMORY','共同空间有新回忆','有人留下了一条新记忆',?)",
                            row.get("user_id"), creator, memoryId));
        }
    }

    private boolean isRelationshipSpace(long spaceId) {
        return count("SELECT COUNT(*) FROM space WHERE id=? AND space_type='RELATIONSHIP'", spaceId) > 0;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private String like(String keyword) { return "%" + (keyword == null ? "" : keyword.trim()) + "%"; }

    private String summarySelect() {
        return "SELECT m.id,m.creator_id,m.title,m.content,m.memory_type,m.occurred_at,m.location,m.latitude,m.longitude,m.visibility,m.created_at,m.updated_at," +
                "u.nickname AS creator_nickname,u.avatar AS creator_avatar," +
                "(SELECT COUNT(*) FROM memory_media mm WHERE mm.memory_id=m.id) AS media_count," +
                "(SELECT fr.id FROM memory_media mm JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=m.id ORDER BY mm.sort_order,mm.id LIMIT 1) AS cover_file_id," +
                "(SELECT fr.mime_type FROM memory_media mm JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=m.id ORDER BY mm.sort_order,mm.id LIMIT 1) AS cover_mime_type," +
                "(SELECT COUNT(*) FROM comments c WHERE c.memory_id=m.id) AS comment_count," +
                "(SELECT COUNT(*) FROM reaction r WHERE r.memory_id=m.id) AS reaction_count " +
                "FROM memory m JOIN user_account u ON u.id=m.creator_id";
    }

    public record CreateCommand(String title, String content, String type, LocalDateTime occurredAt, String location,
                                Double latitude, Double longitude, String visibility, List<Long> spaceIds,
                                List<Long> customViewerIds, List<Long> fileIds) {}
}
