package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.MessageService;
import kz.homebot.service.ShoppingService;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ShoppingHandler implements CommandHandler {
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final ShoppingService shopping;
    private final MessageService messages;
    public ShoppingHandler(HouseholdRepository households, MemberRepository members,
                           ShoppingService shopping, MessageService messages) {
        this.households = households; this.members = members; this.shopping = shopping; this.messages = messages;
    }
    @Override public List<String> commands() { return List.of("tizim", "satyp"); }
    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        var household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) return;
        var member = members.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
        if (member == null) return;
        if ("satyp".equals(ctx.command())) {
            try {
                Long id = Long.parseLong(ctx.args().trim());
                ctx.reply(messages.getMessage(shopping.buy(household, member, id)
                        ? "shopping.bought" : "shopping.notFound"));
            } catch (NumberFormatException e) { ctx.reply(messages.getMessage("shopping.usageBuy")); }
            return;
        }
        if (!ctx.args().isBlank()) {
            try { ctx.reply(messages.getMessage("shopping.added", shopping.add(household, member, ctx.args()).size())); }
            catch (IllegalArgumentException e) { ctx.reply(messages.getMessage("shopping.invalid")); }
            return;
        }
        var open = shopping.open(household);
        if (open.isEmpty()) { ctx.reply(messages.getMessage("shopping.empty")); return; }
        StringBuilder text = new StringBuilder(messages.getMessage("shopping.header"));
        for (var item : open) text.append("\n#").append(item.getId()).append(" ").append(item.getText());
        ctx.reply(text.toString());
    }
}
