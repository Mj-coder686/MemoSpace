package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventService {
    private final JdbcTemplate jdbc;
    private final PermissionService permission;

    public EventService(JdbcTemplate jdbc, PermissionService permission) {
        this.jdbc = jdbc;
        this.permission = permission;
    }

    @Transactional
    public Map<String, Object> create(long userId, long spaceId, String name, String description,
                                      LocalDateTime startAt, LocalDateTime endAt, String location, List<Long> memoryIds) {
        permission.requireUpload(userId, spaceId);
        long id = JdbcIds.insert(jdbc, "INSERT INTO `event`(creator_id,space_id,name,description,start_at,end_at,location) VALUES(?,?,?,?,?,?,?)",
                userId, spaceId, name.trim(), description, startAt, endAt, location);
        if (memoryIds != null) for (long memoryId : memoryIds.stream().distinct().toList()) {
            permission.requireEdit(userId, memoryId);
            jdbc.update("INSERT INTO event_memory(event_id,memory_id) VALUES(?,?)", id, memoryId);
        }
        return detail(userId, id);
    }

    public List<Map<String, Object>> list(long userId, long spaceId) {
        permission.requireSpaceAccess(userId, spaceId);
        return jdbc.queryForList("SELECT e.id,e.name,e.description,e.start_at,e.end_at,e.location,e.cover_url,u.nickname AS creator_nickname," +
                "(SELECT COUNT(*) FROM event_memory em WHERE em.event_id=e.id) AS memory_count FROM `event` e JOIN user_account u ON u.id=e.creator_id " +
                "WHERE e.space_id=? ORDER BY e.start_at DESC", spaceId);
    }

    public Map<String, Object> detail(long userId, long eventId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT e.*,s.name AS space_name,u.nickname AS creator_nickname FROM `event` e " +
                "JOIN space s ON s.id=e.space_id JOIN user_account u ON u.id=e.creator_id WHERE e.id=?", eventId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "事件不存在");
        long spaceId = ((Number) rows.get(0).get("space_id")).longValue();
        permission.requireSpaceAccess(userId, spaceId);
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("memories", jdbc.queryForList("SELECT m.id,m.title,m.content,m.memory_type,m.occurred_at,m.location FROM event_memory em JOIN memory m ON m.id=em.memory_id WHERE em.event_id=? ORDER BY m.occurred_at", eventId));
        return result;
    }
}
