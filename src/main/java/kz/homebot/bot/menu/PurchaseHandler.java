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
import java.util.Comparator;

@Component
public class PurchaseHandler implements CallbackRouter.Handler, InteractionReplyRouter.Handler,
        MainMenuHandler.Destination, CatalogMenu.Selection {
    private static final String KIND = "purchase";
    private static final String RECEIPTS = "receipts";
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
                write(PurchaseDraft.EMPTY), row -> row.version() == 0
                        ? sessions.advance(row, "basket", row.payload(), false) : resumeChoice(row));
        render(session, call.member(), call.output(), null);
    }

    @Override
    public void backToBasket(CallbackRouter.Call call) {
        var row = sessions.withOwned(call.member(), call.message().getChatId(), KIND, "basket", write(PurchaseDraft.EMPTY),
                current -> sessions.advance(current, "basket", current.payload(), false));
        render(row, call.member(), call.output(), null);
    }

    @Override
    public void choose(String action, CallbackRouter.Call call) {
        boolean custom = ProductCatalog.CUSTOM.equals(action);
        var recent = action.startsWith("p:r:") ? purchases.recent(call.member()).stream()
                .filter(item -> action.equals("p:r:" + Long.toString(item.id(), 36))).findFirst().orElse(null) : null;
        String key = recent != null ? recent.catalogKey() : custom ? null : action.substring(2);
        String selectedName = recent != null ? recent.label() : custom ? null : messages.getMessage("catalog.product." + key);
        if (!custom && recent == null && !products.contains(key)) {
            call.output().send(call.message().getChatId(), messages.getMessage("purchase.missing"), null);
            return;
        }
        var change = sessions.withOwned(call.member(), call.message().getChatId(), KIND, "basket", write(PurchaseDraft.EMPTY), row -> {
            var draft = read(row);
            if (List.of("name", "price", "editPrice", "photo").contains(row.state()) || row.state().startsWith("resume:")) {
                return new Change(resumeChoice(row), null);
            }
            if (draft.lines().size() >= PurchaseDraft.MAX_LINES) {
                return new Change(sessions.advance(row, "basket", write(draft), false), "purchase.lineLimit");
            }
            var input = draft.input(key, selectedName, null);
            return new Change(sessions.advance(row, custom ? "name" : "price", write(input), false), null);
        });
        render(change.session(), call.member(), call.output(), change.error());
    }

    @Override
    public List<CatalogMenu.RecentProduct> recent(CallbackRouter.Call call) {
        return purchases.recent(call.member()).stream().map(item ->
                new CatalogMenu.RecentProduct("r:" + Long.toString(item.id(), 36), "menu.value", item.label())).toList();
    }

    @Override
    public void handle(CallbackRouter.Call call) {
        String[] payload = call.data().split(":", 3);
        String action = payload.length == 3 ? payload[2] : "";
        if (action.startsWith("h:") || action.startsWith("r:") || List.of("showPhoto", "void", "voidYes").contains(action)) {
            receiptButton(call);
            return;
        }
        boolean confirm = "confirm".equals(action);
        java.util.Optional<Change> result;
        try {
            result = confirm ? sessions.withConfirmation(call, KIND, (row, key) -> confirm(row, call.member()))
                    : sessions.withButton(call, KIND, (row, key) -> button(row, key, call.member()));
        } catch (PurchaseService.ShoppingChanged changed) {
            call.answer().send(messages.getMessage("purchase.listChanged"));
            return;
        }
        if (result.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        call.answer().send(null);
        var change = result.get();
        var row = change.session();
        if (change.receiptId() != null) {
            showReceipt(call, change.receiptId(), false);
        } else if ("receiptHistory".equals(row.state())) {
            showHistory(call, 0);
        } else if ("catalog".equals(row.state())) {
            catalog.fromBasket(call);
        } else {
            render(row, call.member(), call.output(), change.error());
        }
    }

    private Change confirm(InteractionSession row, Member buyer) {
        if ("confirmed".equals(row.state())) {
            return new Change(row, null, Long.valueOf(row.payload()));
        }
        if (!"basket".equals(row.state())) {
            return null;
        }
        var draft = read(row);
        if (draft.lines().isEmpty()) {
            return new Change(sessions.advance(row, "basket", write(draft), false), "purchase.empty");
        }
        var receipt = purchases.confirm(buyer, row.id(), draft);
        sessions.finish(row, String.valueOf(receipt.id()));
        return new Change(row, null, receipt.id());
    }

    private InteractionSession resumeChoice(InteractionSession row) {
        String state = row.state().startsWith("resume:") ? row.state() : "resume:" + row.state();
        return sessions.advance(row, state, row.payload(), false);
    }

    private Change button(InteractionSession row, String action, Member buyer) {
        var draft = read(row);
        String state = row.state();
        if ("resume".equals(action) && state.startsWith("resume:")) {
            String previous = state.substring(7);
            if (List.of("home", "cancel", "catalog", "receiptHistory").contains(previous)) {
                previous = "basket";
            }
            return next(row, previous, draft);
        }
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
        if ("retry".equals(action) && List.of("name", "price", "editPrice", "photo").contains(state)) {
            return next(row, state, draft);
        }
        if ("basket".equals(state)) {
            return switch (action) {
                case "add" -> next(row, "catalog", draft);
                case "history" -> next(row, "receiptHistory", draft);
                case "photo" -> next(row, "photo", draft);
                case "shopping" -> next(row, "shopping", draft.input(null, null, 0));
                case "edit", "remove" -> next(row, action, draft);
                case "participants" -> draft.personal() ? null : next(row, action, draft.input(null, null, 0));
                case "sharing" -> next(row, "basket", draft.sharing(!draft.personal(), draft.participantIds()));

                default -> null;
            };
        }
        if ("shopping".equals(state)) {
            if ("clear".equals(action)) {
                return next(row, state, draft.shopping(List.of()));
            }
            if (action.matches("s:[0-9a-z]{1,13}")) {
                long id = Long.parseLong(action.substring(2), 36);
                var ids = new ArrayList<>(draft.shoppingIds());
                if (!ids.remove(id)) {
                    if (purchases.shoppingItems(buyer).stream().noneMatch(item -> item.getId() == id)) {
                        return new Change(row, "purchase.listChanged");
                    }
                    ids.add(id);
                }
                return next(row, state, draft.shopping(ids));
            }
            if (action.matches("page:[0-9]{1,5}")) {
                int page = Integer.parseInt(action.substring(5));
                if (page * 8 < purchases.shoppingItems(buyer).size()) {
                    return next(row, state, draft.input(null, null, page));
                }
            }
        }
        if ("participants".equals(state)) {
            if (action.matches("t:[0-9a-z]{1,13}")) {
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
        var change = sessions.withReply(expected, row -> reply(row, message));
        if (change.isEmpty()) {
            output.send(message.getChatId(), messages.getMessage("v2.promptExpired"), null);
        } else if (change.get().error() != null) {
            output.send(message.getChatId(), messages.getMessage(change.get().error()), null);
        } else {
            render(change.get().session(), member, output, null);
        }
    }

    private Change reply(InteractionSession row, Message message) {
        var draft = read(row);
        String text = message.getText();
        if ("photo".equals(row.state())) {
            var photo = toPhoto(message);
            return photo == null ? new Change(row, "purchase.invalidPhoto") : next(row, "basket", draft.attach(photo));
        }
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
            case "photo" -> {
                text = messages.getMessage("purchase.photo");
                prompt = messages.getMessage("purchase.enterPhoto");
                buttons.add(new MenuViews.Button("v2.retry", "retry"));
            }
            case "name", "price", "editPrice" -> {
                text = messages.getMessage("menu.basket");
                prompt = "name".equals(row.state()) ? messages.getMessage("purchase.enterName")
                        : messages.getMessage("purchase.enterPrice", capitalize(draft.label()));
                buttons.add(new MenuViews.Button("v2.retry", "retry"));
            }
            case "shopping" -> {
                text = messages.getMessage("purchase.markList");
                var all = purchases.shoppingItems(member);
                int page = draft.index();
                int end = Math.min(all.size(), (page + 1) * 8);
                for (int i = page * 8; i < end; i++) {
                    var item = all.get(i);
                    String key = draft.shoppingIds().contains(item.getId()) ? "purchase.selected" : "menu.value";
                    buttons.add(new MenuViews.Button(key, "s:" + Long.toString(item.getId(), 36), item.getText()));
                }
                if (all.isEmpty()) {
                    text += "\n" + messages.getMessage("v2.empty");
                }
                if (!draft.shoppingIds().isEmpty()) {
                    buttons.add(new MenuViews.Button("purchase.clearList", "clear"));
                }
                if (page > 0) {
                    buttons.add(new MenuViews.Button("catalog.previous", "page:" + (page - 1)));
                }
                if (end < all.size()) {
                    buttons.add(new MenuViews.Button("catalog.next", "page:" + (page + 1)));
                }
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
                if (row.state().startsWith("resume:")) {
                    text = messages.getMessage("purchase.savedDraft") + "\n\n" + text;
                    buttons.add(new MenuViews.Button("menu.resume", "resume"));
                    break;
                }
                buttons.add(new MenuViews.Button("purchase.add", "add"));
                if (!draft.lines().isEmpty()) {
                    buttons.add(new MenuViews.Button("purchase.edit", "edit"));
                    buttons.add(new MenuViews.Button("purchase.remove", "remove"));
                }
                if (!draft.personal()) {
                    buttons.add(new MenuViews.Button("purchase.participants", "participants"));
                }
                buttons.add(new MenuViews.Button("purchase.sharing", "sharing"));
                buttons.add(new MenuViews.Button("purchase.photo", "photo"));
                buttons.add(new MenuViews.Button("purchase.history", "history"));
                buttons.add(new MenuViews.Button("purchase.markList", "shopping"));
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
                .append(draft.photo() == null ? "" : "\n" + messages.getMessage("purchase.photoAttached"))
                .append(draft.personal() ? "" : "\n" + messages.getMessage("purchase.members",
                        String.join(", ", purchases.participants(member, draft).stream().map(Member::getDisplayName).toList())))
                .toString();
    }

    private void receiptButton(CallbackRouter.Call call) {
        var result = sessions.withButton(call, RECEIPTS, (row, action) -> {
            boolean page = action.matches("h:[0-9]{1,6}");
            boolean receipt = "history".equals(row.state()) && action.matches("r:[0-9a-z]{1,13}");
            boolean photo = "receipt".equals(row.state()) && "showPhoto".equals(action);
            boolean voidPreview = "receipt".equals(row.state()) && "void".equals(action);
            boolean voidConfirm = "voidConfirm".equals(row.state()) && "voidYes".equals(action);
            if (!page && !receipt && !photo && !voidPreview && !voidConfirm && !"h:home".equals(action)) {
                return null;
            }
            if (voidConfirm && !purchases.voidReceipt(call.member(), Long.parseLong(row.payload()))) {
                return null;
            }
            sessions.advance(row, "closed", "{}", true);
            return photo ? "photo:" + row.payload() : voidPreview ? "void:" + row.payload() : action;
        });
        if (result.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        call.answer().send(null);
        String action = result.get();
        if ("h:home".equals(action)) {
            views.main(call.member(), call.message().getChatId(), messages.getMessage("menu.title"), call.output());
        } else if (action.startsWith("h:")) {
            showHistory(call, Integer.parseInt(action.substring(2)));
        } else if (action.startsWith("r:")) {
            showReceipt(call, Long.parseLong(action.substring(2), 36), false);
        } else if (action.startsWith("void:")) {
            showVoid(call, Long.parseLong(action.substring(5)));
        } else if ("voidYes".equals(action)) {
            call.output().send(call.message().getChatId(), messages.getMessage("purchase.corrected"), null);
            open("basket", call);
        } else {
            showReceipt(call, Long.parseLong(action.substring(6)), true);
        }
    }

    private void showHistory(CallbackRouter.Call call, int page) {
        var history = purchases.history(call.member(), page);
        var buttons = new ArrayList<MenuViews.Button>();
        history.stream().limit(8).forEach(receipt -> buttons.add(new MenuViews.Button("purchase.historyRow",
                "r:" + Long.toString(receipt.id(), 36), String.valueOf(receipt.id()), money.format(receipt.total()),
                "VOID".equals(receipt.status()) ? messages.getMessage("purchase.void") : "")));
        if (page > 0) {
            buttons.add(new MenuViews.Button("catalog.previous", "h:" + (page - 1)));
        }
        if (history.size() > 8) {
            buttons.add(new MenuViews.Button("catalog.next", "h:" + (page + 1)));
        }
        buttons.add(new MenuViews.Button("menu.home", "h:home"));
        var row = sessions.navigate(call.member(), call.message().getChatId(), RECEIPTS, "history", "{}");
        views.send(row, kind(), messages.getMessage("purchase.history") + (history.isEmpty()
                ? "\n" + messages.getMessage("v2.empty") : ""), buttons, call.output());
    }

    private void showReceipt(CallbackRouter.Call call, long id, boolean showPhoto) {
        var receipt = purchases.receipt(call.member(), id).orElse(null);
        if (receipt == null) {
            call.output().send(call.message().getChatId(), messages.getMessage("v2.empty"), null);
            return;
        }
        String number = String.valueOf(id);
        var text = new StringBuilder(messages.getMessage("purchase.confirmed", number));
        for (var line : receipt.lines()) {
            text.append("\n").append(line.label()).append(" — ").append(money.format(line.price()));
        }
        text.append("\n").append(messages.getMessage("purchase.total", money.format(receipt.total())))
                .append("\n").append(messages.getMessage("purchase.buyer", receipt.buyer(), TIME.format(receipt.time())))
                .append("\n").append(messages.getMessage(receipt.shared() ? "purchase.shared" : "purchase.personal"));
        if ("VOID".equals(receipt.status())) {
            text.append("\n").append(messages.getMessage("purchase.void"));
        }
        if (receipt.shared()) {
            text.append("\n").append(messages.getMessage("purchase.members", String.join(", ", receipt.participants())))
                    .append("\n").append(messages.getMessage("purchase.expenseId", receipt.expenseId().toString()));
        }
        var buttons = new ArrayList<MenuViews.Button>();
        if (receipt.photo() != null) {
            buttons.add(new MenuViews.Button("purchase.photo", "showPhoto"));
            if (showPhoto && !call.output().photo(call.message().getChatId(), receipt.photo().fileId(),
                    messages.getMessage("purchase.confirmed", number))) {
                text.append("\n").append(messages.getMessage("v2.retry"));
            }
        }
        if (receipt.buyerId() == call.member().getId() && "CONFIRMED".equals(receipt.status())) {
            buttons.add(new MenuViews.Button("purchase.correct", "void"));
        }
        buttons.add(new MenuViews.Button("purchase.history", "h:0"));
        buttons.add(new MenuViews.Button("menu.home", "h:home"));
        var row = sessions.navigate(call.member(), call.message().getChatId(), RECEIPTS, "receipt", number);
        views.send(row, kind(), text.toString(), buttons, call.output());
    }

    private void showVoid(CallbackRouter.Call call, long id) {
        var target = purchases.receipt(call.member(), id).orElse(null);
        if (target == null || target.buyerId() != call.member().getId() || !"CONFIRMED".equals(target.status())) {
            call.output().send(call.message().getChatId(), messages.getMessage("v2.denied"), null);
            return;
        }
        var row = sessions.navigate(call.member(), call.message().getChatId(), RECEIPTS, "voidConfirm", String.valueOf(id));
        views.send(row, kind(), messages.getMessage("purchase.voidConfirm", String.valueOf(id)), List.of(
                new MenuViews.Button("menu.confirm", "voidYes"),
                new MenuViews.Button("menu.cancel", "h:0")), call.output());
    }

    static PurchaseDraft.Photo toPhoto(Message message) {
        if (!message.hasPhoto() || message.getDocument() != null) {
            return null;
        }
        return message.getPhoto().stream().filter(size -> size.getFileSize() != null)
                .max(Comparator.comparingInt(size -> size.getFileSize()))
                .map(size -> new PurchaseDraft.Photo(size.getFileId(), "image/jpeg", size.getFileSize()))
                .filter(PurchaseDraft.Photo::valid).orElse(null);
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

    private record Change(InteractionSession session, String error, Long receiptId) {
        Change(InteractionSession session, String error) {
            this(session, error, null);
        }
    }
}
