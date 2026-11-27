package kz.homebot.bot.menu;

import kz.homebot.bot.CommandContext;
import kz.homebot.bot.CommandRouter;
import kz.homebot.bot.Sender;
import kz.homebot.bot.TelegramOutput;
import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.service.MessageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MainMenuHandler implements CallbackRouter.Handler {
    private static final Map<String, List<String>> MENUS = Map.of(
            "cook", List.of("schedule", "ready", "cookHistory", "swap", "scheduleSettings"),
            "products", List.of("catalog", "basket", "shopping", "recent"),
            "finance", List.of("expense", "balance", "debts", "repay", "history", "report"),
            "calls", List.of("tea", "meal", "store", "play", "urgent", "guests"),
            "other", List.of("shopping", "chores", "notes", "deadlines", "random", "presence", "dinner"),
            "settings", List.of("notifications", "quiet", "evening", "night", "help"));
    private static final Map<String, String> COMMANDS = Map.of(
            "schedule", "kezek", "shopping", "tizim", "balance", "balans", "debts", "qaryz",
            "history", "tarih", "report", "esep", "chores", "ister", "deadlines", "merzim");

    private final SessionStore sessions;
    private final MenuViews views;
    private final MessageService messages;
    private final ObjectProvider<CommandRouter> commands;
    private final ObjectProvider<Destination> destinations;

    public MainMenuHandler(SessionStore sessions, MenuViews views, MessageService messages,
                           ObjectProvider<CommandRouter> commands, ObjectProvider<Destination> destinations) {
        this.sessions = sessions;
        this.views = views;
        this.messages = messages;
        this.commands = commands;
        this.destinations = destinations;
    }

    @Override
    public String kind() {
        return "m";
    }

    @Override
    public boolean privateAction(String data, long chat, TelegramOutput output) {
        if (!"help".equals(data)) {
            return false;
        }
        output.send(chat, messages.getMessage("bot.komek.text"), null);
        return true;
    }

    @Override
    public void handle(CallbackRouter.Call call) {
        var state = sessions.withButton(call, "menu", (session, action) -> {
            if (session.userId() == null) {
                boolean available = "main".equals(session.state()) && MENUS.containsKey(action)
                        || "shortcut".equals(session.state()) && "home".equals(action);
                return available ? new Navigation(action, session.state()) : null;
            }
            List<String> children = MENUS.getOrDefault(session.state(), List.of());
            if ("home".equals(action) || "back".equals(action) || "cancel".equals(action) || children.contains(action)) {
                sessions.advance(session, "closed", "{}", true);
                return new Navigation("back".equals(action) || "cancel".equals(action) ? "home" : action, session.state());
            }
            return null;
        });
        if (state.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        call.answer().send(null);
        String action = state.get().action();
        String parent = state.get().parent();
        long chat = call.message().getChatId();
        if ("home".equals(action)) {
            views.main(call.member(), chat, messages.getMessage("menu.title"), call.output());
        } else if (MENUS.containsKey(action)) {
            submenu(call, action, messages.getMessage("v2.empty"));
        } else {
            var handler = destinations.orderedStream().filter(route -> route.supports(action)).findFirst();
            if (handler.isPresent()) {
                handler.get().open(action, call);
                return;
            }
            String command = COMMANDS.get(action);
            if (command != null) {
                var member = call.member();
                commands.getObject().route(new CommandContext(chat, call.message().getChat().getType(),
                        new Sender(member.getTelegramUserId(), member.getDisplayName(), member.getTelegramUsername()),
                        command, "", List.of(), text -> submenu(call, parent, text)));
            } else {
                String key = switch (action) {
                    case "help" -> "bot.komek.text";
                    case "expense" -> "shygyn.format.error";
                    default -> "v2.empty";
                };
                submenu(call, parent, messages.getMessage(key));
            }
        }
    }

    void submenu(CallbackRouter.Call call, String name, String text) {
        var session = sessions.navigate(call.member(), call.message().getChatId(), "menu", name, "{}");
        List<MenuViews.Button> buttons = new ArrayList<>();
        MENUS.get(name).forEach(item -> buttons.add(new MenuViews.Button("menu." + item, item)));
        buttons.add(new MenuViews.Button("menu.back", "back"));
        buttons.add(new MenuViews.Button("menu.cancel", "cancel"));
        buttons.add(new MenuViews.Button("menu.home", "home"));
        views.send(session, kind(), messages.getMessage("menu." + name) + "\n\n" + text, buttons, call.output());
    }

    private record Navigation(String action, String parent) {}

    public interface Destination {
        boolean supports(String action);
        void open(String action, CallbackRouter.Call call);
    }
}
