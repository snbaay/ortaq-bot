package kz.homebot.bot;

import java.util.List;

public interface CommandHandler {
    List<String> commands();
    void handle(CommandContext ctx);
}
