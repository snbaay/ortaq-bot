package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.MessageService;
import kz.homebot.service.RandomPicker;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class RandomHandler implements CommandHandler {
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final RandomPicker picker;
    private final MessageService messages;
    public RandomHandler(HouseholdRepository households, MemberRepository members,
                         RandomPicker picker, MessageService messages) {
        this.households = households; this.members = members; this.picker = picker; this.messages = messages;
    }
    @Override public List<String> commands() { return List.of("kim", "tandau", "tiyn"); }
    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        switch (ctx.command()) {
            case "tiyn" -> ctx.reply(messages.getMessage(picker.coin() ? "random.heads" : "random.tails"));
            case "tandau" -> {
                List<String> options = Arrays.stream(ctx.args().split("[,|]"))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (options.size() < 2 || options.size() > 10) {
                    ctx.reply(messages.getMessage("random.usageChoose")); return;
                }
                ctx.reply(messages.getMessage("random.choice", picker.pick(options)));
            }
            case "kim" -> {
                var household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
                if (household == null) return;
                var all = members.findByHousehold(household);
                if (all.isEmpty()) { ctx.reply(messages.getMessage("random.noMembers")); return; }
                String name = picker.pick(all).getDisplayName();
                ctx.reply(ctx.args().isBlank() ? messages.getMessage("random.person", name)
                        : messages.getMessage("random.personWithQuestion", ctx.args().trim(), name));
            }
            default -> { }
        }
    }
}
