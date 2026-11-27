package kz.homebot.bot;

import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.callback.ProcessedUpdates;
import kz.homebot.bot.interaction.InteractionReplyRouter;
import kz.homebot.bot.menu.MenuViews;
import kz.homebot.domain.Member;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.config.AllowedChats;
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
    private final TelegramOutput output;
    private final CallbackRouter callbacks;
    private final ProcessedUpdates updates;
    private final InteractionReplyRouter replies;
    private final MenuViews menus;
    private final AllowedChats allowedChats;

    @Autowired
    public HomeTelegramBot(
            @Value("${bot.token}") String botToken,
            @Value("${bot.username}") String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService,
            CommandParser commandParser,
            CommandRouter commandRouter,
            AllowedChats allowedChats, CallbackRouter callbacks, ProcessedUpdates updates, InteractionReplyRouter replies, MenuViews menus) {
        this(botToken, botUsername, messageService, memberRegistrationService, commandParser, commandRouter,
                new OkHttpTelegramClient(botToken), allowedChats, callbacks, updates, replies, menus);
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
        this(botToken, botUsername, messageService, memberRegistrationService,
                commandParser, commandRouter, expenseHandler, telegramClient, new AllowedChats(""));
    }

    public HomeTelegramBot(String botToken, String botUsername, MessageService messageService,
                           MemberRegistrationService memberRegistrationService, CommandParser commandParser,
                           CommandRouter commandRouter, ExpenseHandler expenseHandler,
                           TelegramClient telegramClient, AllowedChats allowedChats) {
        this(botToken, botUsername, messageService, memberRegistrationService, commandParser, commandRouter,
                telegramClient, allowedChats, null, null, null, null);
    }

    public HomeTelegramBot(String botToken, String botUsername, MessageService messageService,
                           MemberRegistrationService memberRegistrationService, CommandParser commandParser,
                           CommandRouter commandRouter, TelegramClient telegramClient, AllowedChats allowedChats,
                           CallbackRouter callbacks, ProcessedUpdates updates, InteractionReplyRouter replies, MenuViews menus) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.commandParser = commandParser;
        this.commandRouter = commandRouter;
        this.allowedChats = allowedChats;
        this.telegramClient = telegramClient;
        this.output = new TelegramOutput(telegramClient);
        this.callbacks = callbacks;
        this.updates = updates;
        this.replies = replies;
        this.menus = menus;
    }

    TelegramOutput output() {
        return output;
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
            log.error("Failed to register bot commands menu: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            if (callbacks != null) {
                callbacks.route(botUsername, update, output);
            }
            return;
        }
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        Chat chat = message.getChat();
        if (chat == null) {
            return;
        }
        String text = message.hasText() ? message.getText().trim() : "";
        Long chatId = message.getChatId();
        if (!allowedChats.allows(chatId)) {
            log.debug("Ignoring update from chat {}", chatId);
            return;
        }
        String chatType = chat.getType();

        boolean isGroup = chat.isGroupChat() || chat.isSuperGroupChat()
                || "group".equalsIgnoreCase(chatType) || "supergroup".equalsIgnoreCase(chatType);

        User from = message.getFrom();
        // Telegram can send group posts on behalf of a chat or another bot. Neither is a roommate.
        if (isGroup && (from == null || from.getId() == null || from.getId() <= 0
                || Boolean.TRUE.equals(from.getIsBot()) || message.getSenderChat() != null)) {
            return;
        }
        Member member = isGroup && message.hasText()
                ? memberRegistrationService.registerMember(chatId, chat.getTitle(), from) : null;

        Sender sender = from != null
                ? new Sender(from.getId(), buildSenderName(from), from.getUserName())
                : new Sender(0L, "Anonymous", null);

        Optional<CommandParser.ParsedCommand> parsed = commandParser.parse(text, botUsername);
        if (parsed.isPresent()) {
            if (updates != null && !updates.claim(botUsername, update.getUpdateId(), null)) {
                return;
            }
            CommandParser.ParsedCommand cmd = parsed.get();
            CommandContext routedCtx = new CommandContext(
                    chatId,
                    chatType,
                    sender,
                    cmd.command(),
                    cmd.args(),
                    message.getEntities(),
                    replyText -> {
                        if (member != null && menus != null) {
                            menus.withMainButton(member, chatId, replyText, output);
                        } else {
                            output.send(chatId, replyText, null);
                        }
                    },
                    text,
                    menus == null ? null : new CommandContext.CommandSource(botUsername, update.getUpdateId(), member, output)
            );
            commandRouter.route(routedCtx);
        } else if (isGroup && replies != null && !text.startsWith("/")) {
            replies.route(message, output);
        }
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
        return messageService.getMessage("member.defaultName");
    }


}
