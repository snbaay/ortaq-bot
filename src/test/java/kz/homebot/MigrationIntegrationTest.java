package kz.homebot;

import kz.homebot.config.LegacySchemaMigrationStrategy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigrationIntegrationTest {
    @Autowired DataSource legacyDataSource;
    @Autowired JdbcTemplate legacyJdbc;
    @TempDir Path tempDir;

    @Test
    void existingPreV2SchemaIsBaselinedWithoutChangingLedgerOrCookingRows() {

        legacyJdbc.update("INSERT INTO household(id, telegram_chat_id, title, created_at) VALUES (10, -123, 'Home', CURRENT_TIMESTAMP)");
        legacyJdbc.update("INSERT INTO member(id, household_id, telegram_user_id, display_name, created_at) VALUES (11, 10, 101, 'A', CURRENT_TIMESTAMP), (12, 10, 102, 'B', CURRENT_TIMESTAMP), (13, 10, 103, 'C', CURRENT_TIMESTAMP)");
        legacyJdbc.update("INSERT INTO expense(id, household_id, payer_id, amount, description, created_at, deleted, settlement) VALUES (21, 10, 11, 90.00, 'Food', CURRENT_TIMESTAMP, FALSE, FALSE), (22, 10, 12, 30.00, 'Paid back', CURRENT_TIMESTAMP, FALSE, TRUE)");
        legacyJdbc.update("INSERT INTO expense_share(id, expense_id, member_id, share_amount) VALUES (31, 21, 11, 30.00), (32, 21, 12, 30.00), (33, 21, 13, 30.00), (34, 22, 11, 15.00), (35, 22, 12, 15.00)");
        legacyJdbc.update("INSERT INTO cooking_schedule(id, household_id, day_of_week, member_id) VALUES (41, 10, 'MONDAY', 11)");
        legacyJdbc.update("INSERT INTO cooking_swap(id, household_id, swap_date, from_member_id, to_member_id) VALUES (42, 10, DATE '2026-11-16', 11, 12)");
        legacyJdbc.update("INSERT INTO shopping_item(id, household_id, text, added_by, added_at, bought) VALUES (51, 10, 'Tea', 11, CURRENT_TIMESTAMP, FALSE)");
        legacyJdbc.update("INSERT INTO note(id, household_id, author_id, text, created_at, deleted) VALUES (61, 10, 12, 'Meeting', CURRENT_TIMESTAMP, FALSE)");
        legacyJdbc.update("INSERT INTO chore_log(id, household_id, member_id, text, created_at) VALUES (71, 10, 13, 'Cleaned', CURRENT_TIMESTAMP)");
        legacyJdbc.update("INSERT INTO payment_deadline(id, household_id, title, day_of_month, amount, created_by, active) VALUES (81, 10, 'Rent', 15, 600.00, 11, TRUE)");

        Map<String, List<Long>> allIds = legacyIds(legacyJdbc);
        List<String> balances = balances(legacyJdbc);
        var flyway = flyway(legacyDataSource);
        new LegacySchemaMigrationStrategy().migrate(flyway);
        new LegacySchemaMigrationStrategy().migrate(flyway);

        assertThat(legacyJdbc.queryForObject("SELECT \"version\" FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", String.class)).isEqualTo("1");
        assertThat(legacyJdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1'", Integer.class)).isEqualTo(1);
        assertThat(legacyIds(legacyJdbc)).isEqualTo(allIds);
        assertThat(legacyJdbc.queryForObject("SELECT sum(amount) FROM expense", java.math.BigDecimal.class)).isEqualByComparingTo("120.00");
        assertThat(legacyJdbc.queryForObject("SELECT sum(share_amount) FROM expense_share", java.math.BigDecimal.class)).isEqualByComparingTo("120.00");
        assertThat(balances(legacyJdbc)).isEqualTo(balances);
        assertThat(legacyJdbc.queryForObject("SELECT count(*) FROM cooking_schedule WHERE id = 41", Integer.class)).isEqualTo(1);
        assertThat(legacyJdbc.queryForObject("SELECT count(*) FROM cooking_swap WHERE id = 42", Integer.class)).isEqualTo(1);
    }

    @Test
    void emptyDatabaseCreatesLegacyTablesAndRepeatedMigrationDoesNothing() {
        DataSource dataSource = newDataSource();
        var flyway = flyway(dataSource);
        new LegacySchemaMigrationStrategy().migrate(flyway);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND table_name IN ('HOUSEHOLD','MEMBER','EXPENSE','EXPENSE_SHARE','COOKING_SCHEDULE','COOKING_SWAP','SHOPPING_ITEM','NOTE','CHORE_LOG','PAYMENT_DEADLINE')", Integer.class)).isEqualTo(10);
        new LegacySchemaMigrationStrategy().migrate(flyway);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void partialLegacySchemaIsRefusedWithoutBaselining() {
        DataSource dataSource = newDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE household(id BIGINT PRIMARY KEY)");
        assertThatThrownBy(() -> new LegacySchemaMigrationStrategy().migrate(flyway(dataSource)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND table_name = 'FLYWAY_SCHEMA_HISTORY'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC'", Integer.class)).isEqualTo(1);
    }

    @Test
    void failedMigrationRollsBackLedgerChange() throws Exception {
        DataSource dataSource = newDataSource();
        new LegacySchemaMigrationStrategy().migrate(flyway(dataSource));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO household(id, telegram_chat_id, title, created_at) VALUES (1, -100, 'Test', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO member(id, household_id, telegram_user_id, display_name, created_at) VALUES (1, 1, 100, 'Tester', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO expense(id, household_id, payer_id, amount, description, created_at, deleted, settlement) VALUES (1, 1, 1, 42.00, 'Test', CURRENT_TIMESTAMP, FALSE, FALSE)");
        Files.writeString(tempDir.resolve("V9999__intentional_failure.sql"),
                "UPDATE expense SET amount = 999.00 WHERE id = 1;\nINSERT INTO missing_table VALUES (1);\n");
        var broken = Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration", "filesystem:" + tempDir)
                .baselineVersion("1").cleanDisabled(true).load();
        assertThatThrownBy(broken::migrate).isInstanceOf(Exception.class);
        assertThat(jdbc.queryForObject("SELECT amount FROM expense WHERE id = 1", java.math.BigDecimal.class)).isEqualByComparingTo("42.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '9999' AND \"success\" = TRUE", Integer.class)).isZero();
    }

    private static List<String> balances(JdbcTemplate jdbc) {
        return jdbc.queryForList("""
                SELECT CAST(m.id AS VARCHAR) || ':' || CAST(
                    COALESCE((SELECT SUM(e.amount) FROM expense e WHERE e.payer_id = m.id AND NOT e.deleted), 0)
                  - COALESCE((SELECT SUM(s.share_amount) FROM expense_share s JOIN expense e ON e.id = s.expense_id
                              WHERE s.member_id = m.id AND NOT e.deleted), 0) AS VARCHAR)
                FROM member m ORDER BY m.id
                """, String.class);
    }

    private static Map<String, List<Long>> legacyIds(JdbcTemplate jdbc) {
        Map<String, List<Long>> ids = new LinkedHashMap<>();
        for (String table : List.of("household", "member", "expense", "expense_share", "cooking_schedule",
                "cooking_swap", "shopping_item", "note", "chore_log", "payment_deadline")) {
            ids.put(table, jdbc.queryForList("SELECT id FROM " + table + " ORDER BY id", Long.class));
        }
        return ids;
    }

    private static Flyway flyway(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineVersion("1").cleanDisabled(true).load();
    }

    private static DataSource newDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    }
}
