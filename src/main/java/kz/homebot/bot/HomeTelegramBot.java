package kz.homebot.bot;

import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class HomeTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(HomeTelegramBot.class);

    private final String botToken;
    private final String botUsername;
    private final TelegramClient telegramClient;
    private final MessageService messageService;
    private final MemberRegistrationService memberRegistrationService;
    private final CommandParser commandParser;
    private final CommandRouter commandRouter;
    private final ExpenseHandler expenseHandler;

    @Autowired
    public HomeTelegramBot(
            @Value("${bot.token}") String botToken,
            @Value("${bot.username}") String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService,
            CommandParser commandParser,
            CommandRouter commandRouter,
            ExpenseHandler expenseHandler) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.commandParser = commandParser;
        this.commandRouter = commandRouter;
        this.expenseHandler = expenseHandler;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    // Secondary constructor for testing (inject TelegramClient directly)
    public HomeTelegramBot(
            String botToken,
            String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService,
            CommandParser commandParser,
            CommandRouter commandRouter,
            ExpenseHandler expenseHandler,
            TelegramClient telegramClient) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.commandParser = commandParser;
        this.commandRouter = commandRouter;
        this.expenseHandler = expenseHandler;
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
            List<BotCommand> commands = new ArrayList<>();
            for (CommandHandler handler : commandRouter.getHandlers()) {
                for (String cmd : handler.commands()) {
                    String desc = messageService.getMessage("command." + cmd + ".description");
                    commands.add(new BotCommand(cmd, desc));
                }
            }
            telegramClient.execute(SetMyCommands.builder().commands(commands).build());
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
        String chatType = chat.getType();

        boolean isGroup = chat.isGroupChat() || chat.isSuperGroupChat()
                || "group".equalsIgnoreCase(chatType) || "supergroup".equalsIgnoreCase(chatType);

        User from = message.getFrom();
        if (isGroup && from != null) {
            memberRegistrationService.registerMember(chatId, chat.getTitle(), from);
        }

        Sender sender = from != null
                ? new Sender(from.getId(), buildSenderName(from), from.getUserName())
                : new Sender(0L, "Anonymous", null);

        CommandContext ctx = new CommandContext(
                chatId,
                chatType,
                sender,
                "",
                "",
                message.getEntities(),
                replyText -> send(chatId, replyText),
                text
        );

        Optional<CommandParser.ParsedCommand> parsed = commandParser.parse(text, botUsername);
        if (parsed.isPresent()) {
            CommandParser.ParsedCommand cmd = parsed.get();
            CommandContext routedCtx = new CommandContext(
                    chatId,
                    chatType,
                    sender,
                    cmd.command(),
                    cmd.args(),
                    message.getEntities(),
                    replyText -> send(chatId, replyText),
                    text
            );
            commandRouter.route(routedCtx);
        } else if (isGroup && startsWithDigit(text)) {
            expenseHandler.handlePlainExpense(ctx, text);
        }
    }

    private boolean startsWithDigit(String text) {
        return !text.isEmpty() && Character.isDigit(text.charAt(0));
    }

    private String buildSenderName(User from) {
        String first = from.getFirstName();
        String last = from.getLastName();
        if (first != null && last != null && !first.isBlank() && !last.isBlank()) {
            return first.trim() + " " + last.trim();
        } else if (first != null && !first.isBlank()) {
            return first.trim();
        } else if (last != null && !last.isBlank()) {
            return last.trim();
        }
        return "Пайдаланушы";
    }

    private void send(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
