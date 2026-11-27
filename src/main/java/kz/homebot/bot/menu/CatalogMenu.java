package kz.homebot.bot.menu;

import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.interaction.InteractionSession;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.service.MessageService;
import kz.homebot.service.ProductCatalog;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CatalogMenu implements CallbackRouter.Handler, MainMenuHandler.Destination {
    private static final String KIND = "catalog";
    private final ProductCatalog catalog;
    private final MainMenuHandler mainMenu;
    private final SessionStore sessions;
    private final MenuViews views;
    private final MessageService messages;
    private final ObjectProvider<Selection> selection;

    public CatalogMenu(ProductCatalog catalog, SessionStore sessions, MenuViews views, MessageService messages,
                       ObjectProvider<Selection> selection, MainMenuHandler mainMenu) {
        this.catalog = catalog;
        this.sessions = sessions;
        this.views = views;
        this.messages = messages;
        this.selection = selection;
        this.mainMenu = mainMenu;
    }

    @Override
    public String kind() {
        return "c";
    }

    @Override
    public boolean supports(String action) {
        return KIND.equals(action) || "recent".equals(action);
    }

    @Override
    public void open(String action, CallbackRouter.Call call) {
        show(sessions.navigate(call.member(), call.message().getChatId(), KIND, action, "{}"), call);
    }

    @Override
    public void handle(CallbackRouter.Call call) {
        var next = sessions.withButton(call, KIND, (session, action) -> {
            if ("home".equals(action) || "back".equals(action) || "cancel".equals(action)) {
                String nextState = "back".equals(action) ? (KIND.equals(session.state()) ? "products" : KIND) : "home";
                return sessions.advance(session, nextState, "{}", !KIND.equals(nextState));
            }
            if ("recent".equals(action) || categoryPage(action) != null) {
                return sessions.advance(session, action, "{}", false);
            }
            if ("custom".equals(action) && KIND.equals(session.state())
                    || action.startsWith("p:") && offered(session, action.substring(2), call)) {
                return sessions.advance(session, "selected", action, true);
            }
            return null;
        });
        if (next.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        call.answer().send(null);
        var session = next.get();
        switch (session.state()) {
            case "products" -> mainMenu.submenu(call, "products", messages.getMessage("v2.empty"));
            case "home" -> views.main(call.member(), session.chatId(), messages.getMessage("menu.title"), call.output());
            case "selected" -> {
                var target = selection.getIfAvailable();
                if (target != null) {
                    target.choose(session.payload(), call);
                } else {
                    open(KIND, call);
                }
            }
            default -> show(session, call);
        }
    }

    private void show(InteractionSession session, CallbackRouter.Call call) {
        List<MenuViews.Button> buttons = new ArrayList<>();
        String title;
        if (KIND.equals(session.state())) {
            title = messages.getMessage("menu.catalog");
            buttons.add(new MenuViews.Button("menu.recent", "recent"));
            catalog.categories().forEach(category -> buttons.add(new MenuViews.Button(
                    "catalog.category." + category.key(), "v:" + category.key() + ":0")));
            buttons.add(new MenuViews.Button("catalog.custom", "custom"));
        } else if ("recent".equals(session.state())) {
            var recent = recent(call);
            title = messages.getMessage("menu.recent") + (recent.isEmpty() ? "\n" + messages.getMessage("v2.empty") : "");
            recent.forEach(product -> buttons.add(new MenuViews.Button(product.labelKey(), "p:" + product.key(), product.label())));
        } else {
            var page = categoryPage(session.state());
            title = messages.getMessage("catalog.category." + page.category().key());
            page.category().page(page.number()).forEach(key -> buttons.add(new MenuViews.Button("catalog.product." + key, "p:" + key)));
            if (page.number() > 0) {
                buttons.add(new MenuViews.Button("catalog.previous", "v:" + page.category().key() + ":" + (page.number() - 1)));
            }
            if (page.number() + 1 < page.category().pageCount()) {
                buttons.add(new MenuViews.Button("catalog.next", "v:" + page.category().key() + ":" + (page.number() + 1)));
            }
        }
        buttons.add(new MenuViews.Button("menu.back", "back"));
        buttons.add(new MenuViews.Button("menu.home", "home"));
        views.send(session, kind(), title, buttons, call.output());
    }

    private boolean offered(InteractionSession session, String key, CallbackRouter.Call call) {
        if ("recent".equals(session.state())) {
            return recent(call).stream().anyMatch(product -> product.key().equals(key));
        }
        var page = categoryPage(session.state());
        return page != null && page.category().page(page.number()).contains(key);
    }

    private List<RecentProduct> recent(CallbackRouter.Call call) {
        var target = selection.getIfAvailable();
        return target == null ? List.of() : target.recent(call);
    }

    private Page categoryPage(String action) {
        if (!action.matches("v:[a-z]+:[0-9]{1,2}")) {
            return null;
        }
        String[] parts = action.split(":");
        return catalog.category(parts[1])
                .map(category -> new Page(category, Integer.parseInt(parts[2])))
                .filter(page -> page.number() < page.category().pageCount()).orElse(null);
    }

    private record Page(ProductCatalog.Category category, int number) {}

    public record RecentProduct(String key, String labelKey, String label) {}

    public interface Selection {
        void choose(String action, CallbackRouter.Call call);
        default List<RecentProduct> recent(CallbackRouter.Call call) {
            return List.of();
        }
    }
}
