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

/** Upgrades existing Docker volumes created before customizable appearance fields existed. */
@Component
@Order(1)
public class AppearanceMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public AppearanceMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        addColumn("space", "custom_primary_color", "VARCHAR(20)");
        addColumn("space", "custom_background_color", "VARCHAR(20)");
        addColumn("space", "custom_text_color", "VARCHAR(20)");
        addColumn("space", "background_file_id", "BIGINT");
        addColumn("space", "background_brightness", "INT NOT NULL DEFAULT 100");
        addColumn("space", "background_overlay", "INT NOT NULL DEFAULT 18");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_appearance (" +
                "user_id BIGINT PRIMARY KEY," +
                "background_color VARCHAR(20) NOT NULL DEFAULT '#f5f2ec'," +
                "background_file_id BIGINT," +
                "background_brightness INT NOT NULL DEFAULT 100," +
                "background_overlay INT NOT NULL DEFAULT 0," +
                "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    private void addColumn(String table, String column, String definition) throws Exception {
        if (!hasColumn(table, column)) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
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
