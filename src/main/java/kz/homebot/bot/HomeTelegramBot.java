package kz.homebot.bot;

import jakarta.annotation.PostConstruct;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

@Component
public class HomeTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(HomeTelegramBot.class);

    private final String botToken;
    private final String botUsername;
    private final TelegramClient telegramClient;
    private final MessageService messageService;
    private final MemberRegistrationService memberRegistrationService;

    public HomeTelegramBot(
            @Value("${bot.token}") String botToken,
            @Value("${bot.username}") String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    public HomeTelegramBot(
            String botToken,
            String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService,
            TelegramClient telegramClient) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.telegramClient = telegramClient;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @PostConstruct
    public void registerCommands() {
        try {
            List<BotCommand> commands = List.of(
                    new BotCommand("start", messageService.getMessage("command.start.description")),
                    new BotCommand("shygyn", messageService.getMessage("command.shygyn.description")),
                    new BotCommand("qaryz", messageService.getMessage("command.qaryz.description")),
                    new BotCommand("balans", messageService.getMessage("command.balans.description")),
                    new BotCommand("tolem", messageService.getMessage("command.tolem.description")),
                    new BotCommand("oshiru", messageService.getMessage("command.oshiru.description"))
            );
            SetMyCommands setMyCommands = SetMyCommands.builder()
                    .commands(commands)
                    .build();
            telegramClient.execute(setMyCommands);
            log.info("Telegram command menu registered successfully for @{}", botUsername);
        } catch (TelegramApiException e) {
            log.error("Failed to register bot commands menu: {}", e.getMessage());
        }
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        Chat chat = message.getChat();
        String text = message.getText().trim();
        Long chatId = message.getChatId();

        boolean isGroup = chat.isGroupChat() || chat.isSuperGroupChat()
                || "group".equals(chat.getType()) || "supergroup".equals(chat.getType());

        if (isGroup) {
            if (message.getFrom() != null) {
                memberRegistrationService.registerMember(chatId, chat.getTitle(), message.getFrom());
            }
        }

        if (isCommand(text, "/start")) {
            handleStartCommand(chatId);
        }
    }

    private boolean isCommand(String text, String command) {
        if (text.equals(command)) {
            return true;
        }
        return text.equalsIgnoreCase(command + "@" + botUsername);
    }

    private void handleStartCommand(Long chatId) {
        String greeting = messageService.getMessage("bot.start.greeting");
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(greeting)
                .build();
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send start message to chatId {}: {}", chatId, e.getMessage());
        }
    }
}
