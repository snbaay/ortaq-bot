package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.CookingRotationService;
import kz.homebot.service.MessageService;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class CookingHandler implements CommandHandler {
    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final CookingRotationService rotation;
    private final MessageService messages;

    public CookingHandler(HouseholdRepository households, MemberRepository members,
                          CookingRotationService rotation, MessageService messages) {
        this.households = households; this.members = members; this.rotation = rotation; this.messages = messages;
    }

    @Override public List<String> commands() { return List.of("kezek", "otkizu", "dayyn"); }

    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        Household household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null || members.findByHousehold(household).size() < 2) {
            ctx.reply(messages.getMessage("cooking.needTwo")); return;
        }
        LocalDate today = LocalDate.now(ALMATY);
        switch (ctx.command()) {
            case "kezek" -> {
                var duty = rotation.getOrCreateDuty(household, today);
                var next = rotation.preview(household, duty.getMember().getId(), 3);
                StringBuilder reply = new StringBuilder(messages.getMessage("cooking.today", duty.getMember().getDisplayName()));
                for (int i = 0; i < next.size(); i++) {
                    reply.append("\n").append(messages.getMessage("cooking.next", i + 1, next.get(i).getDisplayName()));
                }
                ctx.reply(reply.toString());
            }
            case "otkizu" -> ctx.reply(messages.getMessage("cooking.skipped",
                    rotation.skip(household, today).getMember().getDisplayName()));
            case "dayyn" -> ctx.reply(messages.getMessage("cooking.ready", ctx.sender().displayName()));
            default -> { }
        }
    }
}
