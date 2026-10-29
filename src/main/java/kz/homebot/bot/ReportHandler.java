package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import kz.homebot.service.MonthlyReportService;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Component
public class ReportHandler implements CommandHandler {
    private final HouseholdRepository households;
    private final MonthlyReportService reports;
    private final MessageService messages;
    private final MoneyFormatter money;
    public ReportHandler(HouseholdRepository households, MonthlyReportService reports,
                         MessageService messages, MoneyFormatter money) {
        this.households = households; this.reports = reports; this.messages = messages; this.money = money;
    }
    @Override public List<String> commands() { return List.of("esep"); }
    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        var household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) return;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Almaty"));
        int month = today.getMonthValue();
        if (!ctx.args().isBlank()) {
            try { month = Integer.parseInt(ctx.args().trim()); }
            catch (NumberFormatException e) { ctx.reply(messages.getMessage("report.usage")); return; }
        }
        if (month < 1 || month > 12) { ctx.reply(messages.getMessage("report.usage")); return; }
        var report = reports.report(household, YearMonth.of(today.getYear(), month));
        StringBuilder reply = new StringBuilder(messages.getMessage("report.header", month, today.getYear(), money.format(report.total())));
        for (var total : report.members()) reply.append("\n").append(messages.getMessage("report.member",
                total.member().getDisplayName(), money.format(total.paid()), money.format(total.consumed())));
        if (!report.top().isEmpty()) {
            reply.append("\n").append(messages.getMessage("report.top"));
            for (var expense : report.top()) reply.append("\n").append(expense.getDescription())
                    .append(" — ").append(money.format(expense.getAmount()));
        }
        ctx.reply(reply.toString());
    }
}
