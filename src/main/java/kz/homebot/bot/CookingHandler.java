package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.CookingScheduleService;
import kz.homebot.service.MessageService;
import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class CookingHandler implements CommandHandler {
    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final CookingScheduleService schedules;
    private final MessageService messages;

    public CookingHandler(HouseholdRepository households, MemberRepository members,
                          CookingScheduleService schedules, MessageService messages) {
        this.households = households; this.members = members; this.schedules = schedules; this.messages = messages;
    }

    @Override public List<String> commands() {
        return List.of("kezek", "kezek_qoyu", "kezek_tazalau", "otkizu", "dayyn");
    }

    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        Household household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) return;
        LocalDate today = LocalDate.now(ALMATY);
        try {
            switch (ctx.command()) {
                case "kezek" -> showWeek(ctx, household, today);
                case "kezek_qoyu" -> {
                    schedules.setSchedule(household, ctx.args());
                    ctx.reply(messages.getMessage("cooking.saved"));
                }
                case "kezek_tazalau" -> {
                    schedules.clear(household);
                    ctx.reply(messages.getMessage("cooking.cleared"));
                }
                case "otkizu" -> {
                    if (ctx.args().isBlank()) { ctx.reply(messages.getMessage("cooking.usageSwap")); return; }
                    Member sender = members.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
                    Member target = schedules.handOver(household, today, sender, ctx.args());
                    ctx.reply(messages.getMessage("cooking.swapped", target.getDisplayName()));
                }
                case "dayyn" -> ctx.reply(messages.getMessage("cooking.ready", ctx.sender().displayName()));
                default -> { }
            }
        } catch (CookingScheduleService.ScheduleException e) {
            String names = members.findByHousehold(household).stream()
                    .map(Member::getDisplayName).collect(Collectors.joining(", "));
            String key = switch (e.issue()) {
                case INVALID -> "cooking.invalidSchedule";
                case UNKNOWN_MEMBER -> "cooking.unknownMember";
                case AMBIGUOUS_MEMBER -> "cooking.ambiguousMember";
                case NOT_COOKING -> "cooking.notCooking";
                case SAME_MEMBER -> "cooking.sameMember";
            };
            ctx.reply(messages.getMessage(key, names));
        }
    }

    private void showWeek(CommandContext ctx, Household household, LocalDate today) {
        var week = schedules.week(household, today);
        String todayNames = formatCooks(week.get(today.getDayOfWeek()));
        StringBuilder reply = new StringBuilder(messages.getMessage("cooking.today", todayNames))
                .append("\n").append(messages.getMessage("cooking.week"));
        for (DayOfWeek day : DayOfWeek.values()) {
            String marker = day == today.getDayOfWeek() ? messages.getMessage("cooking.todayMark") + " " : "";
            String label = messages.getMessage("weekday." + day.name().toLowerCase(Locale.ROOT));
            reply.append("\n").append(messages.getMessage("cooking.weekLine", marker, label,
                    formatCooks(week.get(day))));
        }
        ctx.reply(reply.toString());
    }

    private String formatCooks(List<Member> cooks) {
        return cooks.isEmpty() ? messages.getMessage("cooking.dayOff")
                : cooks.stream().map(Member::getDisplayName).collect(Collectors.joining(", "));
    }
}
