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
    private final String accounts;

    public AdminAccountInitializer(JdbcTemplate jdbc, AuthService auth,
                                   @Value("${app.admin.enabled}") boolean enabled,
                                   @Value("${app.admin.accounts:}") String accounts,
                                   @Value("${app.admin.username}") String username,
                                   @Value("${app.admin.password}") String password,
                                   @Value("${app.admin.nickname}") String nickname) {
        this.jdbc = jdbc;
        this.auth = auth;
        this.enabled = enabled;
        this.accounts = accounts == null ? "" : accounts.trim();
        this.username = username.trim().toLowerCase();
        this.password = password;
        this.nickname = nickname.trim();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        List<AdminSeed> seeds = configuredAccounts();
        if (seeds.isEmpty()) seeds = List.of(new AdminSeed(username, password, nickname));
        for (AdminSeed seed : seeds) create(seed);
    }

    private void create(AdminSeed seed) {
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id,is_admin FROM user_account WHERE username=?", seed.username());
        if (!existing.isEmpty()) {
            Object rawFlag = existing.get(0).get("is_admin");
            boolean administrator = Boolean.TRUE.equals(rawFlag)
                    || (rawFlag instanceof Number && ((Number) rawFlag).intValue() != 0);
            if (!administrator) {
                log.warn("Configured administrator username '{}' is already used by a normal account; administrator was not enabled", seed.username());
            }
            return;
        }
        if (seed.password().length() < 8 || seed.password().length() > 72) {
            throw new IllegalStateException("Every configured administrator password must contain 8-72 characters");
        }
        auth.register(seed.username(), seed.password(), seed.nickname());
        jdbc.update("UPDATE user_account SET is_admin=TRUE WHERE username=?", seed.username());
        log.info("Local administrator account '{}' created", seed.username());
    }

    private List<AdminSeed> configuredAccounts() {
        if (accounts.isBlank()) return List.of();
        return java.util.Arrays.stream(accounts.split(";"))
                .map(String::trim).filter(value -> !value.isBlank())
                .map(value -> {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) {
                        throw new IllegalStateException("ADMIN_ACCOUNTS entries must use username|password|nickname");
                    }
                    return new AdminSeed(parts[0].trim().toLowerCase(), parts[1], parts[2].trim());
                }).toList();
    }

    private record AdminSeed(String username, String password, String nickname) {}
}
