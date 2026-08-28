package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PermissionService {
    private final JdbcTemplate jdbc;

    public PermissionService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean canAccessSpace(long userId, long spaceId) {
        return count("SELECT COUNT(*) FROM space_member sm JOIN space s ON s.id=sm.space_id WHERE sm.user_id=? AND sm.space_id=?", userId, spaceId) > 0;
    }

    public boolean canUploadToSpace(long userId, long spaceId) {
        return count("SELECT COUNT(*) FROM space_member sm JOIN space s ON s.id=sm.space_id WHERE sm.user_id=? AND sm.space_id=? AND s.status='ACTIVE'", userId, spaceId) > 0;
    }

    public boolean canViewMemory(long userId, long memoryId) {
        var rows = jdbc.queryForList("SELECT creator_id,visibility FROM memory WHERE id=?", memoryId);
        if (rows.isEmpty()) return false;
        Map<String, Object> memory = rows.get(0);
        long creatorId = ((Number) memory.get("creator_id")).longValue();
        String visibility = String.valueOf(memory.get("visibility"));
        if (creatorId == userId || "PUBLIC".equals(visibility)) return true;
        if ("CUSTOM".equals(visibility)) {
            return count("SELECT COUNT(*) FROM memory_custom_viewer WHERE memory_id=? AND user_id=?", memoryId, userId) > 0;
        }
        if ("RELATIONSHIP".equals(visibility)) {
            return count("SELECT COUNT(*) FROM memory_space ms JOIN space_member sm ON sm.space_id=ms.space_id " +
                    "WHERE ms.memory_id=? AND sm.user_id=?", memoryId, userId) > 0;
        }
        return false;
    }

    public boolean canEditMemory(long userId, long memoryId) {
        return count("SELECT COUNT(*) FROM memory WHERE id=? AND creator_id=?", memoryId, userId) > 0;
    }

    public void requireSpaceAccess(long userId, long spaceId) {
        if (!canAccessSpace(userId, spaceId)) throw forbidden();
    }

    public void requireUpload(long userId, long spaceId) {
        if (!canUploadToSpace(userId, spaceId)) throw forbidden();
    }

    public void requireView(long userId, long memoryId) {
        if (!canViewMemory(userId, memoryId)) throw forbidden();
    }

    public void requireEdit(long userId, long memoryId) {
        if (!canEditMemory(userId, memoryId)) throw forbidden();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "该内容属于私密空间，你没有访问权限");
    }
}
