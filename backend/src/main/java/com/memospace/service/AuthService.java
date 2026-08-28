package com.memospace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.memospace.api.ApiException;
import com.memospace.domain.UserAccount;
import com.memospace.domain.UserMapper;
import com.memospace.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthService {
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RelationshipCategoryService categories;
    private final PublicIdService publicIds;

    public AuthService(UserMapper users, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService,
                       RelationshipCategoryService categories, PublicIdService publicIds) {
        this.users = users;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.categories = categories;
        this.publicIds = publicIds;
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String nickname) {
        String normalized = username.trim().toLowerCase();
        if (users.selectCount(new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, normalized)) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "用户名已被使用");
        }
        UserAccount user = new UserAccount();
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname.trim());
        user.setBio("记录生活里值得珍藏的小事。");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        publicIds.insertWithPublicId(user);
        Long themeId = jdbc.queryForObject("SELECT id FROM space_theme WHERE preset_name='Midnight Mist' LIMIT 1", Long.class);
        long spaceId = JdbcIds.insert(jdbc,
                "INSERT INTO space(space_type,name,owner_id,visibility,theme_id,status) VALUES('PERSONAL',?,?,?,?, 'ACTIVE')",
                user.getNickname() + "的拾光空间", user.getId(), "PRIVATE", themeId);
        jdbc.update("INSERT INTO space_member(space_id,user_id,member_role) VALUES(?,?,'OWNER')", spaceId, user.getId());
        categories.ensureDefaults(user.getId());
        return session(user);
    }

    public Map<String, Object> login(String username, String password) {
        UserAccount user = users.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username.trim().toLowerCase()));
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码不正确");
        }
        return session(user);
    }

    public Map<String, Object> profile(long userId) {
        UserAccount user = users.selectById(userId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        return publicProfile(user);
    }

    public Map<String, Object> updateProfile(long userId, String nickname, String bio, String avatar,
                                             String gender, LocalDate birthday, String location) {
        UserAccount user = users.selectById(userId);
        if (nickname != null && !nickname.isBlank()) user.setNickname(nickname.trim());
        if (bio != null) user.setBio(bio.trim());
        if (avatar != null) user.setAvatar(avatar.trim());
        if (gender != null) user.setGender(gender);
        if (birthday != null) user.setBirthday(birthday);
        if (location != null) user.setLocation(location.trim());
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
        return publicProfile(user);
    }

    public void changePassword(long userId, String oldPassword, String newPassword) {
        UserAccount user = users.selectById(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
    }

    private Map<String, Object> session(UserAccount user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtService.issue(user.getId(), user.getUsername()));
        result.put("user", publicProfile(user));
        return result;
    }

    public static Map<String, Object> publicProfile(UserAccount user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("public_id", user.getPublicId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("bio", user.getBio());
        result.put("gender", user.getGender());
        result.put("birthday", user.getBirthday());
        result.put("location", user.getLocation());
        result.put("createdAt", user.getCreatedAt());
        return result;
    }
}
