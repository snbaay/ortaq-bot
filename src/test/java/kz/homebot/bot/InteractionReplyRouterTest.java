package kz.homebot.bot;

import kz.homebot.bot.interaction.InteractionReplyRouter;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.UtcTimestamp;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InteractionReplyRouterTest {
    @Test
    void restartReloadsOnlyTheOriginalUsersPromptInTheOriginalGroup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__legacy_schema.sql"),
                new ClassPathResource("db/migration/V3__interaction_sessions.sql")).execute(ds);
        var jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO household(id, telegram_chat_id, title, created_at) VALUES (1, -100, 'A', CURRENT_TIMESTAMP), (2, -101, 'B', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO member(id, household_id, telegram_user_id, display_name, created_at) VALUES (1, 1, 7, 'A', CURRENT_TIMESTAMP), (2, 1, 8, 'B', CURRENT_TIMESTAMP), (3, 2, 7, 'A', CURRENT_TIMESTAMP)");
        var home = new Household(-100L, "A", LocalDateTime.now());
        home.setId(1L);
        var other = new Household(-101L, "B", LocalDateTime.now());
        other.setId(2L);
        var owner = new Member(home, 7L, "A", LocalDateTime.now());
        var sessions = new SessionStore(jdbc);
        var original = sessions.open(owner, -100L, "purchase", "price", "{}");
        assertThat(sessions.bindPrompt(original, 10)).isTrue();

        var members = mock(MemberRegistrationService.class);
        when(members.findRegisteredMember(-100L, 7L)).thenReturn(Optional.of(owner));
        when(members.findRegisteredMember(-100L, 8L)).thenReturn(Optional.of(new Member(home, 8L, "B", LocalDateTime.now())));
        when(members.findRegisteredMember(-101L, 7L)).thenReturn(Optional.of(new Member(other, 7L, "A", LocalDateTime.now())));
        var handler = mock(InteractionReplyRouter.Handler.class);
        when(handler.replyKind()).thenReturn("purchase");
        var messages = mock(MessageService.class);
        when(messages.getMessage("v2.promptExpired")).thenReturn("expired");
        var output = mock(TelegramOutput.class);
        var restarted = new InteractionReplyRouter(new SessionStore(jdbc), members, messages, List.of(handler));

        restarted.route(message(-100, 8, 10), output);
        restarted.route(message(-101, 7, 10), output);
        restarted.route(message(-100, 7, null), output);
        restarted.route(message(-100, 7, 11), output);
        verifyNoInteractions(handler, output);
        var correct = message(-100, 7, 10);
        restarted.route(correct, output);
        verify(handler).replyKind();
        verify(handler).reply(argThat(row -> row.id().equals(original.id())), eq(owner), eq(correct), eq(output));

        jdbc.update("UPDATE interaction_session SET prompt_expires_at = ?", UtcTimestamp.from(Instant.now().minusSeconds(60)));
        restarted.route(correct, output);
        verify(output).send(-100L, "expired", null);
        verifyNoMoreInteractions(handler);

        assertThat(sessions.open(owner, -100L, "purchase", "price", "{}").id()).isEqualTo(original.id());
        jdbc.update("UPDATE interaction_session SET expires_at = ?", UtcTimestamp.from(Instant.now().minusSeconds(60)));
        assertThat(sessions.open(owner, -100L, "purchase", "price", "{}").id()).isNotEqualTo(original.id());
    }

    private Message message(long chatId, long userId, Integer replyId) {
        var message = new Message();
        message.setChat(Chat.builder().id(chatId).type("supergroup").build());
        message.setFrom(new User(userId, "User", false));
        message.setText("123");
        if (replyId != null) {
            var prompt = new Message();
            prompt.setMessageId(replyId);
            message.setReplyToMessage(prompt);
        }
        return message;
    }
}
