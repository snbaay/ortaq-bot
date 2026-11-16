package kz.homebot;

import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.service.DigestJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration",
        "spring.datasource.url=jdbc:h2:mem:clean_migration_startup;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CleanMigrationStartupTest {
    @MockBean HomeTelegramBot bot;
    @MockBean DigestJob digestJob;
    @Autowired JdbcTemplate jdbc;

    @Test
    void springStartsWithFlywayThenHibernateValidation() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", Integer.class))
                .isEqualTo(1);
    }
}
