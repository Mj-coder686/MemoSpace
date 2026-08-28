package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatService {
    private final JdbcTemplate jdbc;

    public ChatService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public synchronized SendResult send(long senderId, long friendId, String clientMessageId, String content) {
        String cleanClientId = requireText(clientMessageId, "clientMessageId 不能为空");
        String cleanContent = requireText(content, "消息内容不能为空");
        if (cleanClientId.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "clientMessageId 不能超过 80 个字符");
        }
        if (cleanContent.length() > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "消息内容不能超过 1000 个字符");
        }

        long friendshipId = requireActiveFriendship(senderId, friendId);
        List<ChatMessage> existing = messages(
                "SELECT * FROM direct_message WHERE sender_id=? AND client_message_id=?",
                senderId, cleanClientId);
        if (!existing.isEmpty()) return duplicate(existing.get(0), friendshipId, friendId);

        try {
            long messageId = JdbcIds.insert(jdbc,
                    "INSERT INTO direct_message(friendship_id,sender_id,receiver_id,client_message_id,content) VALUES(?,?,?,?,?)",
                    friendshipId, senderId, friendId, cleanClientId, cleanContent);
            return new SendResult(message(messageId), false);
        } catch (DuplicateKeyException ex) {
            List<ChatMessage> raced = messages(
                    "SELECT * FROM direct_message WHERE sender_id=? AND client_message_id=?",
                    senderId, cleanClientId);
            if (raced.isEmpty()) throw ex;
            return duplicate(raced.get(0), friendshipId, friendId);
        }
    }

    public MessagePage history(long userId, long friendId, Long beforeId, int limit) {
        long friendshipId = requireActiveFriendship(userId, friendId);
        int pageSize = Math.max(1, Math.min(limit, 100));
        long cursor = beforeId == null ? Long.MAX_VALUE : beforeId;
        if (cursor <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "beforeId 必须为正数");

        List<ChatMessage> descending = messages(
                "SELECT * FROM direct_message WHERE friendship_id=? AND id<? ORDER BY id DESC LIMIT ?",
                friendshipId, cursor, pageSize + 1);
        boolean hasMore = descending.size() > pageSize;
        if (hasMore) descending = new ArrayList<>(descending.subList(0, pageSize));
        else descending = new ArrayList<>(descending);
        Collections.reverse(descending);
        Long nextBeforeId = descending.isEmpty() ? null : descending.get(0).id();
        return new MessagePage(descending, hasMore, nextBeforeId);
    }

    @Transactional
    public ReadReceipt markRead(long readerId, long friendId, long throughMessageId) {
        if (throughMessageId <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "throughMessageId 必须为正数");
        long friendshipId = requireActiveFriendship(readerId, friendId);
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM direct_message WHERE id=? AND friendship_id=?",
                Integer.class, throughMessageId, friendshipId);
        if (exists == null || exists == 0) throw new ApiException(HttpStatus.NOT_FOUND, "消息不存在");

        LocalDateTime readAt = LocalDateTime.now();
        int updated = jdbc.update("UPDATE direct_message SET read_at=? " +
                        "WHERE friendship_id=? AND receiver_id=? AND id<=? AND read_at IS NULL",
                readAt, friendshipId, readerId, throughMessageId);
        return new ReadReceipt(readerId, friendId, throughMessageId, readAt, updated);
    }

    @Transactional
    public ChatMessage markDelivered(long messageId) {
        LocalDateTime deliveredAt = LocalDateTime.now();
        jdbc.update("UPDATE direct_message SET delivered_at=? WHERE id=? AND delivered_at IS NULL",
                deliveredAt, messageId);
        return message(messageId);
    }

    public List<Long> activeFriendIds(long userId) {
        return jdbc.query("SELECT CASE WHEN user_low_id=? THEN user_high_id ELSE user_low_id END AS friend_id " +
                        "FROM friendship WHERE status='ACTIVE' AND (user_low_id=? OR user_high_id=?) ORDER BY friend_id",
                (rs, rowNum) -> rs.getLong("friend_id"), userId, userId, userId);
    }

    public boolean areActiveFriends(long firstUserId, long secondUserId) {
        if (firstUserId == secondUserId) return false;
        long low = Math.min(firstUserId, secondUserId);
        long high = Math.max(firstUserId, secondUserId);
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM friendship WHERE user_low_id=? AND user_high_id=? AND status='ACTIVE'",
                Integer.class, low, high);
        return count != null && count > 0;
    }

    private long requireActiveFriendship(long firstUserId, long secondUserId) {
        if (firstUserId == secondUserId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "不能给自己发送私聊消息");
        }
        long low = Math.min(firstUserId, secondUserId);
        long high = Math.max(firstUserId, secondUserId);
        List<Long> ids = jdbc.query(
                "SELECT id FROM friendship WHERE user_low_id=? AND user_high_id=? AND status='ACTIVE'",
                (rs, rowNum) -> rs.getLong(1), low, high);
        if (ids.isEmpty()) throw new ApiException(HttpStatus.FORBIDDEN, "只有好友可以私聊");
        return ids.get(0);
    }

    private SendResult duplicate(ChatMessage existing, long friendshipId, long friendId) {
        if (existing.friendshipId() != friendshipId || existing.receiverId() != friendId) {
            throw new ApiException(HttpStatus.CONFLICT, "clientMessageId 已用于另一条消息");
        }
        return new SendResult(existing, true);
    }

    private ChatMessage message(long messageId) {
        List<ChatMessage> rows = messages("SELECT * FROM direct_message WHERE id=?", messageId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "消息不存在");
        return rows.get(0);
    }

    private List<ChatMessage> messages(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> mapMessage(rs), args);
    }

    private ChatMessage mapMessage(ResultSet rs) throws SQLException {
        return new ChatMessage(
                rs.getLong("id"),
                rs.getLong("friendship_id"),
                rs.getLong("sender_id"),
                rs.getLong("receiver_id"),
                rs.getString("client_message_id"),
                rs.getString("content"),
                timestamp(rs, "sent_at"),
                timestamp(rs, "delivered_at"),
                timestamp(rs, "read_at"));
    }

    private LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, message);
        return value.strip();
    }

    public record ChatMessage(long id, long friendshipId, long senderId, long receiverId,
                              String clientMessageId, String content, LocalDateTime sentAt,
                              LocalDateTime deliveredAt, LocalDateTime readAt) {}

    public record SendResult(ChatMessage message, boolean duplicate) {}

    public record MessagePage(List<ChatMessage> items, boolean hasMore, Long nextBeforeId) {}

    public record ReadReceipt(long readerId, long friendId, long throughMessageId,
                              LocalDateTime readAt, int updatedCount) {}
}
