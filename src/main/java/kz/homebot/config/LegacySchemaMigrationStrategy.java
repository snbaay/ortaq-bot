package kz.homebot.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Baselines only a complete, recognizable pre-V2 schema. Never baselines an arbitrary nonempty DB. */
@Component
public class LegacySchemaMigrationStrategy implements FlywayMigrationStrategy {
    private static final Map<String, Set<String>> LEGACY_COLUMNS = legacyColumns();

    @Override
    public void migrate(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String schema = connection.getSchema();
            Set<String> tables = tableNames(metadata, connection.getCatalog(), schema);
            if (tables.contains("flyway_schema_history") || tables.isEmpty()) {
                flyway.migrate();
                return;
            }
            if (!tables.equals(LEGACY_COLUMNS.keySet())) {
                throw new IllegalStateException("Refusing Flyway baseline: legacy table set does not match");
            }
            for (var entry : LEGACY_COLUMNS.entrySet()) {
                String table = entry.getKey();
                Set<String> actual = columnNames(metadata, connection.getCatalog(), schema, table);
                if (!actual.containsAll(entry.getValue()) || !primaryKeyIsId(metadata, connection.getCatalog(), schema, table)) {
                    throw new IllegalStateException("Refusing Flyway baseline: legacy schema differs at " + table);
                }
            }
            checkMoneyColumn(metadata, connection.getCatalog(), schema, "expense", "amount");
            checkMoneyColumn(metadata, connection.getCatalog(), schema, "expense_share", "share_amount");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect the legacy schema before Flyway", e);
        }
        flyway.baseline();
        flyway.migrate();
    }

    private static Set<String> tableNames(DatabaseMetaData metadata, String catalog, String schema) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (ResultSet rows = metadata.getTables(catalog, schema, "%", null)) {
            while (rows.next()) {
                String type = rows.getString("TABLE_TYPE");
                if ("TABLE".equalsIgnoreCase(type) || "BASE TABLE".equalsIgnoreCase(type)) {
                    names.add(normalize(rows.getString("TABLE_NAME")));
                }
            }
        }
        return names;
    }

    private static Set<String> columnNames(DatabaseMetaData metadata, String catalog, String schema, String table) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (ResultSet rows = metadata.getColumns(catalog, schema, "%", "%")) {
            while (rows.next()) {
                if (table.equals(normalize(rows.getString("TABLE_NAME")))) {
                    names.add(normalize(rows.getString("COLUMN_NAME")));
                }
            }
        }
        return names;
    }

    private static boolean primaryKeyIsId(DatabaseMetaData metadata, String catalog, String schema, String table) throws SQLException {
        Set<String> keys = new TreeSet<>();
        String actualTable = null;
        try (ResultSet rows = metadata.getTables(catalog, schema, "%", null)) {
            while (rows.next()) {
                if (table.equals(normalize(rows.getString("TABLE_NAME")))) {
                    actualTable = rows.getString("TABLE_NAME");
                    break;
                }
            }
        }
        if (actualTable == null) {
            return false;
        }
        try (ResultSet rows = metadata.getPrimaryKeys(catalog, schema, actualTable)) {
            while (rows.next()) {
                if (table.equals(normalize(rows.getString("TABLE_NAME")))) {
                    keys.add(normalize(rows.getString("COLUMN_NAME")));
                }
            }
        }
        return keys.equals(Set.of("id"));
    }

    private static void checkMoneyColumn(DatabaseMetaData metadata, String catalog, String schema,
                                         String table, String column) throws SQLException {
        try (ResultSet rows = metadata.getColumns(catalog, schema, "%", "%")) {
            while (rows.next()) {
                if (table.equals(normalize(rows.getString("TABLE_NAME")))
                        && column.equals(normalize(rows.getString("COLUMN_NAME")))) {
                    int type = rows.getInt("DATA_TYPE");
                    if ((type != Types.DECIMAL && type != Types.NUMERIC)
                            || rows.getInt("DECIMAL_DIGITS") != 2) {
                        throw new IllegalStateException("Refusing Flyway baseline: money column differs at " + table);
                    }
                    return;
                }
            }
        }
        throw new IllegalStateException("Refusing Flyway baseline: money column missing at " + table);
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Set<String>> legacyColumns() {
        Map<String, Set<String>> columns = new LinkedHashMap<>();
        columns.put("household", Set.of("id", "telegram_chat_id", "title", "created_at", "last_digest_date"));
        columns.put("member", Set.of("id", "household_id", "telegram_user_id", "display_name", "telegram_username", "created_at"));
        columns.put("expense", Set.of("id", "household_id", "payer_id", "amount", "description", "created_at", "deleted", "settlement"));
        columns.put("expense_share", Set.of("id", "expense_id", "member_id", "share_amount"));
        columns.put("cooking_schedule", Set.of("id", "household_id", "day_of_week", "member_id"));
        columns.put("cooking_swap", Set.of("id", "household_id", "swap_date", "from_member_id", "to_member_id"));
        columns.put("shopping_item", Set.of("id", "household_id", "text", "added_by", "added_at", "bought", "bought_by", "bought_at"));
        columns.put("note", Set.of("id", "household_id", "author_id", "text", "created_at", "deleted"));
        columns.put("chore_log", Set.of("id", "household_id", "member_id", "text", "created_at"));
        columns.put("payment_deadline", Set.of("id", "household_id", "title", "day_of_month", "amount", "created_by", "active"));
        return Map.copyOf(columns);
    }
}
