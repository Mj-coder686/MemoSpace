package com.memospace;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlywayExistingDatabaseMigrationTest {
    @Test
    void baselinesExistingSchemaThenAddsModerationWithoutLosingUsers() throws Exception {
        String url = "jdbc:h2:mem:flyway_existing_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        String baseline;
        try (var stream = getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
            if (stream == null) throw new IllegalStateException("Missing baseline migration");
            baseline = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            for (String statement : baseline.split(";")) {
                if (!statement.isBlank()) connection.createStatement().execute(statement);
            }
            connection.createStatement().execute("INSERT INTO user_account(public_id,username,password_hash,nickname) VALUES('100000009999','existing_user','hash','保留用户')");
        }

        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true).baselineVersion("1").cleanDisabled(true).load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var users = connection.createStatement().executeQuery("SELECT COUNT(*) FROM user_account WHERE username='existing_user'");
            users.next(); assertEquals(1, users.getInt(1));
            var history = connection.createStatement().executeQuery("SELECT MAX(version) FROM flyway_schema_history WHERE success=TRUE");
            history.next(); assertEquals("2", history.getString(1));
            var columns = connection.createStatement().executeQuery("SELECT violation_count,account_status FROM user_account WHERE username='existing_user'");
            columns.next(); assertEquals(0, columns.getInt(1)); assertEquals("ACTIVE", columns.getString(2));
        }
    }
}
