package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.BalanceService;
import kz.homebot.service.DebtCalculator;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BalanceHandler implements CommandHandler {

    private final BalanceService balanceService;
    private final DebtCalculator debtCalculator;
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final MessageService messageService;
    private final MoneyFormatter moneyFormatter;

    public BalanceHandler(BalanceService balanceService,
                          DebtCalculator debtCalculator,
                          HouseholdRepository householdRepository,
                          MemberRepository memberRepository,
                          MessageService messageService,
                          MoneyFormatter moneyFormatter) {
        this.balanceService = balanceService;
        this.debtCalculator = debtCalculator;
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.messageService = messageService;
        this.moneyFormatter = moneyFormatter;
    }

    @Override
    public List<String> commands() {
        return List.of("balans", "qaryz");
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) {
            ctx.reply(messageService.getMessage("error.groupOnly"));
            return;
        }

        Household household = householdRepository.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) {
            ctx.reply(messageService.getMessage("balans.empty"));
            return;
        }

        if ("balans".equalsIgnoreCase(ctx.command())) {
            handleBalans(ctx, household);
        } else if ("qaryz".equalsIgnoreCase(ctx.command())) {
            handleQaryz(ctx, household);
        }
    }

    private void handleBalans(CommandContext ctx, Household household) {
        List<BalanceService.MemberBalance> balances = balanceService.getMemberBalances(household);
        if (balances.isEmpty()) {
            ctx.reply(messageService.getMessage("balans.empty"));
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(messageService.getMessage("balans.header")).append("\n");
        for (BalanceService.MemberBalance mb : balances) {
            String sign = mb.balance().compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            sb.append(mb.member().getDisplayName())
              .append(": ")
              .append(sign)
              .append(moneyFormatter.format(mb.balance()))
              .append("\n");
        }
        ctx.reply(sb.toString().trim());
    }

    private void handleQaryz(CommandContext ctx, Household household) {
        ctx.reply(buildQaryzText(household));
    }

    public String buildQaryzText(Household household) {
        Map<Long, BigDecimal> balances = balanceService.getBalances(household);
        List<DebtCalculator.Transfer> transfers = debtCalculator.calculate(balances);

        if (transfers.isEmpty()) {
            return messageService.getMessage("debts.allEven");
        }

        Map<Long, Member> memberMap = memberRepository.findByHousehold(household).stream()
                .collect(Collectors.toMap(Member::getId, m -> m));

        StringBuilder sb = new StringBuilder();
        sb.append(messageService.getMessage("qaryz.header")).append("\n");
        for (DebtCalculator.Transfer t : transfers) {
            Member from = memberMap.get(t.fromMemberId());
            Member to = memberMap.get(t.toMemberId());
            String fromName = from != null ? from.getDisplayName() : "ID " + t.fromMemberId();
            String toName = to != null ? to.getDisplayName() : "ID " + t.toMemberId();
            sb.append(fromName)
              .append(" → ")
              .append(toName)
              .append(": ")
              .append(moneyFormatter.format(t.amount()))
              .append("\n");
        }
        return sb.toString().trim();
    }
}
