package kz.homebot;

import kz.homebot.bot.CommandParser;
import kz.homebot.bot.CommandRouter;
import kz.homebot.bot.ExpenseHandler;
import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.bot.StartHelpHandler;
import kz.homebot.config.AllowedChats;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeTelegramBotTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private MessageService messageService;

    @Mock
    private MemberRegistrationService memberRegistrationService;

    private HomeTelegramBot bot;

    @BeforeEach
    void setUp() {
        CommandParser parser = new CommandParser();
        StartHelpHandler startHelpHandler = new StartHelpHandler(messageService);
        ExpenseHandler expenseHandler = mock(ExpenseHandler.class);
        CommandRouter router = new CommandRouter(List.of(startHelpHandler, expenseHandler), messageService);

        bot = new HomeTelegramBot(
                "123456:fake_token",
                "test_bot",
                messageService,
                memberRegistrationService,
                parser,
                router,
                expenseHandler,
                telegramClient
        );
    }

    @Test
    void privateChatStartShouldReplyAndNotCallRegistrationService() throws Exception {
        Update update = new Update();
        Message message = new Message();
        Chat chat = Chat.builder()
                .id(999L)
                .type("private")
                .build();

        User user = new User(999L, "Askar", false);

        message.setChat(chat);
        message.setFrom(user);
        message.setText("/start");
        update.setMessage(message);

        when(messageService.getMessage("bot.start.greeting")).thenReturn("Сәлем!");

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("999", captor.getValue().getChatId());
        assertEquals("Сәлем!", captor.getValue().getText());

        verify(memberRegistrationService, never()).registerMember(any(), any(), any(User.class));
    }

    @Test
    void komekCommandShouldReturnHelpText() throws Exception {
        Update update = new Update();
        Message message = new Message();
        Chat chat = Chat.builder()
                .id(888L)
                .type("private")
                .build();

        User user = new User(888L, "Berik", false);

        message.setChat(chat);
        message.setFrom(user);
        message.setText("/komek");
        update.setMessage(message);

        when(messageService.getMessage("bot.komek.text")).thenReturn("Көмек мәтіні");

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertEquals("888", captor.getValue().getChatId());
        assertEquals("Көмек мәтіні", captor.getValue().getText());
    }

    @Test
    void groupChatMessageShouldCallRegistrationService() {
        Update update = new Update();
        Message message = new Message();
        Chat chat = Chat.builder()
                .id(-100123456789L)
                .type("group")
                .title("Flat 404")
                .build();

        User user = new User(555L, "Dias", false);
        user.setLastName("Ospan");

        message.setChat(chat);
        message.setFrom(user);
        message.setText("Сәлем достар");
        update.setMessage(message);

        bot.consume(update);

        verify(memberRegistrationService).registerMember(eq(-100123456789L), eq("Flat 404"), eq(user));
    }

    @Test
    void supergroupCommandShouldCallRegistrationService() {
        Update update = new Update();
        Message message = new Message();
        Chat chat = Chat.builder()
                .id(-100987654321L)
                .type("supergroup")
                .title("Super Flat")
                .build();

        User user = new User(777L, "Madina", false);

        message.setChat(chat);
        message.setFrom(user);
        message.setText("/shygyn 1500 nan");
        update.setMessage(message);

        bot.consume(update);

        verify(memberRegistrationService).registerMember(eq(-100987654321L), eq("Super Flat"), eq(user));
    }

    @Test
    void ignoresUnlistedChatBeforeRegistration() {
        CommandParser parser = new CommandParser();
        ExpenseHandler expenseHandler = mock(ExpenseHandler.class);
        CommandRouter router = new CommandRouter(List.of(new StartHelpHandler(messageService), expenseHandler), messageService);
        HomeTelegramBot restricted = new HomeTelegramBot("123456:fake_token", "test_bot", messageService,
                memberRegistrationService, parser, router, expenseHandler, telegramClient, new AllowedChats("-1"));
        Update update = new Update();
        Message message = new Message();
        message.setChat(Chat.builder().id(-2L).type("group").title("Other").build());
        message.setFrom(new User(2L, "User", false));
        message.setText("/start");
        update.setMessage(message);
        restricted.consume(update);
        verify(memberRegistrationService, never()).registerMember(any(), any(), any(User.class));
    }
}
