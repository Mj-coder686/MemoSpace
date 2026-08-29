package com.memospace.service;

import com.memospace.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AppearanceService {
    private final JdbcTemplate jdbc;

    public AppearanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> get(long userId) {
        ensure(userId);
        return jdbc.queryForMap("SELECT user_id,background_color,background_file_id,background_brightness," +
                "background_overlay,updated_at FROM user_appearance WHERE user_id=?", userId);
    }

    @Transactional
    public Map<String, Object> update(long userId, String backgroundColor, Long backgroundFileId,
                                      Integer brightness, Integer overlay, boolean clearImage) {
        ensure(userId);
        String color = requireColor(backgroundColor == null ? "#f5f2ec" : backgroundColor);
        int safeBrightness = range(brightness == null ? 100 : brightness, 25, 130, "背景亮度");
        int safeOverlay = range(overlay == null ? 0 : overlay, 0, 85, "遮罩强度");
        if (backgroundFileId != null) requireOwnedImage(userId, backgroundFileId);
        jdbc.update("UPDATE user_appearance SET background_color=?,background_brightness=?,background_overlay=?," +
                        "background_file_id=CASE WHEN ? THEN NULL WHEN ? IS NOT NULL THEN ? ELSE background_file_id END," +
                        "updated_at=CURRENT_TIMESTAMP WHERE user_id=?",
                color, safeBrightness, safeOverlay, clearImage, backgroundFileId, backgroundFileId, userId);
        return get(userId);
    }

    public void requireOwnedImage(long userId, long fileId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM file_record WHERE id=? AND owner_id=? AND mime_type LIKE 'image/%'",
                Integer.class, fileId, userId);
        if (count == null || count == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "背景图片不存在或不属于你");
    }

    private void ensure(long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user_appearance WHERE user_id=?", Integer.class, userId);
        if (count != null && count > 0) return;
        jdbc.update("INSERT INTO user_appearance(user_id) VALUES(?)", userId);
    }

    private String requireColor(String value) {
        String color = value.trim();
        if (!color.matches("^#[0-9a-fA-F]{6}$")) throw new ApiException(HttpStatus.BAD_REQUEST, "颜色格式不正确");
        return color.toLowerCase();
    }

    private int range(int value, int min, int max, String label) {
        if (value < min || value > max) throw new ApiException(HttpStatus.BAD_REQUEST, label + "超出允许范围");
        return value;
    }
}
