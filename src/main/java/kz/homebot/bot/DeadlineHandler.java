package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.DeadlineService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import kz.homebot.service.PaymentCountdown;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class DeadlineHandler implements CommandHandler {
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final DeadlineService deadlines;
    private final PaymentCountdown countdown;
    private final MessageService messages;
    private final MoneyFormatter money;
    public DeadlineHandler(HouseholdRepository households, MemberRepository members, DeadlineService deadlines,
                           PaymentCountdown countdown, MessageService messages, MoneyFormatter money) {
        this.households = households; this.members = members; this.deadlines = deadlines;
        this.countdown = countdown; this.messages = messages; this.money = money;
    }
    @Override public List<String> commands() { return List.of("merzim", "merzim_qosu", "merzim_oshiru"); }
    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        var household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) return;
        switch (ctx.command()) {
            case "merzim_qosu" -> {
                var creator = members.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
                if (creator == null) return;
                String[] parts = ctx.args().trim().split("\\s+", 3);
                if (parts.length < 3) { ctx.reply(messages.getMessage("deadline.usage")); return; }
                try {
                    int day = Integer.parseInt(parts[0]);
                    BigDecimal amount = "-".equals(parts[1]) ? null
                            : new BigDecimal(parts[1].replace(",", "."));
                    var saved = deadlines.add(household, creator, day, amount, parts[2]);
                    ctx.reply(messages.getMessage("deadline.added", saved.getId()));
                } catch (IllegalArgumentException e) {
                    ctx.reply(messages.getMessage("deadline.invalid"));
                }
            }
            case "merzim_oshiru" -> {
                try {
                    long id = Long.parseLong(ctx.args().trim());
                    ctx.reply(messages.getMessage(deadlines.delete(household, id)
                            ? "deadline.deleted" : "deadline.notFound"));
                } catch (NumberFormatException e) { ctx.reply(messages.getMessage("deadline.usageDelete")); }
            }
            case "merzim" -> {
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Almaty"));
                var upcoming = deadlines.upcoming(household, today);
                if (upcoming.isEmpty()) { ctx.reply(messages.getMessage("deadline.empty")); return; }
                StringBuilder reply = new StringBuilder(messages.getMessage("deadline.header"));
                for (var deadline : upcoming) {
                    long days = countdown.daysLeft(today, deadline.getDayOfMonth());
                    String time = days == 0 ? messages.getMessage("deadline.today")
                            : days == 1 ? messages.getMessage("deadline.tomorrow")
                            : messages.getMessage("deadline.days", days);
                    reply.append("\n#").append(deadline.getId()).append(" ").append(deadline.getTitle());
                    if (deadline.getAmount() != null) reply.append(" — ").append(money.format(deadline.getAmount()));
                    reply.append(" — ").append(time);
                }
                ctx.reply(reply.toString());
            }
            default -> { }
        }
    }
}
