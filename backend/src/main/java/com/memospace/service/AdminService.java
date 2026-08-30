package com.memospace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.memospace.api.ApiException;
import com.memospace.domain.UserAccount;
import com.memospace.domain.UserMapper;
import com.memospace.security.JwtService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class AdminService {
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public AdminService(UserMapper users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwt) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    public Map<String, Object> login(String username, String password) {
        UserAccount user = users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username.trim().toLowerCase()));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "管理员账号或密码不正确");
        }
        if (!Boolean.TRUE.equals(user.getAdmin())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "该账号不是管理员账号");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwt.issueAdmin(user.getId(), user.getUsername()));
        result.put("user", adminProfile(user));
        return result;
    }

    public Map<String, Object> me(long adminId) {
        UserAccount user = requireUser(adminId);
        if (!Boolean.TRUE.equals(user.getAdmin())) throw new ApiException(HttpStatus.FORBIDDEN, "管理员权限已失效");
        return adminProfile(user);
    }

    public Map<String, Object> users(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        int offset = (safePage - 1) * safeSize;
        String normalized = keyword == null ? "" : keyword.trim();
        String where = normalized.isEmpty() ? "" :
                " WHERE public_id=? OR LOWER(username) LIKE LOWER(?) OR LOWER(nickname) LIKE LOWER(?)";
        Object[] searchArgs = normalized.isEmpty() ? new Object[]{} :
                new Object[]{normalized, "%" + normalized + "%", "%" + normalized + "%"};
        Long total = normalized.isEmpty()
                ? jdbc.queryForObject("SELECT COUNT(*) FROM user_account", Long.class)
                : jdbc.queryForObject("SELECT COUNT(*) FROM user_account" + where, Long.class, searchArgs);

        String select = "SELECT id,public_id,username,nickname,is_admin,created_at FROM user_account" +
                where + " ORDER BY is_admin DESC,id DESC LIMIT ? OFFSET ?";
        Object[] queryArgs = new Object[searchArgs.length + 2];
        System.arraycopy(searchArgs, 0, queryArgs, 0, searchArgs.length);
        queryArgs[queryArgs.length - 2] = safeSize;
        queryArgs[queryArgs.length - 1] = offset;
        List<Map<String, Object>> items = jdbc.queryForList(select, queryArgs);
        return Map.of("items", items, "total", total == null ? 0L : total, "page", safePage, "size", safeSize);
    }

    @Transactional
    public Map<String, Object> resetPassword(long adminId, long targetUserId, String newPassword) {
        UserAccount target = requireUser(targetUserId);
        target.setPasswordHash(passwordEncoder.encode(newPassword));
        target.setUpdatedAt(LocalDateTime.now());
        users.updateById(target);
        audit(adminId, targetUserId, "RESET_PASSWORD", "管理员重置了用户登录密码");
        return Map.of("message", "临时密码已经生效", "userId", targetUserId);
    }

    @Transactional
    public Map<String, Object> changeMemoId(long adminId, long targetUserId, String memoId) {
        UserAccount target = requireUser(targetUserId);
        String normalized = memoId.trim();
        if (normalized.equals(target.getPublicId())) return userRow(targetUserId);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM user_account WHERE public_id=? AND id<>?",
                Integer.class, normalized, targetUserId);
        if (exists != null && exists > 0) throw new ApiException(HttpStatus.CONFLICT, "这个 Memo ID 已经被其他用户使用");
        try {
            jdbc.update("UPDATE user_account SET public_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", normalized, targetUserId);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "这个 Memo ID 已经被其他用户使用");
        }
        audit(adminId, targetUserId, "CHANGE_MEMO_ID", "Memo ID 从 " + target.getPublicId() + " 修改为 " + normalized);
        return userRow(targetUserId);
    }

    public List<Map<String, Object>> audit(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return jdbc.queryForList("SELECT l.id,l.action_type,l.detail,l.created_at,l.target_user_id," +
                "a.nickname AS admin_nickname,t.nickname AS target_nickname,t.public_id AS target_public_id " +
                "FROM admin_audit_log l JOIN user_account a ON a.id=l.admin_id " +
                "LEFT JOIN user_account t ON t.id=l.target_user_id ORDER BY l.id DESC LIMIT ?", safeLimit);
    }

    private UserAccount requireUser(long userId) {
        UserAccount user = users.selectById(userId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        return user;
    }

    private void audit(long adminId, long targetUserId, String action, String detail) {
        jdbc.update("INSERT INTO admin_audit_log(admin_id,target_user_id,action_type,detail) VALUES(?,?,?,?)",
                adminId, targetUserId, action, detail);
    }

    private Map<String, Object> userRow(long userId) {
        return jdbc.queryForMap("SELECT id,public_id,username,nickname,is_admin,created_at FROM user_account WHERE id=?", userId);
    }

    private Map<String, Object> adminProfile(UserAccount user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("public_id", user.getPublicId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("is_admin", true);
        return result;
    }
}
