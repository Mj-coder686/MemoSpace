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
public class FriendService {
    private final JdbcTemplate jdbc;

    public FriendService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(long userId) {
        return jdbc.queryForList("SELECT f.id AS friendship_id,u.id AS friend_id,u.public_id,u.username,u.nickname,u.avatar,u.bio,u.location," +
                        "s.remark_name,COALESCE(s.allow_direct_reminders,TRUE) AS allow_direct_reminders," +
                        "COALESCE(s.mute_chat,FALSE) AS mute_chat," +
                        "(SELECT COUNT(*) FROM direct_message dm WHERE dm.friendship_id=f.id AND dm.receiver_id=? AND dm.read_at IS NULL) AS unread_count," +
                        "(SELECT dm.content FROM direct_message dm WHERE dm.friendship_id=f.id ORDER BY dm.id DESC LIMIT 1) AS last_message," +
                        "(SELECT dm.sent_at FROM direct_message dm WHERE dm.friendship_id=f.id ORDER BY dm.id DESC LIMIT 1) AS last_message_at," +
                        "f.created_at AS friends_since " +
                        "FROM friendship f " +
                        "JOIN user_account u ON u.id=CASE WHEN f.user_low_id=? THEN f.user_high_id ELSE f.user_low_id END " +
                        "LEFT JOIN friend_setting s ON s.owner_id=? AND s.friend_id=u.id " +
                        "WHERE f.status='ACTIVE' AND (f.user_low_id=? OR f.user_high_id=?) " +
                        "ORDER BY COALESCE(s.remark_name,u.nickname),u.id",
                userId, userId, userId, userId, userId);
    }

    public List<Map<String, Object>> requests(long userId) {
        return jdbc.queryForList("SELECT fr.id,fr.sender_id,fr.receiver_id,fr.message,fr.status,fr.created_at,fr.responded_at," +
                        "CASE WHEN fr.receiver_id=? THEN 'INCOMING' ELSE 'OUTGOING' END AS direction," +
                        "sender.public_id AS sender_public_id,sender.username AS sender_username,sender.nickname AS sender_nickname,sender.avatar AS sender_avatar," +
                        "receiver.public_id AS receiver_public_id,receiver.username AS receiver_username,receiver.nickname AS receiver_nickname,receiver.avatar AS receiver_avatar " +
                        "FROM friend_request fr JOIN user_account sender ON sender.id=fr.sender_id " +
                        "JOIN user_account receiver ON receiver.id=fr.receiver_id " +
                        "WHERE fr.sender_id=? OR fr.receiver_id=? " +
                        "ORDER BY CASE WHEN fr.status='PENDING' THEN 0 ELSE 1 END,fr.created_at DESC",
                userId, userId, userId);
    }

