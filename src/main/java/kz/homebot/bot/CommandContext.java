package kz.homebot.bot;

import org.telegram.telegrambots.meta.api.objects.MessageEntity;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public record CommandContext(
        Long chatId,
        String chatType,
        Sender sender,
        String command,
        String args,
        List<MessageEntity> entities,
        Consumer<String> replier
) {
    public CommandContext {
        if (entities == null) {
            entities = Collections.emptyList();
        }
        if (args == null) {
            args = "";
        }
    }

    public void reply(String text) {
        if (text == null || text.isBlank() || replier == null) {
            return;
        }
        if (text.length() > 4096) {
            text = text.substring(0, 4095) + "…";
        }
        replier.accept(text);
    }

    public boolean isGroup() {
        return "group".equalsIgnoreCase(chatType) || "supergroup".equalsIgnoreCase(chatType);
    }
}
