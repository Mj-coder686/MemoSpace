package com.memospace.service;

import com.memospace.api.ApiException;
import com.memospace.realtime.RealtimeNotificationPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RelationshipService {
    private final JdbcTemplate jdbc;
    private final RelationshipCategoryService categories;
    private final RealtimeNotificationPublisher realtime;

    public RelationshipService(JdbcTemplate jdbc, RelationshipCategoryService categories,
                               RealtimeNotificationPublisher realtime) {
        this.jdbc = jdbc;
        this.categories = categories;
        this.realtime = realtime;
    }

    public List<Map<String, Object>> invitations(long userId) {
        return jdbc.queryForList("SELECT i.id,i.sender_id,i.receiver_id,i.relationship_type,i.message,i.status,i.expires_at,i.created_at," +
                        "s.nickname AS sender_nickname,s.avatar AS sender_avatar,r.nickname AS receiver_nickname," +
                        "ic.sender_category_id AS category_id,ic.category_key,ic.category_name,ic.category_icon,ic.theme_id " +
                        "FROM relationship_invitation i JOIN user_account s ON s.id=i.sender_id JOIN user_account r ON r.id=i.receiver_id " +
                        "LEFT JOIN relationship_invitation_category ic ON ic.invitation_id=i.id " +
                        "WHERE i.sender_id=? OR i.receiver_id=? ORDER BY i.created_at DESC", userId, userId);
    }

    @Transactional
    public synchronized Map<String, Object> invite(long senderId, long receiverId, long categoryId, String message) {
        Map<String, Object> category = categories.ownedCategory(senderId, categoryId);
        String relationshipType = relationshipType(String.valueOf(category.get("category_key")));
        if (senderId == receiverId) throw new ApiException(HttpStatus.BAD_REQUEST, "不能向自己发起关系邀请");
        if (count("SELECT COUNT(*) FROM user_account WHERE id=? AND is_admin=FALSE", receiverId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (count("SELECT COUNT(*) FROM user_block WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",
                senderId, receiverId, receiverId, senderId) > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "双方当前无法建立关系");
        }
        if (count("SELECT COUNT(*) FROM relationship_invitation i JOIN relationship_invitation_category ic ON ic.invitation_id=i.id " +
                        "WHERE ((i.sender_id=? AND i.receiver_id=?) OR (i.sender_id=? AND i.receiver_id=?)) " +
                        "AND ic.sender_category_id=? AND i.status='PENDING' AND i.expires_at>CURRENT_TIMESTAMP",
                senderId, receiverId, receiverId, senderId, categoryId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "该分类下已有待处理邀请");
        }
        Long existingRelationship = activeRelationship(senderId, receiverId);
        if (existingRelationship != null && count("SELECT COUNT(*) FROM relationship_category_link WHERE category_id=? AND relationship_id=?",
                categoryId, existingRelationship) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "对方已经在这个关系分类中");
        }

        LocalDateTime expires = LocalDateTime.now().plusDays(7);
        long invitationId = JdbcIds.insert(jdbc,
                "INSERT INTO relationship_invitation(sender_id,receiver_id,relationship_type,message,status,expires_at) VALUES(?,?,?,?,'PENDING',?)",
                senderId, receiverId, relationshipType, message, expires);
        jdbc.update("INSERT INTO relationship_invitation_category(invitation_id,sender_category_id,category_key,category_name,category_icon,theme_id) " +
                        "VALUES(?,?,?,?,?,?)", invitationId, categoryId, category.get("category_key"), category.get("name"),
                category.get("icon"), category.get("theme_id"));
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) " +
                        "VALUES(?,?,'RELATIONSHIP_INVITE','收到关系邀请',?,?)",
                receiverId, senderId, "邀请你绑定为「" + category.get("name") + "」", invitationId);
        realtime.publishAfterCommit(receiverId, "RELATIONSHIP_INVITE", "收到关系申请",
                "邀请你绑定为「" + category.get("name") + "」", invitationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", invitationId);
        result.put("status", "PENDING");
        result.put("categoryId", categoryId);
        result.put("categoryName", category.get("name"));
        result.put("expiresAt", expires);
        result.put("willReuseSpace", existingRelationship != null);
        return result;
    }

    @Transactional
    public Map<String, Object> respond(long userId, long invitationId, boolean accept) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT i.*,ic.sender_category_id,ic.category_key,ic.category_name,ic.category_icon,ic.theme_id " +
                "FROM relationship_invitation i LEFT JOIN relationship_invitation_category ic ON ic.invitation_id=i.id WHERE i.id=? FOR UPDATE", invitationId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "邀请不存在");
        Map<String, Object> invite = rows.get(0);
        long receiver = ((Number) invite.get("receiver_id")).longValue();
        long sender = ((Number) invite.get("sender_id")).longValue();
        if (receiver != userId) throw new ApiException(HttpStatus.FORBIDDEN, "无权处理他人邀请");
        if (!"PENDING".equals(String.valueOf(invite.get("status")))) throw new ApiException(HttpStatus.CONFLICT, "邀请已处理");
        LocalDateTime expires = ((Timestamp) invite.get("expires_at")).toLocalDateTime();
        if (expires.isBefore(LocalDateTime.now())) {
            jdbc.update("UPDATE relationship_invitation SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP WHERE id=?", invitationId);
            throw new ApiException(HttpStatus.GONE, "邀请已过期");
        }
        if (!accept) {
            jdbc.update("UPDATE relationship_invitation SET status='REJECTED',responded_at=CURRENT_TIMESTAMP WHERE id=?", invitationId);
            return Map.of("status", "REJECTED");
        }

        Map<String, Object> categorySnapshot = ensureInvitationCategory(invite, sender, invitationId);
        Long relationshipId = activeRelationship(sender, receiver);
        boolean reused = relationshipId != null;
        long spaceId;
        if (relationshipId == null) {
            String relationshipType = String.valueOf(invite.get("relationship_type"));
            relationshipId = JdbcIds.insert(jdbc,
                    "INSERT INTO relationships(relationship_type,status,established_at) VALUES(?,'ACTIVE',CURRENT_TIMESTAMP)", relationshipType);
            jdbc.update("INSERT INTO relationship_member(relationship_id,user_id) VALUES(?,?),(?,?)",
                    relationshipId, sender, relationshipId, receiver);
            spaceId = createSpace(relationshipId, sender, receiver, categorySnapshot);
        } else {
            spaceId = ensureSpace(relationshipId, sender, receiver, categorySnapshot);
        }

        long senderCategoryId = ((Number) categorySnapshot.get("sender_category_id")).longValue();
        long receiverCategoryId = categories.categoryForReceiver(receiver, categorySnapshot);
        categories.linkIfMissing(senderCategoryId, relationshipId);
        categories.linkIfMissing(receiverCategoryId, relationshipId);

        jdbc.update("UPDATE relationship_invitation SET status='ACCEPTED',responded_at=CURRENT_TIMESTAMP WHERE id=?", invitationId);
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) " +
                        "VALUES(?,?,'RELATIONSHIP_ACCEPT','关系已建立',?,?)",
                sender, receiver, "「" + categorySnapshot.get("category_name") + "」分类中的共同空间已准备好", spaceId);
        realtime.publishAfterCommit(sender, "RELATIONSHIP_ACCEPT", "关系申请已接受",
                "「" + categorySnapshot.get("category_name") + "」分类中的共同空间已准备好", spaceId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ACCEPTED");
        result.put("relationshipId", relationshipId);
        result.put("spaceId", spaceId);
        result.put("reusedSpace", reused);
        result.put("categoryName", categorySnapshot.get("category_name"));
        return result;
    }

    public List<Map<String, Object>> list(long userId) {
        return categories.relationships(userId);
    }

    public List<Map<String, Object>> replaceCategories(long userId, long relationshipId, List<Long> categoryIds) {
        return categories.replaceRelationshipCategories(userId, relationshipId, categoryIds);
    }

    @Transactional
    public void archive(long userId, long relationshipId) {
        if (count("SELECT COUNT(*) FROM relationship_member WHERE relationship_id=? AND user_id=?", relationshipId, userId) == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权封存该关系空间");
        }
        jdbc.update("UPDATE relationships SET status='ARCHIVED',archived_at=CURRENT_TIMESTAMP WHERE id=? AND status='ACTIVE'", relationshipId);
        jdbc.update("UPDATE space SET status='ARCHIVED',archived_at=CURRENT_TIMESTAMP WHERE relationship_id=?", relationshipId);
    }

    private Map<String, Object> ensureInvitationCategory(Map<String, Object> invite, long sender, long invitationId) {
        if (invite.get("sender_category_id") != null) return invite;
        String type = String.valueOf(invite.get("relationship_type"));
        Long categoryId = categories.defaultCategoryId(sender, type);
        Map<String, Object> category = categories.ownedCategory(sender, categoryId);
        jdbc.update("INSERT INTO relationship_invitation_category(invitation_id,sender_category_id,category_key,category_name,category_icon,theme_id) VALUES(?,?,?,?,?,?)",
                invitationId, categoryId, category.get("category_key"), category.get("name"), category.get("icon"), category.get("theme_id"));
        Map<String, Object> snapshot = new LinkedHashMap<>(invite);
        snapshot.put("sender_category_id", categoryId);
        snapshot.put("category_key", category.get("category_key"));
        snapshot.put("category_name", category.get("name"));
        snapshot.put("category_icon", category.get("icon"));
        snapshot.put("theme_id", category.get("theme_id"));
        return snapshot;
    }

    private long createSpace(long relationshipId, long sender, long receiver, Map<String, Object> category) {
        String senderName = jdbc.queryForObject("SELECT nickname FROM user_account WHERE id=?", String.class, sender);
        String receiverName = jdbc.queryForObject("SELECT nickname FROM user_account WHERE id=?", String.class, receiver);
        Long themeId = category.get("theme_id") instanceof Number number ? number.longValue() : null;
        if (themeId == null) themeId = jdbc.queryForObject("SELECT id FROM space_theme WHERE preset_name='Midnight Mist'", Long.class);
        long spaceId = JdbcIds.insert(jdbc,
                "INSERT INTO space(space_type,name,relationship_id,visibility,theme_id,status) VALUES('RELATIONSHIP',?,?, 'RELATIONSHIP',?,'ACTIVE')",
                senderName + " & " + receiverName, relationshipId, themeId);
        jdbc.update("INSERT INTO space_member(space_id,user_id,member_role) VALUES(?,?,'MEMBER'),(?,?,'MEMBER')",
                spaceId, sender, spaceId, receiver);
        return spaceId;
    }

    private long ensureSpace(long relationshipId, long sender, long receiver, Map<String, Object> category) {
        List<Long> spaces = jdbc.query("SELECT id FROM space WHERE relationship_id=? ORDER BY id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), relationshipId);
        return spaces.isEmpty() ? createSpace(relationshipId, sender, receiver, category) : spaces.get(0);
    }

    private Long activeRelationship(long firstUser, long secondUser) {
        List<Long> ids = jdbc.query("SELECT r.id FROM relationships r " +
                        "JOIN relationship_member a ON a.relationship_id=r.id AND a.user_id=? " +
                        "JOIN relationship_member b ON b.relationship_id=r.id AND b.user_id=? " +
                        "WHERE r.status='ACTIVE' ORDER BY r.id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), firstUser, secondUser);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String relationshipType(String categoryKey) {
        return switch (categoryKey) {
            case "LOVER" -> "COUPLE";
            case "FAMILY" -> "FAMILY";
            default -> "FRIEND";
        };
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
