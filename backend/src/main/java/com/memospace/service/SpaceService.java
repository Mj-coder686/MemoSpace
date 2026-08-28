package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpaceService {
    private final JdbcTemplate jdbc;
    private final PermissionService permission;

    public SpaceService(JdbcTemplate jdbc, PermissionService permission) {
        this.jdbc = jdbc;
        this.permission = permission;
    }

    public List<Map<String, Object>> list(long userId) {
        List<Map<String, Object>> spaces = jdbc.queryForList(baseSelect() +
                " JOIN space_member sm ON sm.space_id=s.id WHERE sm.user_id=? ORDER BY s.space_type,s.created_at DESC", userId);
        spaces.forEach(this::decorate);
        return spaces;
    }

    public Map<String, Object> detail(long userId, long spaceId) {
        permission.requireSpaceAccess(userId, spaceId);
        List<Map<String, Object>> rows = jdbc.queryForList(baseSelect() + " WHERE s.id=?", spaceId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "空间不存在");
        Map<String, Object> space = new LinkedHashMap<>(rows.get(0));
        decorate(space);
        space.put("members", jdbc.queryForList("SELECT u.id,u.nickname,u.avatar,u.bio FROM space_member sm JOIN user_account u ON u.id=sm.user_id WHERE sm.space_id=?", spaceId));
        space.put("anniversaries", jdbc.queryForList("SELECT id,title,anniversary_date,repeat_yearly FROM anniversary WHERE space_id=? ORDER BY anniversary_date", spaceId));
        return space;
    }

    public List<Map<String, Object>> timeline(long userId, long spaceId) {
        permission.requireSpaceAccess(userId, spaceId);
        return jdbc.queryForList("SELECT m.id,m.title,m.content,m.memory_type,m.occurred_at,m.location,m.visibility," +
                "u.id AS creator_id,u.nickname AS creator_nickname,u.avatar AS creator_avatar," +
                "(SELECT COUNT(*) FROM memory_media mm WHERE mm.memory_id=m.id) AS media_count," +
                "(SELECT fr.id FROM memory_media mm JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=m.id ORDER BY mm.sort_order,mm.id LIMIT 1) AS cover_file_id," +
                "(SELECT fr.mime_type FROM memory_media mm JOIN file_record fr ON fr.object_key=mm.object_key WHERE mm.memory_id=m.id ORDER BY mm.sort_order,mm.id LIMIT 1) AS cover_mime_type " +
                "FROM memory_space ms JOIN memory m ON m.id=ms.memory_id JOIN user_account u ON u.id=m.creator_id " +
                "WHERE ms.space_id=? ORDER BY m.occurred_at DESC", spaceId);
    }

    @Transactional
    public Map<String, Object> updateTheme(long userId, long spaceId, String name, long themeId) {
        permission.requireSpaceAccess(userId, spaceId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM space_theme WHERE id=?", Integer.class, themeId) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "主题不存在");
        }
        if (name == null || name.isBlank()) jdbc.update("UPDATE space SET theme_id=? WHERE id=?", themeId, spaceId);
        else jdbc.update("UPDATE space SET name=?,theme_id=? WHERE id=?", name.trim(), themeId, spaceId);
        return detail(userId, spaceId);
    }

    public List<Map<String, Object>> themes() {
        return jdbc.queryForList("SELECT id,preset_name,primary_color,secondary_color,background_color,surface_color,text_color,muted_color,radius,card_opacity FROM space_theme ORDER BY id");
    }

    private String baseSelect() {
        return "SELECT s.id,s.space_type,s.name,s.relationship_id,s.cover_url,s.status,s.created_at,s.archived_at," +
                "t.id AS theme_id,t.preset_name,t.primary_color,t.secondary_color,t.background_color,t.surface_color,t.text_color,t.muted_color,t.radius,t.card_opacity " +
                "FROM space s LEFT JOIN space_theme t ON t.id=s.theme_id";
    }

    private void decorate(Map<String, Object> space) {
        long id = ((Number) space.get("id")).longValue();
        space.put("memoryCount", jdbc.queryForObject("SELECT COUNT(*) FROM memory_space WHERE space_id=?", Long.class, id));
        space.put("photoCount", jdbc.queryForObject("SELECT COUNT(*) FROM memory_space ms JOIN memory m ON m.id=ms.memory_id WHERE ms.space_id=? AND m.memory_type IN ('PHOTO','MIXED')", Long.class, id));
        space.put("placeCount", jdbc.queryForObject("SELECT COUNT(DISTINCT m.location) FROM memory_space ms JOIN memory m ON m.id=ms.memory_id WHERE ms.space_id=? AND m.location IS NOT NULL", Long.class, id));
    }
}
