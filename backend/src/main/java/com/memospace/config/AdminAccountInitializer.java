package com.memospace.config;

import com.memospace.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Creates the configured local administrator once without ever overwriting its password on restart. */
@Component
@Order(20)
public class AdminAccountInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final JdbcTemplate jdbc;
    private final AuthService auth;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String nickname;

    public AdminAccountInitializer(JdbcTemplate jdbc, AuthService auth,
                                   @Value("${app.admin.enabled}") boolean enabled,
                                   @Value("${app.admin.username}") String username,
                                   @Value("${app.admin.password}") String password,
                                   @Value("${app.admin.nickname}") String nickname) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.enabled = enabled;
        this.username = username.trim().toLowerCase();
        this.password = password;
        this.nickname = nickname.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id,is_admin FROM user_account WHERE username=?", username);
        if (!existing.isEmpty()) {
            Object rawFlag = existing.get(0).get("is_admin");
            boolean administrator = Boolean.TRUE.equals(rawFlag)
                    || (rawFlag instanceof Number && ((Number) rawFlag).intValue() != 0);
            if (!administrator) {
                log.warn("Configured administrator username '{}' is already used by a normal account; administrator was not enabled", username);
            }
            return;
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException("ADMIN_PASSWORD must contain 8-72 characters");
        }
        auth.register(username, password, nickname);
        jdbc.update("UPDATE user_account SET is_admin=TRUE WHERE username=?", username);
        log.info("Local administrator account '{}' created", username);
    }
}
