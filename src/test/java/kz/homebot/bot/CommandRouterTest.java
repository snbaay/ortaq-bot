package kz.homebot.bot;

import kz.homebot.service.MessageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandRouterTest {

    @Test
    void routeToMatchingHandler() {
        MessageService messageService = mock(MessageService.class);
        CommandHandler handler = new CommandHandler() {
            @Override
            public List<String> commands() {
                return List.of("test");
            }

            @Override
            public void handle(CommandContext ctx) {
                ctx.reply("handled test");
            }
        };

        CommandRouter router = new CommandRouter(List.of(handler), messageService);
        AtomicReference<String> replyHolder = new AtomicReference<>();

        CommandContext ctx = new CommandContext(
                123L, "private", new Sender(1L, "User", null), "test", "", List.of(), replyHolder::set);

        boolean handled = router.route(ctx);
        assertThat(handled).isTrue();
        assertThat(replyHolder.get()).isEqualTo("handled test");
    }

    @Test
    void unknownCommandReturnsFalse() {
        MessageService messageService = mock(MessageService.class);
        CommandRouter router = new CommandRouter(List.of(), messageService);

        CommandContext ctx = new CommandContext(
                123L, "private", new Sender(1L, "User", null), "unknown", "", List.of(), s -> {});

        boolean handled = router.route(ctx);
        assertThat(handled).isFalse();
    }

    @Test
    void catchHandlerExceptionAndReplyWithGenericError() {
        MessageService messageService = mock(MessageService.class);
        when(messageService.getMessage("error.generic")).thenReturn("Қате орын алды");

        CommandHandler failingHandler = new CommandHandler() {
            @Override
            public List<String> commands() {
                return List.of("fail");
            }

            @Override
            public void handle(CommandContext ctx) {
                throw new RuntimeException("Simulated unexpected failure");
            }
        };

        CommandRouter router = new CommandRouter(List.of(failingHandler), messageService);
        AtomicReference<String> replyHolder = new AtomicReference<>();

        CommandContext ctx = new CommandContext(
                123L, "group", new Sender(1L, "User", null), "fail", "", List.of(), replyHolder::set);

        boolean handled = router.route(ctx);
        assertThat(handled).isTrue();
        assertThat(replyHolder.get()).isEqualTo("Қате орын алды");
    }

    @Test
    void messageTruncationAt4096Chars() {
        List<String> replies = new ArrayList<>();
        CommandContext ctx = new CommandContext(
                123L, "private", new Sender(1L, "User", null), "long", "", List.of(), replies::add);

        String longText = "a".repeat(5000);
        ctx.reply(longText);

        assertThat(replies).hasSize(1);
        assertThat(replies.get(0)).hasSize(4096);
        assertThat(replies.get(0)).endsWith("…");
    }
}
