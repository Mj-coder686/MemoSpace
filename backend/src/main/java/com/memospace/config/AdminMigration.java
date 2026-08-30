package com.memospace.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Locale;

/** Adds administrator metadata to installations that already have a persistent database volume. */
@Component
@Order(2)
public class AdminMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public AdminMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!hasColumn("user_account", "is_admin")) {
            jdbc.execute("ALTER TABLE user_account ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE");
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS admin_audit_log (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "admin_id BIGINT NOT NULL," +
                "target_user_id BIGINT," +
                "action_type VARCHAR(40) NOT NULL," +
                "detail VARCHAR(300)," +
                "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private boolean hasColumn(String table, String column) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, null)) {
                    while (columns.next()) if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
                }
            }
            return false;
        }
    }
}
