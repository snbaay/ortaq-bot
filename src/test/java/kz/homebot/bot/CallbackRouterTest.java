package kz.homebot.bot;

import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.callback.ProcessedUpdates;
import kz.homebot.config.AllowedChats;
import kz.homebot.domain.Member;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CallbackRouterTest {
    @Test
    void acknowledgesEveryTapButProcessesOnlyUniqueAuthorizedUpdates() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V2__processed_updates.sql")).execute(ds);
        var members = mock(MemberRegistrationService.class);
        when(members.findRegisteredMember(-100L, 7L)).thenReturn(Optional.of(new Member()));
        when(members.findRegisteredMember(-100L, 8L)).thenReturn(Optional.empty());
        var handler = mock(CallbackRouter.Handler.class);
        when(handler.kind()).thenReturn("m");
        var router = new CallbackRouter(new AllowedChats("-100"), members, new ProcessedUpdates(new JdbcTemplate(ds)),
                List.of(handler), mock(MessageService.class));
        var output = mock(TelegramOutput.class);

        router.route("test_bot", update(1, "a", -100, 7), output);
        router.route("test_bot", update(1, "b", -100, 7), output);
        router.route("test_bot", update(2, "a", -100, 7), output);
        router.route("test_bot", update(3, "c", -101, 7), output);
        router.route("test_bot", update(4, "d", -100, 8), output);

        verify(handler).handle(any());
        verify(output, times(5)).answer(any(), any());
        verify(members, never()).findRegisteredMember(-101L, 7L);
    }

    private Update update(int id, String callbackId, long chatId, long userId) {
        var bot = new User(10L, "Bot", true);
        bot.setUserName("test_bot");
        var message = new Message();
        message.setChat(Chat.builder().id(chatId).type("supergroup").build());
        message.setMessageId(10);
        message.setFrom(bot);
        var callback = new CallbackQuery();
        callback.setId(callbackId);
        callback.setMessage(message);
        callback.setFrom(new User(userId, "User", false));
        callback.setData("v2:m:abc");
        var update = new Update();
        update.setUpdateId(id);
        update.setCallbackQuery(callback);
        return update;
    }
}
