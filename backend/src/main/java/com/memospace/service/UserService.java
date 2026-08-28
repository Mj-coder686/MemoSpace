package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserService {
    private final JdbcTemplate jdbc;

    public UserService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> search(long currentUser, String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        String q = "%" + normalized + "%";
        return jdbc.queryForList("SELECT u.id,u.public_id,u.username,u.nickname,u.avatar,u.bio,u.location," +
                        "CASE WHEN f.follower_id IS NULL THEN FALSE ELSE TRUE END AS following," +
                        "CASE " +
                        "WHEN EXISTS(SELECT 1 FROM friendship fs WHERE fs.status='ACTIVE' AND " +
                        "((fs.user_low_id=? AND fs.user_high_id=u.id) OR (fs.user_low_id=u.id AND fs.user_high_id=?))) THEN 'FRIEND' " +
                        "WHEN EXISTS(SELECT 1 FROM friend_request fr WHERE fr.status='PENDING' AND fr.sender_id=? AND fr.receiver_id=u.id) THEN 'OUTGOING' " +
                        "WHEN EXISTS(SELECT 1 FROM friend_request fr WHERE fr.status='PENDING' AND fr.receiver_id=? AND fr.sender_id=u.id) THEN 'INCOMING' " +
                        "ELSE 'NONE' END AS friend_state " +
                        "FROM user_account u LEFT JOIN user_follow f ON f.following_id=u.id AND f.follower_id=? " +
                        "WHERE u.id<>? AND NOT EXISTS(SELECT 1 FROM user_block b WHERE " +
                        "(b.blocker_id=? AND b.blocked_id=u.id) OR (b.blocker_id=u.id AND b.blocked_id=?)) " +
                        "AND (u.public_id=? OR u.public_id LIKE ? OR LOWER(u.nickname) LIKE LOWER(?) OR LOWER(u.username) LIKE LOWER(?)) " +
                        "ORDER BY CASE WHEN u.public_id=? THEN 0 ELSE 1 END,u.nickname LIMIT 30",
                currentUser, currentUser, currentUser, currentUser,
                currentUser, currentUser, currentUser, currentUser, normalized, q, q, q, normalized);
    }

    public Map<String, Object> profile(long viewer, long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.id,u.public_id,u.username,u.nickname,u.avatar,u.bio,u.location,u.created_at," +
                "(SELECT COUNT(*) FROM user_follow WHERE following_id=u.id) AS followers," +
                "(SELECT COUNT(*) FROM user_follow WHERE follower_id=u.id) AS following," +
                "(SELECT COUNT(*) FROM memory WHERE creator_id=u.id AND visibility='PUBLIC') AS public_memories," +
                "CASE WHEN EXISTS(SELECT 1 FROM user_follow WHERE follower_id=? AND following_id=u.id) THEN TRUE ELSE FALSE END AS is_following " +
                "FROM user_account u WHERE u.id=?", viewer, userId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        return rows.get(0);
    }

    @Transactional
    public boolean toggleFollow(long userId, long targetId) {
        if (userId == targetId) throw new ApiException(HttpStatus.BAD_REQUEST, "不能关注自己");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE id=?", Integer.class, targetId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        int exists = jdbc.queryForObject("SELECT COUNT(*) FROM user_follow WHERE follower_id=? AND following_id=?", Integer.class, userId, targetId);
        if (exists > 0) {
            jdbc.update("DELETE FROM user_follow WHERE follower_id=? AND following_id=?", userId, targetId);
            return false;
        }
        jdbc.update("INSERT INTO user_follow(follower_id,following_id) VALUES(?,?)", userId, targetId);
        jdbc.update("INSERT INTO notification(user_id,actor_id,notification_type,title,content) VALUES(?,?,'FOLLOW','有人关注了你','去看看 TA 的记忆空间吧')", targetId, userId);
        return true;
    }

    @Transactional
    public boolean toggleBlock(long userId, long targetId) {
        if (userId == targetId) throw new ApiException(HttpStatus.BAD_REQUEST, "不能拉黑自己");
        int exists = jdbc.queryForObject("SELECT COUNT(*) FROM user_block WHERE blocker_id=? AND blocked_id=?", Integer.class, userId, targetId);
        if (exists > 0) {
            jdbc.update("DELETE FROM user_block WHERE blocker_id=? AND blocked_id=?", userId, targetId);
            return false;
        }
        jdbc.update("INSERT INTO user_block(blocker_id,blocked_id) VALUES(?,?)", userId, targetId);
        jdbc.update("DELETE FROM user_follow WHERE (follower_id=? AND following_id=?) OR (follower_id=? AND following_id=?)",
                userId, targetId, targetId, userId);
        jdbc.update("UPDATE relationship_invitation SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP WHERE status='PENDING' AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",
                userId, targetId, targetId, userId);
        jdbc.update("UPDATE friend_request SET status='EXPIRED',responded_at=CURRENT_TIMESTAMP WHERE status='PENDING' AND ((sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?))",
                userId, targetId, targetId, userId);
        jdbc.update("UPDATE friendship SET status='ENDED',ended_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP " +
                        "WHERE status='ACTIVE' AND ((user_low_id=? AND user_high_id=?) OR (user_low_id=? AND user_high_id=?))",
                userId, targetId, targetId, userId);
        jdbc.update("DELETE FROM friend_setting WHERE (owner_id=? AND friend_id=?) OR (owner_id=? AND friend_id=?)",
                userId, targetId, targetId, userId);
        return true;
    }
}
