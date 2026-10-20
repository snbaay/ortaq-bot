package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ExpenseParser;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class ExpenseHandler implements CommandHandler {

    private final ExpenseParser expenseParser;
    private final ExpenseService expenseService;
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final MessageService messageService;
    private final MoneyFormatter moneyFormatter;

    public ExpenseHandler(ExpenseParser expenseParser,
                          ExpenseService expenseService,
                          HouseholdRepository householdRepository,
                          MemberRepository memberRepository,
                          MessageService messageService,
                          MoneyFormatter moneyFormatter) {
        this.expenseParser = expenseParser;
        this.expenseService = expenseService;
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.messageService = messageService;
        this.moneyFormatter = moneyFormatter;
    }

    @Override
    public List<String> commands() {
        return List.of("shygyn");
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) {
            ctx.reply(messageService.getMessage("error.groupOnly"));
            return;
        }

        String rawText = ctx.args().isBlank() ? "" : ctx.args();
        handleExpenseInput(ctx, rawText);
    }

    public void handlePlainExpense(CommandContext ctx, String text) {
        if (!ctx.isGroup()) {
            ctx.reply(messageService.getMessage("error.groupOnly"));
            return;
        }
        handleExpenseInput(ctx, text);
    }

    private void handleExpenseInput(CommandContext ctx, String rawText) {
        if (rawText.isBlank()) {
            ctx.reply(messageService.getMessage("usage.shygyn"));
            return;
        }

        ExpenseParser.ParseResult parsed;
        try {
            parsed = expenseParser.parse(rawText);
        } catch (IllegalArgumentException e) {
            ctx.reply(messageService.getMessage("shygyn.format.error"));
            return;
        }

        Household household = householdRepository.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) {
            return;
        }

        Member payer = memberRepository.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
        if (payer == null) {
            return;
        }

        ExpenseService.ExpenseResult result = expenseService.addExpense(
                household, payer, parsed.amount(), parsed.description());

        String sharesText = buildSharesText(result.members(), result.shares());
        String reply = messageService.getMessage("shygyn.added",
                moneyFormatter.format(parsed.amount()),
                parsed.description(),
                payer.getDisplayName(),
                sharesText);
        ctx.reply(reply);
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
}
