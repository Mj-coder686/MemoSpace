package com.memospace.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;

public final class JdbcIds {
    private JdbcIds() {}

    public static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, keyHolder);
        Number key = null;
        if (!keyHolder.getKeyList().isEmpty()) {
            var keys = keyHolder.getKeyList().get(0);
            Object value = keys.entrySet().stream()
                    .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                    .map(java.util.Map.Entry::getValue)
                    .findFirst()
                    .orElseGet(() -> keys.values().stream().findFirst().orElse(null));
            if (value instanceof Number number) key = number;
        }
        if (key == null) throw new IllegalStateException("数据库未返回新记录 ID");
        return key.longValue();
    }
}
