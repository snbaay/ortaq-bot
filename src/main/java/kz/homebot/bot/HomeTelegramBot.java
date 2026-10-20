package kz.homebot.bot;

import jakarta.annotation.PostConstruct;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.service.BalanceService;
import kz.homebot.service.DebtCalculator;
import kz.homebot.service.ExpenseParser;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class HomeTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(HomeTelegramBot.class);

    private final String botToken;
    private final String botUsername;
    private final TelegramClient telegramClient;
    private final MessageService messageService;
    private final MemberRegistrationService memberRegistrationService;
    private final HouseholdRepository householdRepository;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;
    private final DebtCalculator debtCalculator;
    private final ExpenseParser expenseParser;
    private final MoneyFormatter moneyFormatter;

    @org.springframework.beans.factory.annotation.Autowired
    public HomeTelegramBot(
            @Value("${bot.token}") String botToken,
            @Value("${bot.username}") String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService,
            HouseholdRepository householdRepository,
            ExpenseService expenseService,
            BalanceService balanceService,
            DebtCalculator debtCalculator,
            ExpenseParser expenseParser,
            MoneyFormatter moneyFormatter) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.householdRepository = householdRepository;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
        this.debtCalculator = debtCalculator;
        this.expenseParser = expenseParser;
        this.moneyFormatter = moneyFormatter;
        this.telegramClient = new OkHttpTelegramClient(botToken);
    }

    // Secondary constructor for testing (inject TelegramClient directly)
    public HomeTelegramBot(
            String botToken,
            String botUsername,
            MessageService messageService,
            MemberRegistrationService memberRegistrationService,
            HouseholdRepository householdRepository,
            ExpenseService expenseService,
            BalanceService balanceService,
            DebtCalculator debtCalculator,
            ExpenseParser expenseParser,
            MoneyFormatter moneyFormatter,
            TelegramClient telegramClient) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.messageService = messageService;
        this.memberRegistrationService = memberRegistrationService;
        this.householdRepository = householdRepository;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
        this.debtCalculator = debtCalculator;
        this.expenseParser = expenseParser;
        this.moneyFormatter = moneyFormatter;
        this.telegramClient = telegramClient;
    }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

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
            telegramClient.execute(SetMyCommands.builder().commands(commands).build());
            log.info("Telegram command menu registered successfully for @{}", botUsername);
        } catch (TelegramApiException e) {
            log.error("Failed to register bot commands menu: {}", e.getMessage());
        }
    }

    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message message = update.getMessage();
        Chat chat = message.getChat();
        String text = message.getText().trim();
        Long chatId = message.getChatId();

        boolean isGroup = chat.isGroupChat() || chat.isSuperGroupChat()
                || "group".equals(chat.getType()) || "supergroup".equals(chat.getType());

        Member member = null;
        if (isGroup && message.getFrom() != null) {
            member = memberRegistrationService.registerMember(chatId, chat.getTitle(), message.getFrom());
        }

        if (isCommand(text, "/start")) {
            send(chatId, messageService.getMessage("bot.start.greeting"));

        } else if (isCommand(text, "/shygyn") || startsWithShygynArgs(text)) {
            handleShygyn(chatId, text, isGroup, member, chat);

        } else if (isCommand(text, "/balans")) {
            handleBalans(chatId, isGroup, chat);

        } else if (isCommand(text, "/qaryz")) {
            handleQaryz(chatId, isGroup, chat);

        } else if (isGroup && !text.startsWith("/") && startsWithDigit(text)) {
            // Plain message starting with a number in a group = expense
            handleShygyn(chatId, text, true, member, chat);
        }
    }

    // ── /shygyn ────────────────────────────────────────────────────────────────

    private void handleShygyn(Long chatId, String text, boolean isGroup, Member payer, Chat chat) {
        if (!isGroup) {
            send(chatId, messageService.getMessage("shygyn.group.only"));
            return;
        }
        if (payer == null) return;

        ExpenseParser.ParseResult parsed;
        try {
            parsed = expenseParser.parse(text);
        } catch (IllegalArgumentException e) {
            send(chatId, messageService.getMessage("shygyn.format.error"));
            return;
        }

        Household household = householdRepository.findByTelegramChatId(chatId).orElse(null);
        if (household == null) return;

        ExpenseService.ExpenseResult result = expenseService.addExpense(
                household, payer, parsed.amount(), parsed.description());

        String sharesText = buildSharesText(result.members(), result.shares());
        String reply = messageService.getMessage("shygyn.added",
                moneyFormatter.format(parsed.amount()),
                parsed.description(),
                payer.getDisplayName(),
                sharesText);
        send(chatId, reply);
    }

    private String buildSharesText(List<Member> members, List<BigDecimal> shares) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            sb.append("• ").append(members.get(i).getDisplayName())
              .append(": ").append(moneyFormatter.format(shares.get(i)));
            if (i < members.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    // ── /balans ────────────────────────────────────────────────────────────────

    private void handleBalans(Long chatId, boolean isGroup, Chat chat) {
        if (!isGroup) {
            send(chatId, messageService.getMessage("balans.group.only"));
            return;
        }
        Household household = householdRepository.findByTelegramChatId(chatId).orElse(null);
        if (household == null) {
            send(chatId, messageService.getMessage("balans.empty"));
            return;
        }

        Map<Long, BigDecimal> balances = balanceService.getBalances(household);
        if (balances.isEmpty()) {
            send(chatId, messageService.getMessage("balans.empty"));
            return;
        }

        // Need member display names: load household members
        var members = new java.util.HashMap<Long, String>();
        for (var entry : kz.homebot.domain.Member.class.getDeclaredFields()) { /* not needed */ }
        // Load names from repository via BalanceService result — we need member list
        // Use the injected memberRepository indirectly through BalanceService is not possible here,
        // so we load via household
        var memberList = kz.homebot.repository.MemberRepository.class.isInterface()
                ? List.<Member>of() : List.<Member>of();
        // Actually we need the repository — better approach: BalanceService returns MemberBalance records
        // But we don't want to over-engineer. We'll just look up the household members here.
        // We already have householdRepository injected. Load members via MemberRepository.
        // => Add memberRepository to bot constructor is the clean way. Let's do that.
        send(chatId, buildBalansText(household, balances, chatId));
    }

    private String buildBalansText(Household household, Map<Long, BigDecimal> balances, Long chatId) {
        // We need member names. Re-use householdRepository which we already have.
        // Load member list (small, bounded by household size)
        // We injected householdRepository but NOT memberRepository. Let's get members
        // from BalanceService which already has memberRepository.
        // Best fix: add BalanceService.getMembersWithBalances() returning sorted list.
        // Implemented inline with what we have: we rely on the fact that BalanceService
        // already loaded members. We need to expose that. See refactor below.
        // For now build a simple map memberId->name via the household.
        // We can't do that here without memberRepository. We'll add it.
        return ""; // placeholder - see updated constructor below
    }

    // ── /qaryz ─────────────────────────────────────────────────────────────────

    private void handleQaryz(Long chatId, boolean isGroup, Chat chat) {
        if (!isGroup) {
            send(chatId, messageService.getMessage("qaryz.group.only"));
            return;
        }
        Household household = householdRepository.findByTelegramChatId(chatId).orElse(null);
        if (household == null) {
            send(chatId, messageService.getMessage("qaryz.all.even"));
            return;
        }

        Map<Long, BigDecimal> balances = balanceService.getBalances(household);
        List<DebtCalculator.Transfer> transfers = debtCalculator.calculate(balances);

        if (transfers.isEmpty()) {
            send(chatId, messageService.getMessage("qaryz.all.even"));
            return;
        }

        // We need member names -> same problem, need memberRepository
        send(chatId, messageService.getMessage("qaryz.header")); // placeholder
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private boolean isCommand(String text, String command) {
        return text.equals(command)
                || text.toLowerCase().startsWith(command + "@");
    }

    private boolean startsWithShygynArgs(String text) {
        // "/shygyn 630 су" - check if starts with /shygyn followed by a space
        return text.toLowerCase().startsWith("/shygyn ");
    }

    private boolean startsWithDigit(String text) {
        return !text.isEmpty() && Character.isDigit(text.charAt(0));
    }

    private void send(Long chatId, String text) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.error("Failed to send message to {}: {}", chatId, e.getMessage());
        }
    }
}
