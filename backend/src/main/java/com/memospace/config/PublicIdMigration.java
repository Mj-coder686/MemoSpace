package com.memospace.config;

import com.memospace.service.PublicIdService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Compatibility migration for installations created before public_id existed.
 * schema.sql remains the source of truth for clean installations; this runner only upgrades old volumes.
 */
@Component
@Order(0)
public class PublicIdMigration implements ApplicationRunner {
    private static final String INDEX_NAME = "uk_user_account_public_id_runtime";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final PublicIdService publicIds;

    public PublicIdMigration(JdbcTemplate jdbc, DataSource dataSource, PublicIdService publicIds) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.publicIds = publicIds;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!hasColumn("user_account", "public_id")) {
            jdbc.execute("ALTER TABLE user_account ADD COLUMN public_id CHAR(12)");
        }

        Set<String> used = new HashSet<>();
        jdbc.queryForList("SELECT id,public_id FROM user_account ORDER BY id").forEach(row -> {
            Object value = row.get("public_id");
            String publicId = value == null ? null : String.valueOf(value).trim();
            if (publicId != null && publicId.matches("\\d{12}") && used.add(publicId)) return;

            String candidate;
            do candidate = publicIds.nextCandidate(); while (!used.add(candidate));
            jdbc.update("UPDATE user_account SET public_id=? WHERE id=?", candidate, row.get("id"));
        });

        if (!hasUniqueIndexOn("user_account", "public_id")) {
            jdbc.execute("CREATE UNIQUE INDEX " + INDEX_NAME + " ON user_account(public_id)");
        }
    }

    private boolean hasColumn(String table, String column) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet columns = metadata.getColumns(connection.getCatalog(), null, tableName, null)) {
                    while (columns.next()) {
                        if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
                    }
                }
            }
            return false;
        }
    }

    private boolean hasUniqueIndexOn(String table, String column) throws Exception {
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            for (String tableName : new String[]{table, table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, tableName, true, false)) {
                    while (indexes.next()) {
                        if (column.equalsIgnoreCase(indexes.getString("COLUMN_NAME"))) return true;
                    }
                }
            }
            return false;
        }
    }
}
