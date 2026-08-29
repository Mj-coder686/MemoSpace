package com.memospace.service;

import com.memospace.api.ApiException;
import com.memospace.realtime.RealtimeNotificationPublisher;
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
    private final RealtimeNotificationPublisher realtime;

    public SocialService(JdbcTemplate jdbc, PermissionService permission, RealtimeNotificationPublisher realtime) {
        this.jdbc = jdbc;
        this.permission = permission;
        this.realtime = realtime;
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

    @Transactional
    public Map<String, Object> anniversary(long userId, long spaceId, String title, LocalDate date, boolean yearly) {
        requireActiveRelationshipSpace(userId, spaceId);
        long id = JdbcIds.insert(jdbc, "INSERT INTO anniversary(space_id,creator_id,title,anniversary_date,repeat_yearly) VALUES(?,?,?,?,?)",
                spaceId, userId, title.trim(), date, yearly);
        notifySpaceMembers(userId, spaceId, "ANNIVERSARY", "共同空间新增纪念日", title.trim(), id);
        return anniversaryRow(spaceId, id);
    }

    @Transactional
    public Map<String, Object> updateAnniversary(long userId, long spaceId, long anniversaryId,
                                                  String title, LocalDate date, boolean yearly) {
        requireActiveRelationshipSpace(userId, spaceId);
        int changed = jdbc.update("UPDATE anniversary SET title=?,anniversary_date=?,repeat_yearly=? WHERE id=? AND space_id=?",
                title.trim(), date, yearly, anniversaryId, spaceId);
        if (changed == 0) throw new ApiException(HttpStatus.NOT_FOUND, "纪念日不存在");
        notifySpaceMembers(userId, spaceId, "ANNIVERSARY", "共同纪念日已更新", title.trim(), anniversaryId);
        return anniversaryRow(spaceId, anniversaryId);
    }

    @Transactional
    public void deleteAnniversary(long userId, long spaceId, long anniversaryId) {
        requireActiveRelationshipSpace(userId, spaceId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT title FROM anniversary WHERE id=? AND space_id=?", anniversaryId, spaceId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "纪念日不存在");
        String title = String.valueOf(rows.get(0).get("title"));
        jdbc.update("DELETE FROM anniversary WHERE id=? AND space_id=?", anniversaryId, spaceId);
        notifySpaceMembers(userId, spaceId, "ANNIVERSARY", "共同纪念日已删除", title, anniversaryId);
    }

    private Map<String, Object> anniversaryRow(long spaceId, long anniversaryId) {
        return jdbc.queryForMap("SELECT id,space_id,creator_id,title,anniversary_date,repeat_yearly,created_at " +
                "FROM anniversary WHERE id=? AND space_id=?", anniversaryId, spaceId);
    }

    private void requireActiveRelationshipSpace(long userId, long spaceId) {
        permission.requireUpload(userId, spaceId);
        int relationshipSpace = jdbc.queryForObject(
                "SELECT COUNT(*) FROM space WHERE id=? AND space_type='RELATIONSHIP'", Integer.class, spaceId);
        if (relationshipSpace == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "纪念日只能添加到关系空间");
    }

    private void notifySpaceMembers(long actorId, long spaceId, String type, String title,
                                    String content, long referenceId) {
        jdbc.queryForList("SELECT user_id FROM space_member WHERE space_id=? AND user_id<>?", spaceId, actorId)
                .forEach(row -> {
                    long userId = ((Number) row.get("user_id")).longValue();
                    jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) " +
                                    "VALUES(?,?,?,?,?,?)", userId, actorId, type, title, content, referenceId);
                    realtime.publishAfterCommit(userId, type, title, content, referenceId);
                });
    }
}
