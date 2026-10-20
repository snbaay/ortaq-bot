package kz.homebot.bot;

import kz.homebot.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CommandRouter {

    private static final Logger log = LoggerFactory.getLogger(CommandRouter.class);

    private final Map<String, CommandHandler> handlerMap = new HashMap<>();
    private final List<CommandHandler> handlers;
    private final MessageService messageService;

    public CommandRouter(List<CommandHandler> handlers, MessageService messageService) {
        this.handlers = handlers;
        this.messageService = messageService;
        for (CommandHandler handler : handlers) {
            for (String cmd : handler.commands()) {
                handlerMap.put(cmd.toLowerCase(), handler);
            }
        }
    }

    public List<CommandHandler> getHandlers() {
        return handlers;
    }

    public boolean route(CommandContext ctx) {
        String cmd = ctx.command().toLowerCase();
        CommandHandler handler = handlerMap.get(cmd);
        if (handler == null) {
            return false;
        }

        try {
            handler.handle(ctx);
        } catch (Exception e) {
            log.error("Error executing command /{} in chat {}: {}", cmd, ctx.chatId(), e.getMessage(), e);
            ctx.reply(messageService.getMessage("error.generic"));
        }
        return true;
    }
}