    @Transactional
    public synchronized Map<String, Object> request(long senderId, long receiverId, String message) {
        if (senderId == receiverId) throw new ApiException(HttpStatus.BAD_REQUEST, "不能添加自己为好友");
        requireUser(receiverId);
        requireNotBlocked(senderId, receiverId);
        if (areActiveFriends(senderId, receiverId)) {
            throw new ApiException(HttpStatus.CONFLICT, "你们已经是好友");
        }
        if (count("SELECT COUNT(*) FROM friend_request WHERE status='PENDING' AND " +
                        "((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",
                senderId, receiverId, receiverId, senderId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "双方已有待处理的好友申请");
        }

        long requestId = JdbcIds.insert(jdbc,
                "INSERT INTO friend_request(sender_id,receiver_id,message,status) VALUES(?,?,?,'PENDING')",
                senderId, receiverId, cleanMessage(message));
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) " +
                        "VALUES(?,?,'FRIEND_REQUEST','收到好友申请',?,?)",
                receiverId, senderId, message == null || message.isBlank() ? "对方希望添加你为好友" : message.trim(), requestId);
        return requestView(requestId);
    }

    @Transactional
    public Map<String, Object> respond(long userId, long requestId, boolean accept) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM friend_request WHERE id=? FOR UPDATE", requestId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "好友申请不存在");
        Map<String, Object> request = rows.get(0);
        long senderId = number(request.get("sender_id"));
        long receiverId = number(request.get("receiver_id"));
        if (receiverId != userId) throw new ApiException(HttpStatus.FORBIDDEN, "无权处理他人的好友申请");
        if (!"PENDING".equals(String.valueOf(request.get("status")))) {
            throw new ApiException(HttpStatus.CONFLICT, "好友申请已经处理");
        }

        if (!accept) {
            jdbc.update("UPDATE friend_request SET status='REJECTED',responded_at=CURRENT_TIMESTAMP WHERE id=?", requestId);
            return Map.of("id", requestId, "status", "REJECTED");
        }
        requireNotBlocked(senderId, receiverId);
        long friendshipId = activateFriendship(senderId, receiverId);
        ensureSetting(senderId, receiverId);
        ensureSetting(receiverId, senderId);
        jdbc.update("UPDATE friend_request SET status='ACCEPTED',responded_at=CURRENT_TIMESTAMP WHERE id=?", requestId);
        jdbc.update("UPDATE friend_request SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP " +
                        "WHERE id<>? AND status='PENDING' AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",
                requestId, senderId, receiverId, receiverId, senderId);
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content,reference_id) " +
                        "VALUES(?,?,'FRIEND_ACCEPT','好友申请已通过','现在可以开始私聊和共同安排提醒了',?)",
                senderId, receiverId, friendshipId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", requestId);
        result.put("status", "ACCEPTED");
        result.put("friendshipId", friendshipId);
        result.put("friendId", senderId);
        return result;
    }

    public Map<String, Object> getSettings(long userId, long friendId) {
        requireActiveFriend(userId, friendId);
        ensureSetting(userId, friendId);
        return jdbc.queryForMap("SELECT owner_id,friend_id,remark_name,allow_direct_reminders,mute_chat,updated_at " +
                "FROM friend_setting WHERE owner_id=? AND friend_id=?", userId, friendId);
    }

    @Transactional
    public Map<String, Object> updateSettings(long userId, long friendId, String remarkName,
                                               Boolean allowDirectReminders, Boolean muteChat) {
        requireActiveFriend(userId, friendId);
        ensureSetting(userId, friendId);
        if (remarkName != null) {
            String value = remarkName.trim();
            jdbc.update("UPDATE friend_setting SET remark_name=?,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND friend_id=?",
                    value.isEmpty() ? null : value, userId, friendId);
        }
        if (allowDirectReminders != null) {
            jdbc.update("UPDATE friend_setting SET allow_direct_reminders=?,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND friend_id=?",
                    allowDirectReminders, userId, friendId);
        }
        if (muteChat != null) {
            jdbc.update("UPDATE friend_setting SET mute_chat=?,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND friend_id=?",
                    muteChat, userId, friendId);
        }
        return getSettings(userId, friendId);
    }

    @Transactional
    public void remove(long userId, long friendId) {
        long friendshipId = requireActiveFriend(userId, friendId);
        jdbc.update("UPDATE friendship SET status='ENDED',ended_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?", friendshipId);
        jdbc.update("UPDATE friend_request SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP WHERE status='PENDING' AND " +
                        "((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",
                userId, friendId, friendId, userId);
    }

    public boolean areActiveFriends(long firstUserId, long secondUserId) {
        long low = Math.min(firstUserId, secondUserId);
        long high = Math.max(firstUserId, secondUserId);
        return count("SELECT COUNT(*) FROM friendship WHERE user_low_id=? AND user_high_id=? AND status='ACTIVE'", low, high) > 0;
    }

    public long requireActiveFriend(long userId, long friendId) {
        long low = Math.min(userId, friendId);
        long high = Math.max(userId, friendId);
        List<Long> ids = jdbc.query("SELECT id FROM friendship WHERE user_low_id=? AND user_high_id=? AND status='ACTIVE'",
                (rs, rowNum) -> rs.getLong(1), low, high);
        if (ids.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "只有好友之间可以使用此功能");
        return ids.get(0);
    }

    private long activateFriendship(long firstUserId, long secondUserId) {
        long low = Math.min(firstUserId, secondUserId);
        long high = Math.max(firstUserId, secondUserId);
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id,status FROM friendship WHERE user_low_id=? AND user_high_id=? FOR UPDATE", low, high);
        if (existing.isEmpty()) {
            return JdbcIds.insert(jdbc,
                    "INSERT INTO friendship(user_low_id,user_high_id,status) VALUES(?,?,'ACTIVE')", low, high);
        }
        long id = number(existing.get(0).get("id"));
        jdbc.update("UPDATE friendship SET status='ACTIVE',ended_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        return id;
    }

    private void ensureSetting(long ownerId, long friendId) {
        if (count("SELECT COUNT(*) FROM friend_setting WHERE owner_id=? AND friend_id=?", ownerId, friendId) == 0) {
            jdbc.update("INSERT INTO friend_setting(owner_id,friend_id,allow_direct_reminders,mute_chat) VALUES(?,?,TRUE,FALSE)",
                    ownerId, friendId);
        }
    }

    private Map<String, Object> requestView(long requestId) {
        return jdbc.queryForMap("SELECT id,sender_id,receiver_id,message,status,created_at FROM friend_request WHERE id=?", requestId);
    }

    private void requireUser(long userId) {
        if (count("SELECT COUNT(*) FROM user_account WHERE id=?", userId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
    }

    private void requireNotBlocked(long firstUserId, long secondUserId) {
        if (count("SELECT COUNT(*) FROM user_block WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)",
                firstUserId, secondUserId, secondUserId, firstUserId) > 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "双方当前无法添加为好友");
        }
    }

    private String cleanMessage(String message) {
        if (message == null) return null;
        String value = message.trim();
        return value.isEmpty() ? null : value;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
