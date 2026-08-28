package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SocialService {
    private static final Set<String> REACTIONS = Set.of("❤️", "😂", "🥹", "👍", "😭");
    private final JdbcTemplate jdbc;
    private final PermissionService permission;

    public SocialService(JdbcTemplate jdbc, PermissionService permission) {
        this.jdbc = jdbc;
        this.permission = permission;
    }

    @Transactional
    public Map<String, Object> comment(long userId, long memoryId, String content) {
        permission.requireView(userId, memoryId);
        Map<String, Object> memory = jdbc.queryForMap("SELECT creator_id,visibility FROM memory WHERE id=?", memoryId);
        String visibility = String.valueOf(memory.get("visibility"));
        long creator = ((Number) memory.get("creator_id")).longValue();
        if ("PRIVATE".equals(visibility) && creator != userId) throw new ApiException(HttpStatus.FORBIDDEN, "私密记忆不接受他人评论");
        long id = JdbcIds.insert(jdbc, "INSERT INTO comments(memory_id,user_id,content) VALUES(?,?,?)", memoryId, userId, content.trim());
        if (creator != userId) jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) VALUES(?,?,'COMMENT','收到新评论',?,?)",
                creator, userId, content.trim(), memoryId);
        return Map.of("id", id, "content", content.trim());
    }

    @Transactional
    public void deleteComment(long userId, long commentId) {
        int changed = jdbc.update("DELETE FROM comments WHERE id=? AND user_id=?", commentId, userId);
        if (changed == 0) throw new ApiException(HttpStatus.FORBIDDEN, "只能删除自己的评论");
    }

    @Transactional
    public Map<String, Object> react(long userId, long memoryId, String reaction) {
        permission.requireView(userId, memoryId);
        if (!REACTIONS.contains(reaction)) throw new ApiException(HttpStatus.BAD_REQUEST, "不支持该回应");
        int exists = jdbc.queryForObject("SELECT COUNT(*) FROM reaction WHERE memory_id=? AND user_id=? AND reaction_type=?", Integer.class, memoryId, userId, reaction);
        jdbc.update("DELETE FROM reaction WHERE memory_id=? AND user_id=?", memoryId, userId);
        if (exists == 0) jdbc.update("INSERT INTO reaction(memory_id,user_id,reaction_type) VALUES(?,?,?)", memoryId, userId, reaction);
        return Map.of("active", exists == 0, "reaction", reaction);
    }

    @Transactional
    public boolean favorite(long userId, long memoryId) {
        permission.requireView(userId, memoryId);
        String visibility = jdbc.queryForObject("SELECT visibility FROM memory WHERE id=?", String.class, memoryId);
        if (!"PUBLIC".equals(visibility)) throw new ApiException(HttpStatus.BAD_REQUEST, "收藏仅用于公开动态");
        int exists = jdbc.queryForObject("SELECT COUNT(*) FROM favorite WHERE memory_id=? AND user_id=?", Integer.class, memoryId, userId);
        if (exists > 0) jdbc.update("DELETE FROM favorite WHERE memory_id=? AND user_id=?", memoryId, userId);
        else jdbc.update("INSERT INTO favorite(memory_id,user_id) VALUES(?,?)", memoryId, userId);
        return exists == 0;
    }

    public List<Map<String, Object>> notifications(long userId) {
        return jdbc.queryForList("SELECT n.id,n.notification_type,n.title,n.content,n.reference_id,n.is_read,n.created_at," +
                "u.nickname AS actor_nickname,u.avatar AS actor_avatar FROM notification n LEFT JOIN user_account u ON u.id=n.actor_id " +
                "WHERE n.user_id=? ORDER BY n.created_at DESC LIMIT 80", userId);
    }

    public void readNotifications(long userId) {
        jdbc.update("UPDATE notification SET is_read=TRUE WHERE user_id=?", userId);
    }

    public List<Map<String, Object>> wall(long userId, long spaceId) {
        permission.requireSpaceAccess(userId, spaceId);
        return jdbc.queryForList("SELECT sm.id,sm.content,sm.created_at,u.id AS user_id,u.nickname,u.avatar FROM space_message sm " +
                "JOIN user_account u ON u.id=sm.user_id WHERE sm.space_id=? ORDER BY sm.created_at DESC LIMIT 100", spaceId);
    }

    public Map<String, Object> leaveMessage(long userId, long spaceId, String content) {
        permission.requireUpload(userId, spaceId);
        long id = JdbcIds.insert(jdbc, "INSERT INTO space_message(space_id,user_id,content) VALUES(?,?,?)", spaceId, userId, content.trim());
        return Map.of("id", id, "content", content.trim());
    }

    public Map<String, Object> anniversary(long userId, long spaceId, String title, LocalDate date, boolean yearly) {
        permission.requireUpload(userId, spaceId);
        long id = JdbcIds.insert(jdbc, "INSERT INTO anniversary(space_id,creator_id,title,anniversary_date,repeat_yearly) VALUES(?,?,?,?,?)",
                spaceId, userId, title.trim(), date, yearly);
        return Map.of("id", id, "title", title.trim(), "date", date);
    }
}
