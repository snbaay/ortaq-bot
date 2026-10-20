package kz.homebot.bot;

import kz.homebot.service.MessageService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StartHelpHandler implements CommandHandler {

    private final MessageService messageService;

    public StartHelpHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public List<String> commands() {
        return List.of("start", "komek");
    }

    @Override
    public void handle(CommandContext ctx) {
        if ("start".equalsIgnoreCase(ctx.command())) {
            ctx.reply(messageService.getMessage("bot.start.greeting"));
        } else if ("komek".equalsIgnoreCase(ctx.command())) {
            ctx.reply(messageService.getMessage("bot.komek.text"));
        }
    }
}
