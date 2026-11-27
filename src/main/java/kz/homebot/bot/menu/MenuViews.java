package kz.homebot.bot.menu;

import kz.homebot.bot.TelegramOutput;
import kz.homebot.bot.interaction.InteractionSession;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.domain.Member;
import kz.homebot.service.MessageService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

@Component
public class MenuViews {
    private static final List<String> MAIN = List.of("cook", "products", "finance", "calls", "other", "settings");

    private final SessionStore sessions;
    private final MessageService messages;

    public MenuViews(SessionStore sessions, MessageService messages) {
        this.sessions = sessions;
        this.messages = messages;
    }

    public void main(Member member, long chat, String text, TelegramOutput output) {
        InteractionSession menu = sessions.shared(member, chat, "menu", "main");
        send(menu, "m", text, MAIN.stream().map(key -> new Button("menu." + key, key)).toList(), output);
    }

    public void withMainButton(Member member, long chat, String text, TelegramOutput output) {
        InteractionSession menu = sessions.shared(member, chat, "menu", "shortcut");
        send(menu, "m", text, List.of(new Button("menu.home", "home")), output);
    }

    public void privateHelp(long chat, String text, TelegramOutput output) {
        output.send(chat, text, keyboard(List.of(row(messages.getMessage("menu.help"), "v2:m:help"))));
    }

    public void send(InteractionSession session, String kind, String text, List<Button> buttons, TelegramOutput output) {
        List<InlineKeyboardRow> rows = buttons.stream()
                .map(button -> row(messages.getMessage(button.label(), button.args()),
                        "v2:" + kind + ":" + session.id() + ":" + Integer.toString(session.version(), 36) + ":" + button.action()))
                .toList();
        var sent = output.send(session.chatId(), text, rows.isEmpty() ? null : keyboard(rows));
        if (sent != null && sent.getMessageId() != null) {
            sessions.bindMenu(session, sent.getMessageId());
        }
    }

    private static InlineKeyboardRow row(String caption, String data) {
        return new InlineKeyboardRow(InlineKeyboardButton.builder().text(caption).callbackData(data).build());
    }

    private static InlineKeyboardMarkup keyboard(List<InlineKeyboardRow> rows) {
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public record Button(String label, String action, Object... args) {}
}
