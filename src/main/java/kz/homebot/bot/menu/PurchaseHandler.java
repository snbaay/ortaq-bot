package kz.homebot.bot.menu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.homebot.bot.TelegramOutput;
import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.interaction.InteractionReplyRouter;
import kz.homebot.bot.interaction.InteractionSession;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.config.Limits;
import kz.homebot.domain.Member;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import kz.homebot.service.ProductCatalog;
import kz.homebot.service.PurchaseDraft;
import kz.homebot.service.PurchaseService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class PurchaseHandler implements CallbackRouter.Handler, InteractionReplyRouter.Handler,
        MainMenuHandler.Destination, CatalogMenu.Selection {
    private static final String KIND = "purchase";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Almaty"));

    private final SessionStore sessions;
    private final MenuViews views;
    private final CatalogMenu catalog;
    private final ProductCatalog products;
    private final PurchaseService purchases;
    private final MessageService messages;
    private final MoneyFormatter money;
    private final ObjectMapper json;

    public PurchaseHandler(SessionStore sessions, MenuViews views, CatalogMenu catalog, ProductCatalog products,
                           PurchaseService purchases, MessageService messages, MoneyFormatter money, ObjectMapper json) {
        this.sessions = sessions;
        this.views = views;
        this.catalog = catalog;
        this.products = products;
        this.purchases = purchases;
        this.messages = messages;
        this.money = money;
        this.json = json;
    }

    @Override
    public String kind() {
        return "p";
    }

    @Override
    public String replyKind() {
        return KIND;
    }

    @Override
    public boolean supports(String action) {
        return "basket".equals(action);
    }

    @Override
    public void open(String action, CallbackRouter.Call call) {
        InteractionSession session = sessions.withOwned(call.member(), call.message().getChatId(), KIND, "basket",
                write(PurchaseDraft.EMPTY), row -> sessions.advance(row, "basket", row.payload(), false));
        render(session, call.member(), call.output(), null);
    }

    @Override
    public void choose(String action, CallbackRouter.Call call) {
        boolean custom = "custom".equals(action);
        String key = custom ? null : action.substring(2);
        if (!custom && !products.contains(key)) {
            call.output().send(call.message().getChatId(), messages.getMessage("purchase.missing"), null);
            return;
        }
        var change = sessions.withOwned(call.member(), call.message().getChatId(), KIND, "basket", write(PurchaseDraft.EMPTY), row -> {
            var draft = read(row);
            if (draft.lines().size() >= PurchaseDraft.MAX_LINES) {
                return new Change(sessions.advance(row, "basket", write(draft), false), "purchase.lineLimit");
            }
            var input = draft.input(key, custom ? null : messages.getMessage("catalog.product." + key), null);
            return new Change(sessions.advance(row, custom ? "name" : "price", write(input), false), null);
        });
        render(change.session(), call.member(), call.output(), change.error());
    }

    @Override
    public void handle(CallbackRouter.Call call) {
        var result = sessions.withButton(call, KIND, (row, action) -> button(row, action, call.member()));
        if (result.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        call.answer().send(null);
        var change = result.get();
        var row = change.session();
        if ("catalog".equals(row.state())) {
            catalog.open("catalog", call);
        } else {
            render(row, call.member(), call.output(), change.error());
        }
    }

    private Change button(InteractionSession row, String action, Member buyer) {
        var draft = read(row);
        String state = row.state();
        if ("home".equals(action)) {
            return next(row, "home", draft);
        }
        if ("cancel".equals(action)) {
            return next(row, "cancel", draft);
        }
        if ("cancelYes".equals(action) && "cancel".equals(state)) {
            return new Change(sessions.advance(row, "cancelled", write(PurchaseDraft.EMPTY), true), null);
        }
        if ("back".equals(action) || "cancelNo".equals(action) && "cancel".equals(state)) {
            return next(row, "basket", draft.input(null, null, null));
        }
        if ("retry".equals(action) && List.of("name", "price", "editPrice").contains(state)) {
            return next(row, state, draft);
        }
        if ("basket".equals(state)) {
            return switch (action) {
                case "add" -> next(row, "catalog", draft);
                case "edit", "remove" -> next(row, action, draft);
                case "participants" -> draft.personal() ? null : next(row, action, draft.input(null, null, 0));
                case "sharing" -> next(row, "basket", draft.sharing(!draft.personal(), draft.participantIds()));
                case "confirm" -> draft.lines().isEmpty() ? new Change(
                        sessions.advance(row, "basket", write(draft), false), "purchase.empty") : null;
                default -> null;
            };
        }
        if ("participants".equals(state)) {
            if (action.matches("t:[0-9a-z]{1,12}")) {
                try {
                    return next(row, state, purchases.toggleParticipant(buyer, draft, Long.parseLong(action.substring(2), 36)));
                } catch (IllegalArgumentException denied) {
                    return new Change(sessions.advance(row, state, write(draft), false), "v2.denied");
                }
            }
            if ("all".equals(action)) {
                return next(row, state, draft.sharing(false, null));
            }
            if (action.matches("page:[0-9]{1,5}")) {
                int page = Integer.parseInt(action.substring(5));
                if (page * 8 < purchases.householdMembers(buyer).size()) {
                    return next(row, state, draft.input(null, null, page));
                }
            }
        }
        if (action.matches("i:[0-9]{1,2}") && List.of("edit", "remove").contains(state)) {
            int index = Integer.parseInt(action.substring(2));
            if (index >= draft.lines().size()) {
                return new Change(sessions.advance(row, "basket", write(draft), false), "purchase.missing");
            }
            var line = draft.lines().get(index);
            return next(row, "edit".equals(state) ? "editPrice" : "removeConfirm", draft.input(line.product(), line.label(), index));
        }
        if ("removeYes".equals(action) && "removeConfirm".equals(state)) {
            return next(row, "basket", draft.remove());
        }
        return null;
    }

    @Override
    public void reply(InteractionSession expected, Member member, Message message, TelegramOutput output) {
        var change = sessions.withReply(expected, row -> reply(row, message.getText()));
        if (change.isEmpty()) {
            output.send(message.getChatId(), messages.getMessage("v2.promptExpired"), null);
        } else if (change.get().error() != null) {
            output.send(message.getChatId(), messages.getMessage(change.get().error()), null);
        } else {
            render(change.get().session(), member, output, null);
        }
    }

    private Change reply(InteractionSession row, String text) {
        var draft = read(row);
        if ("name".equals(row.state())) {
            String name = text == null ? "" : text.strip();
            if (name.isEmpty() || name.codePointCount(0, name.length()) > 60 || name.chars().anyMatch(Character::isISOControl)) {
                return new Change(row, "purchase.badName");
            }
            return next(row, "price", draft.input(null, name, null));
        }
        if (!List.of("price", "editPrice").contains(row.state())) {
            return null;
        }
        var price = PurchaseDraft.parsePrice(text);
        if (price == null) {
            return new Change(row, "v2.money");
        }
        var changed = draft.price(price);
        if (changed.total().compareTo(Limits.MAX_AMOUNT) > 0) {
            return new Change(row, "purchase.totalLimit");
        }
        return next(row, "basket", changed);
    }

    private Change next(InteractionSession previous, String state, PurchaseDraft draft) {
        return new Change(sessions.advance(previous, state, write(draft), false), null);
    }

    private void render(InteractionSession row, Member member, TelegramOutput output, String error) {
        if (List.of("home", "cancelled").contains(row.state())) {
            views.main(member, row.chatId(), messages.getMessage("menu.title"), output);
            return;
        }
        var draft = read(row);
        var buttons = new ArrayList<MenuViews.Button>();
        String text;
        String prompt = null;
        switch (row.state()) {
            case "name", "price", "editPrice" -> {
                text = messages.getMessage("menu.basket");
                prompt = "name".equals(row.state()) ? messages.getMessage("purchase.enterName")
                        : messages.getMessage("purchase.enterPrice", capitalize(draft.label()));
                buttons.add(new MenuViews.Button("v2.retry", "retry"));
            }
            case "participants" -> {
                text = messages.getMessage("purchase.participants");
                var selected = purchases.participants(member, draft).stream().map(Member::getId).toList();
                var all = purchases.householdMembers(member);
                int page = draft.index();
                int end = Math.min(all.size(), (page + 1) * 8);
                for (int i = page * 8; i < end; i++) {
                    Member candidate = all.get(i);
                    String key = selected.contains(candidate.getId()) ? "purchase.selected" : "menu.value";
                    buttons.add(new MenuViews.Button(key, "t:" + Long.toString(candidate.getId(), 36), candidate.getDisplayName()));
                }
                buttons.add(new MenuViews.Button("purchase.all", "all"));
                if (page > 0) {
                    buttons.add(new MenuViews.Button("catalog.previous", "page:" + (page - 1)));
                }
                if (end < all.size()) {
                    buttons.add(new MenuViews.Button("catalog.next", "page:" + (page + 1)));
                }
            }
            case "edit", "remove" -> {
                text = messages.getMessage("purchase.selectLine");
                for (int index = 0; index < draft.lines().size(); index++) {
                    buttons.add(new MenuViews.Button("menu.value", "i:" + index, draft.lines().get(index).label()));
                }
            }
            case "removeConfirm" -> {
                text = messages.getMessage("purchase.removeConfirm", draft.label());
                buttons.add(new MenuViews.Button("menu.confirm", "removeYes"));
            }
            case "cancel" -> {
                text = messages.getMessage("purchase.cancelConfirm");
                buttons.add(new MenuViews.Button("purchase.cancelYes", "cancelYes"));
                buttons.add(new MenuViews.Button("purchase.cancelNo", "cancelNo"));
            }
            default -> {
                text = summary(row, draft, member);
                buttons.add(new MenuViews.Button("purchase.add", "add"));
                if (!draft.lines().isEmpty()) {
                    buttons.add(new MenuViews.Button("purchase.edit", "edit"));
                    buttons.add(new MenuViews.Button("purchase.remove", "remove"));
                }
                if (!draft.personal()) {
                    buttons.add(new MenuViews.Button("purchase.participants", "participants"));
                }
                buttons.add(new MenuViews.Button("purchase.sharing", "sharing"));
                buttons.add(new MenuViews.Button("menu.confirm", "confirm"));
            }
        }
        if (!List.of("basket", "cancel").contains(row.state())) {
            buttons.add(new MenuViews.Button("menu.back", "back"));
        }
        if (!"cancel".equals(row.state())) {
            buttons.add(new MenuViews.Button("menu.cancel", "cancel"));
        }
        buttons.add(new MenuViews.Button("menu.home", "home"));
        views.send(row, kind(), error == null ? text : messages.getMessage(error) + "\n\n" + text, buttons, output);
        if (prompt != null) {
            var sent = output.send(row.chatId(), prompt, ForceReplyKeyboard.builder().forceReply(true).build());
            if (sent != null && sent.getMessageId() != null) {
                sessions.bindPrompt(row, sent.getMessageId());
            }
        }
    }

    private String summary(InteractionSession row, PurchaseDraft draft, Member member) {
        var text = new StringBuilder(messages.getMessage("menu.basket"));
        for (var line : draft.lines()) {
            text.append("\n").append(line.label()).append(" — ").append(money.format(line.price()));
        }
        if (draft.lines().isEmpty()) {
            text.append("\n").append(messages.getMessage("purchase.empty"));
        }
        return text.append("\n").append(messages.getMessage("purchase.total", money.format(draft.total())))
                .append("\n").append(messages.getMessage("purchase.buyer", member.getDisplayName(), TIME.format(row.expiresAt().minusSeconds(1800))))
                .append("\n").append(messages.getMessage(draft.personal() ? "purchase.personal" : "purchase.shared"))
                .append(draft.personal() ? "" : "\n" + messages.getMessage("purchase.members",
                        String.join(", ", purchases.participants(member, draft).stream().map(Member::getDisplayName).toList())))
                .toString();
    }

    private String capitalize(String label) {
        return label.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + label.substring(1);
    }

    private PurchaseDraft read(InteractionSession row) {
        try {
            return json.readValue(row.payload(), PurchaseDraft.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid purchase draft", failure);
        }
    }

    private String write(PurchaseDraft draft) {
        try {
            return json.writeValueAsString(Objects.requireNonNull(draft));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not store purchase draft", failure);
        }
    }

    private record Change(InteractionSession session, String error) {}
}
