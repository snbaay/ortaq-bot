package kz.homebot.bot;

import kz.homebot.domain.Expense;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Component
public class HistoryHandler implements CommandHandler {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM");

    private final ExpenseService expenseService;
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final MessageService messageService;
    private final MoneyFormatter moneyFormatter;

    public HistoryHandler(ExpenseService expenseService,
                          HouseholdRepository householdRepository,
                          MemberRepository memberRepository,
                          MessageService messageService,
                          MoneyFormatter moneyFormatter) {
        this.expenseService = expenseService;
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.messageService = messageService;
        this.moneyFormatter = moneyFormatter;
    }

    @Override
    public List<String> commands() {
        return List.of("tarih", "oshiru");
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) {
            ctx.reply(messageService.getMessage("error.groupOnly"));
            return;
        }

        Household household = householdRepository.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) {
            return;
        }

        if ("tarih".equalsIgnoreCase(ctx.command())) {
            handleTarih(ctx, household);
        } else if ("oshiru".equalsIgnoreCase(ctx.command())) {
            handleOshiru(ctx, household);
        }
    }

    private void handleTarih(CommandContext ctx, Household household) {
        List<Expense> history = expenseService.getHistory(household);
        if (history.isEmpty()) {
            ctx.reply(messageService.getMessage("tarih.empty"));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(messageService.getMessage("tarih.header")).append("\n");
        for (Expense e : history) {
            String dateStr = e.getCreatedAt().format(DATE_FMT);
            String payerName = e.getPayer() != null ? e.getPayer().getDisplayName() : "?";
            sb.append("#").append(e.getId())
              .append(" ").append(dateStr)
              .append(" ").append(payerName)
              .append(": ").append(moneyFormatter.format(e.getAmount()))
              .append(" (").append(e.getDescription()).append(")\n");
        }
        ctx.reply(sb.toString().trim());
    }

    private void handleOshiru(CommandContext ctx, Household household) {
        Member senderMember = memberRepository.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
        if (senderMember == null) {
            ctx.reply(messageService.getMessage("oshiru.empty"));
            return;
        }

        String args = ctx.args().trim();
        if (args.isEmpty()) {
            Optional<Expense> deleted = expenseService.deleteLastEntry(household, senderMember);
            if (deleted.isPresent()) {
                ctx.reply(messageService.getMessage("oshiru.success"));
            } else {
                ctx.reply(messageService.getMessage("oshiru.empty"));
            }
        } else {
            try {
                Long id = Long.parseLong(args);
                ExpenseService.DeleteResult res = expenseService.deleteEntryById(household, senderMember, id);
                switch (res) {
                    case SUCCESS -> ctx.reply(messageService.getMessage("oshiru.success"));
                    case NOT_OWNER -> ctx.reply(messageService.getMessage("oshiru.notOwner"));
                    case NOT_FOUND -> ctx.reply(messageService.getMessage("oshiru.notFound"));
                }
            } catch (NumberFormatException e) {
                ctx.reply(messageService.getMessage("oshiru.notFound"));
            }
        }
    }
}
