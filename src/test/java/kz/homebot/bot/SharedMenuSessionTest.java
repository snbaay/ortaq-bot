package kz.homebot.bot;

import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.interaction.InteractionSession;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SharedMenuSessionTest {
    @Test
    void aSharedMenuOpensIndependentDraftsAndOnlyTheOwnerCanFinishOnce() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__legacy_schema.sql"),
                new ClassPathResource("db/migration/V3__interaction_sessions.sql")).execute(ds);
        var jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO household(id, telegram_chat_id, title, created_at) VALUES (1, -100, 'A', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO member(id, household_id, telegram_user_id, display_name, created_at) VALUES (1, 1, 7, 'A', CURRENT_TIMESTAMP), (2, 1, 8, 'B', CURRENT_TIMESTAMP)");
        var household = new Household(-100L, "A");
        household.setId(1L);
        var first = new Member(household, 7L, "A");
        var second = new Member(household, 8L, "B");
        var sessions = new SessionStore(jdbc);
        var menu = sessions.shared(first, -100, "menu", "main");
        assertThat(sessions.bindMenu(menu, 10)).isTrue();

        var draftA = sessions.withButton(call(first, menu, 10, "purchase"), "menu",
                (row, action) -> sessions.open(first, -100, action, "review", "{}")).orElseThrow();
        var draftB = sessions.withButton(call(second, menu, 10, "purchase"), "menu",
                (row, action) -> sessions.open(second, -100, action, "review", "{}")).orElseThrow();
        assertThat(draftA.id()).isNotEqualTo(draftB.id());
        var edited = sessions.advance(draftA, "review", "{}", false);
        assertThat(sessions.bindMenu(draftA, 11)).isFalse();
        assertThat(sessions.bindMenu(edited, 12)).isTrue();
        assertThat(sessions.withConfirmation(call(second, edited, 12, "confirm"), "purchase",
                (row, action) -> "foreign")).isEmpty();
        assertThat(sessions.withConfirmation(call(first, edited, 13, "confirm"), "purchase",
                (row, action) -> "forwarded")).isEmpty();

        var writes = new AtomicInteger();
        for (int tap = 0; tap < 2; tap++) {
            assertThat(sessions.withConfirmation(call(first, edited, 12, "confirm"), "purchase", (row, action) -> {
                if ("confirmed".equals(row.state())) {
                    return row.payload();
                }
                writes.incrementAndGet();
                sessions.finish(row, "receipt1");
                return "receipt1";
            })).contains("receipt1");
        }
        assertThat(writes.get()).isEqualTo(1);
        assertThat(sessions.withButton(call(first, edited, 12, "cancel"), "purchase",
                (row, action) -> "changed")).isEmpty();
    }

    private CallbackRouter.Call call(Member member, InteractionSession session, int messageId, String action) {
        var message = new Message();
        message.setChat(Chat.builder().id(-100L).type("group").build());
        message.setMessageId(messageId);
        return new CallbackRouter.Call(member, message, session.id() + ":" + Integer.toString(session.version(), 36)
                + ":" + action, "test_bot", 1, null, null);
    }
}
