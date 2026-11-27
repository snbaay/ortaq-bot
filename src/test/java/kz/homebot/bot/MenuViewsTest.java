package kz.homebot.bot;

import kz.homebot.bot.interaction.InteractionSession;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.bot.menu.MenuViews;
import kz.homebot.domain.Member;
import kz.homebot.service.MessageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuViewsTest {
    @Test
    void groupStartHasSixInlineRowsWithOpaqueShortCallbacks() {
        var store = mock(SessionStore.class);
        var output = mock(TelegramOutput.class);
        var messages = mock(MessageService.class);
        when(messages.getMessage(anyString(), any(Object[].class))).thenAnswer(call -> call.getArgument(0));
        var member = new Member();
        var session = new InteractionSession("ab".repeat(16), 10, null, -100, "menu", "main", "{}",
                null, null, null, 0, Instant.now().plusSeconds(10), null);
        when(store.shared(member, -100L, "menu", "main")).thenReturn(session);
        var sent = new Message();
        sent.setMessageId(2);
        when(output.send(eq(-100L), eq("menu"), any(ReplyKeyboard.class))).thenReturn(sent);

        new MenuViews(store, messages).main(member, -100L, "menu", output);

        var markup = ArgumentCaptor.forClass(InlineKeyboardMarkup.class);
        verify(output).send(eq(-100L), eq("menu"), markup.capture());
        var rows = markup.getValue().getKeyboard();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row).hasSize(1);
            assertThat(row.getFirst().getCallbackData().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(64);
        });
        assertThat(rows.stream().map(row -> row.getFirst().getText()).toList()).isEqualTo(List.of(
                "menu.cook", "menu.products", "menu.finance", "menu.calls", "menu.other", "menu.settings"));
        verify(store).bindMenu(session, 2);
    }
}
