package kz.homebot.bot;

import kz.homebot.service.MessageService;
import kz.homebot.bot.menu.MenuViews;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StartHelpHandler implements CommandHandler {

    private final MessageService messageService;

    private final MenuViews menus;

    public StartHelpHandler(MessageService messageService) {
        this(messageService, null);
    }

    @Autowired
    public StartHelpHandler(MessageService messageService, MenuViews menus) {
        this.messageService = messageService;
        this.menus = menus;
    }

    @Override
    public List<String> commands() {
        return List.of("start", "komek");
    }

    @Override
    public void handle(CommandContext ctx) {
        boolean start = "start".equalsIgnoreCase(ctx.command());
        String text = messageService.getMessage(start ? "bot.start.greeting" : "bot.komek.text");
        if (menus != null && ctx.source() != null && ctx.isGroup() && ctx.source().member() != null && start) {
            menus.main(ctx.source().member(), ctx.chatId(), text, ctx.source().output());
        } else if (menus != null && ctx.source() != null && !ctx.isGroup()) {
            menus.privateHelp(ctx.chatId(), text, ctx.source().output());
        } else {
            ctx.reply(text);
        }
    }
}
