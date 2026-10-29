package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ChoreAge;
import kz.homebot.service.ChoreService;
import kz.homebot.service.MessageService;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class ChoreHandler implements CommandHandler {
    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("kk"));
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final ChoreService chores;
    private final ChoreAge age;
    private final MessageService messages;
    public ChoreHandler(HouseholdRepository households, MemberRepository members,
                        ChoreService chores, ChoreAge age, MessageService messages) {
        this.households = households; this.members = members; this.chores = chores;
        this.age = age; this.messages = messages;
    }
    @Override public List<String> commands() { return List.of("is", "ister", "songy"); }
    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        var household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) return;
        switch (ctx.command()) {
            case "is" -> {
                var member = members.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
                if (member == null) return;
                try { chores.add(household, member, ctx.args()); ctx.reply(messages.getMessage("chore.added")); }
                catch (IllegalArgumentException e) { ctx.reply(messages.getMessage("chore.invalid")); }
            }
            case "ister" -> {
                var latest = chores.latest(household);
                if (latest.isEmpty()) { ctx.reply(messages.getMessage("chore.empty")); return; }
                StringBuilder reply = new StringBuilder(messages.getMessage("chore.header"));
                for (var log : latest) reply.append("\n").append(log.getCreatedAt().format(DATE))
                        .append(" — ").append(log.getMember().getDisplayName()).append(": ").append(log.getText());
                ctx.reply(reply.toString());
            }
            case "songy" -> {
                var last = chores.findLast(household, ctx.args());
                if (last.isEmpty()) { ctx.reply(messages.getMessage("chore.notFound")); return; }
                var log = last.get();
                long days = age.daysAgo(LocalDate.now(ALMATY), log.getCreatedAt().toLocalDate());
                ctx.reply(messages.getMessage("chore.last", log.getCreatedAt().format(DATE),
                        log.getMember().getDisplayName(), log.getText(), days));
            }
            default -> { }
        }
    }
}
