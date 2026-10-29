package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class RepaymentHandler implements CommandHandler {

    private final ExpenseService expenseService;
    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;
    private final MessageService messageService;
    private final MoneyFormatter moneyFormatter;
    private final BalanceHandler balanceHandler;

    public RepaymentHandler(ExpenseService expenseService,
                            HouseholdRepository householdRepository,
                            MemberRepository memberRepository,
                            MessageService messageService,
                            MoneyFormatter moneyFormatter,
                            BalanceHandler balanceHandler) {
        this.expenseService = expenseService;
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
        this.messageService = messageService;
        this.moneyFormatter = moneyFormatter;
        this.balanceHandler = balanceHandler;
    }

    @Override
    public List<String> commands() {
        return List.of("tolem");
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

        Member senderMember = memberRepository.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
        if (senderMember == null) {
            return;
        }

        String args = ctx.args().trim();
        if (args.isEmpty()) {
            ctx.reply(messageService.getMessage("tolem.usage"));
            return;
        }

        List<Member> allMembers = memberRepository.findByHousehold(household);
        List<Member> otherMembers = allMembers.stream()
                .filter(m -> !m.getId().equals(senderMember.getId()))
                .toList();

        String[] tokens = args.split("\\s+");
        Member recipient = null;
        BigDecimal amount = null;

        if (allMembers.size() == 2 && tokens.length == 1) {
            recipient = otherMembers.isEmpty() ? null : otherMembers.get(0);
            amount = parseAmount(tokens[0]);
        } else if (tokens.length >= 2) {
            String amountToken = tokens[tokens.length - 1];
            amount = parseAmount(amountToken);

            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 0; i < tokens.length - 1; i++) {
                if (i > 0) nameBuilder.append(" ");
                nameBuilder.append(tokens[i]);
            }
            String nameQuery = nameBuilder.toString().toLowerCase(Locale.ROOT);

            if (senderMember.getDisplayName().toLowerCase(Locale.ROOT).startsWith(nameQuery)) {
                ctx.reply(messageService.getMessage("tolem.self"));
                return;
            }

            List<Member> matches = otherMembers.stream()
                    .filter(m -> m.getDisplayName().toLowerCase(Locale.ROOT).startsWith(nameQuery))
                    .toList();

            if (matches.isEmpty()) {
                String memberNames = allMembers.stream().map(Member::getDisplayName).collect(Collectors.joining(", "));
                ctx.reply(messageService.getMessage("tolem.notFound", memberNames));
                return;
            } else if (matches.size() > 1) {
                String memberNames = matches.stream().map(Member::getDisplayName).collect(Collectors.joining(", "));
                ctx.reply(messageService.getMessage("tolem.ambiguous", memberNames));
                return;
            } else {
                recipient = matches.get(0);
            }
        } else {
            String memberNames = allMembers.stream().map(Member::getDisplayName).collect(Collectors.joining(", "));
            ctx.reply(messageService.getMessage("tolem.notFound", memberNames));
            return;
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            ctx.reply(messageService.getMessage("tolem.usage"));
            return;
        }

        if (recipient == null || recipient.getId().equals(senderMember.getId())) {
            ctx.reply(messageService.getMessage("tolem.self"));
            return;
        }

        String desc = messageService.getMessage("expense.settlement.description");
        expenseService.recordRepayment(household, senderMember, recipient, amount, desc);

        String successMsg = messageService.getMessage("tolem.success",
                senderMember.getDisplayName(),
                recipient.getDisplayName(),
                moneyFormatter.format(amount));

        String qaryzMsg = balanceHandler.buildQaryzText(household);
        ctx.reply(successMsg + "\n\n" + qaryzMsg);
    }

    private BigDecimal parseAmount(String str) {
        if (str == null || str.isBlank()) return null;
        if (str.contains(".") && str.contains(",")) return null;
        String normalized = str.replace(" ", "").replace(",", ".");
        try {
            BigDecimal amt = new BigDecimal(normalized);
            if (amt.compareTo(BigDecimal.ZERO) <= 0 || amt.compareTo(new BigDecimal("100000000")) > 0) {
                return null;
            }
            return amt.setScale(2, RoundingMode.UNNECESSARY);
        } catch (Exception e) {
            return null;
        }
    }
}
