package kz.homebot;

import kz.homebot.config.LegacySchemaMigrationStrategy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Human opt-in only: this test must never run as part of the normal local suite. */
class StagingMigrationRehearsalTest {
    private static final List<String> LEGACY_TABLES = List.of(
            "household", "member", "expense", "expense_share", "cooking_schedule",
            "cooking_swap", "shopping_item", "note", "chore_log", "payment_deadline");

    @Test
    @EnabledIfEnvironmentVariable(named = "ORTAQ_STAGING_MIGRATION_APPROVED", matches = "YES")
    void baselineOnlyTheSanitizedDisposablePostgresCopy() throws SQLException {
        String url = required("ORTAQ_STAGING_JDBC_URL");
        if (!url.startsWith("jdbc:postgresql://") || !url.contains("sslmode=require")) {
            throw new IllegalStateException("Staging JDBC URL must be PostgreSQL with sslmode=require");
        }
        DataSource dataSource = new DriverManagerDataSource(url,
                required("ORTAQ_STAGING_DB_USER"), required("ORTAQ_STAGING_DB_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
            assertEquals("ortaq_restore_check", connection.getCatalog(), "Refusing a non-staging database");
        }
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM member", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM member WHERE telegram_user_id >= 8000000000000000", Integer.class),
                "Refusing an unsanitized staging copy");

        Snapshot before = snapshot(jdbc);
        Flyway flyway = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration").baselineVersion("1")
                .target("1").cleanDisabled(true).load();
        new LegacySchemaMigrationStrategy().migrate(flyway);
        validateJpaMappings(dataSource);
        Snapshot after = snapshot(jdbc);
        assertTrue(before.equals(after), "Migration altered a legacy row, ID, amount, share or balance");
        new LegacySchemaMigrationStrategy().migrate(flyway);
        assertTrue(after.equals(snapshot(jdbc)), "Repeated migration changed legacy data");
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success = TRUE", Integer.class));
    }

    private static Snapshot snapshot(JdbcTemplate jdbc) {
        Map<String, List<Long>> ids = new LinkedHashMap<>();
        for (String table : LEGACY_TABLES) {
            ids.put(table, jdbc.queryForList("SELECT id FROM " + table + " ORDER BY id", Long.class));
        }
        List<Map<String, Object>> expenses = jdbc.queryForList(
                "SELECT id, household_id, payer_id, amount, deleted, settlement FROM expense ORDER BY id");
        List<Map<String, Object>> shares = jdbc.queryForList(
                "SELECT id, expense_id, member_id, share_amount FROM expense_share ORDER BY id");
        List<Map<String, Object>> balances = jdbc.queryForList("""
                SELECT m.id, m.household_id,
                       COALESCE((SELECT SUM(e.amount) FROM expense e WHERE e.payer_id = m.id AND NOT e.deleted), 0)
                     - COALESCE((SELECT SUM(s.share_amount) FROM expense_share s JOIN expense e ON e.id = s.expense_id
                                 WHERE s.member_id = m.id AND NOT e.deleted), 0) AS balance
                FROM member m ORDER BY m.id
                """);
        BigDecimal activeAmount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM expense WHERE NOT deleted", BigDecimal.class);
        BigDecimal activeShares = jdbc.queryForObject("""
                SELECT COALESCE(SUM(s.share_amount), 0) FROM expense_share s
                JOIN expense e ON e.id = s.expense_id WHERE NOT e.deleted
                """, BigDecimal.class);
        return new Snapshot(ids, expenses, shares, balances, activeAmount, activeShares);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing staging-only environment variable: " + key);
        }
        return value;
    }

    private static void validateJpaMappings(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("kz.homebot.domain");
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setDatabasePlatform("org.hibernate.dialect.PostgreSQLDialect");
        factory.setJpaVendorAdapter(adapter);
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        try {
            factory.afterPropertiesSet();
        } finally {
            factory.destroy();
        }
    }

    private record Snapshot(Map<String, List<Long>> ids, List<Map<String, Object>> expenses,
                            List<Map<String, Object>> shares, List<Map<String, Object>> balances,
                            BigDecimal activeAmount, BigDecimal activeShares) { }
}
