package kz.homebot.bot.menu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.homebot.bot.CommandContext;
import kz.homebot.bot.TelegramOutput;
import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.interaction.InteractionReplyRouter;
import kz.homebot.bot.interaction.InteractionSession;
import kz.homebot.bot.interaction.SessionStore;
import kz.homebot.config.AllowedChats;
import kz.homebot.domain.Member;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import kz.homebot.service.PurchaseDraft;
import kz.homebot.service.RepaymentRequestService;
import kz.homebot.service.RepaymentRequestService.Decision;
import kz.homebot.service.RepaymentRequestService.Request;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Component
public class RepaymentMenu implements CallbackRouter.Handler, InteractionReplyRouter.Handler, MainMenuHandler.Destination {
    private static final String KIND = "repayment";
    private static final Draft EMPTY = new Draft(null, null, null, null, 0);
    private final SessionStore sessions;
    private final MenuViews views;
    private final MainMenuHandler main;
    private final RepaymentRequestService requests;
    private final AllowedChats allowedChats;
    private final MessageService messages;
    private final MoneyFormatter money;
    private final ObjectMapper json;

    public RepaymentMenu(SessionStore sessions, MenuViews views, MainMenuHandler main,
                         RepaymentRequestService requests, AllowedChats allowedChats, MessageService messages,
                         MoneyFormatter money, ObjectMapper json) {
        this.sessions = sessions;
        this.views = views;
        this.main = main;
        this.requests = requests;
        this.allowedChats = allowedChats;
        this.messages = messages;
        this.money = money;
        this.json = json;
    }

    @Override
    public String kind() {
        return "r";
    }

    @Override
    public String replyKind() {
        return KIND;
    }

    @Override
    public boolean supports(String action) {
        return "repay".equals(action);
    }

    @Override
    public void open(String action, CallbackRouter.Call call) {
        show(openDraft(call.member(), call.message().getChatId(), "recipient", EMPTY), call.member(), call.output());
    }

    public void fromCommand(CommandContext context, Member sender, Member recipient, BigDecimal amount) {
        if (context.source() == null) {
            context.reply(messages.getMessage("tolem.usage"));
            return;
        }
        show(openDraft(sender, context.chatId(), "preview", new Draft(recipient.getId(), amount, null, null, 0)),
                sender, context.source().output());
    }

    public boolean correctExpense(CommandContext context, Member actor, long expense) {
        if (context.source() == null) {
            return false;
        }
        return requests.forExpense(actor, expense).map(request -> {
            show(correctionDraft(actor, request), actor, context.source().output());
            return true;
        }).orElse(false);
    }

    @Override
    public void handle(CallbackRouter.Call call) {
        String[] data = call.data().split(":", 3);
        if (data.length == 2 && data[0].matches("[0-9a-f]{32}")) {
            decision(call, data[0], data[1]);
            return;
        }
        if (data.length == 3 && "confirm".equals(data[2])) {
            submit(call);
            return;
        }
        var result = sessions.withButton(call, KIND, (row, action) -> navigate(row, call.member(), action));
        if (result.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        call.answer().send(null);
        var state = result.get();
        if ("closed".equals(state.state())) {
            main.submenu(call, "finance", messages.getMessage("v2.empty"));
        } else {
            show(state, call.member(), call.output());
        }
    }

    private void submit(CallbackRouter.Call call) {
        var request = sessions.withConfirmation(call, KIND, (row, action) -> {
            if ("confirmed".equals(row.state())) {
                return requests.forMember(call.member(), Long.parseLong(row.payload())).orElse(null);
            }
            if (!List.of("preview", "reversalPreview").contains(row.state())) {
                return null;
            }
            Draft draft = read(row);
            String source = call.bot() + ":" + call.updateId();
            Request pending = draft.original() == null
                    ? requests.submit(call.member(), draft.recipient(), draft.amount(), source)
                    : requests.correct(call.member(), draft.original(), draft.reason(), source).orElse(null);
            if (pending != null) {
                sessions.finish(row, String.valueOf(pending.id()));
            }
            return pending;
        });
        if (request.isEmpty()) {
            call.answer().send(messages.getMessage("v2.stale"));
            return;
        }
        Request row = request.get();
        call.answer().send(messages.getMessage("repay.sent"));
        if (!deliver(row, call.output()) && row.announcementId() == null && "PENDING".equals(row.status())) {
            call.output().send(row.chat(), messages.getMessage("repay.pendingDelivery"), null);
        }
    }

    private void decision(CallbackRouter.Call call, String token, String action) {
        long chat = call.message().getChatId();
        int message = call.message().getMessageId();
        if ("correct".equals(action)) {
            var target = requests.forCorrection(call.member(), chat, token, message);
            if (target.isEmpty()) {
                call.answer().send(messages.getMessage("v2.denied"));
                return;
            }
            call.answer().send(null);
            show(correctionDraft(call.member(), target.get()), call.member(), call.output());
            return;
        }
        Decision decision = switch (action) {
            case "accept" -> Decision.ACCEPT;
            case "reject" -> Decision.REJECT;
            case "cancel" -> Decision.CANCEL;
            default -> null;
        };
        var result = decision == null ? java.util.Optional.<Request>empty()
                : requests.decide(call.member(), chat, token, message, decision);
        if (result.isEmpty()) {
            call.answer().send(messages.getMessage("v2.denied"));
            return;
        }
        Request request = result.get();
        call.answer().send(outcome(request));
        deliver(request, call.output());
    }

    private InteractionSession correctionDraft(Member actor, Request target) {
        return openDraft(actor, target.chat(), "reason", new Draft(null, target.amount(), target.id(), null, 0));
    }

    private InteractionSession openDraft(Member actor, long chat, String state, Draft draft) {
        return sessions.withOwned(actor, chat, KIND, state, write(draft), row -> {
            if (row.version() == 0) {
                return sessions.advance(row, state, row.payload(), false);
            }
            String stored = row.state().startsWith("resume:") ? row.state() : "resume:" + row.state();
            return sessions.advance(row, stored, row.payload(), false);
        });
    }

    private InteractionSession navigate(InteractionSession row, Member sender, String action) {
        Draft draft = read(row);
        if ("resume".equals(action) && row.state().startsWith("resume:")) {
            return sessions.advance(row, row.state().substring(7), row.payload(), false);
        }
        if ("home".equals(action) || "cancel".equals(action)
                || "back".equals(action) && List.of("recipient", "reason").contains(row.state())) {
            return sessions.advance(row, "closed", "{}", true);
        }
        if ("back".equals(action)) {
            if (row.state().startsWith("resume:")) {
                return null;
            }
            String earlier = switch (row.state()) {
                case "preview" -> "amount";
                case "reversalPreview" -> "reason";
                default -> "recipient";
            };
            return sessions.advance(row, earlier, row.payload(), false);
        }
        if ("retry".equals(action) && List.of("amount", "reason").contains(row.state())) {
            return sessions.advance(row, row.state(), row.payload(), false);
        }
        if ("recipient".equals(row.state()) && action.matches("to:[0-9a-z]{1,13}")) {
            long id = Long.parseLong(action.substring(3), 36);
            if (requests.recipients(sender).stream().anyMatch(member -> member.getId() == id)) {
                return sessions.advance(row, "amount", write(new Draft(id, null, null, null, draft.page())), false);
            }
        }
        if ("recipient".equals(row.state()) && action.matches("page:[0-9]{1,5}")) {
            int page = Integer.parseInt(action.substring(5));
            if (page * 8 < requests.recipients(sender).size()) {
                return sessions.advance(row, "recipient", write(new Draft(null, null, null, null, page)), false);
            }
        }
        return null;
    }

    @Override
    public void reply(InteractionSession expected, Member member, Message message, TelegramOutput output) {
        var next = sessions.withReply(expected, row -> {
            Draft draft = read(row);
            String input = message.getText() == null ? "" : message.getText().strip();
            if ("amount".equals(row.state())) {
                var amount = PurchaseDraft.parsePrice(input);
                return amount == null ? row : sessions.advance(row, "preview",
                        write(new Draft(draft.recipient(), amount, null, null, draft.page())), false);
            }
            if ("reason".equals(row.state())) {
                return input.isEmpty() || input.length() > 200 || input.chars().anyMatch(Character::isISOControl)
                        ? row : sessions.advance(row, "reversalPreview",
                        write(new Draft(null, draft.amount(), draft.original(), input, 0)), false);
            }
            return null;
        });
        if (next.isEmpty()) {
            output.send(message.getChatId(), messages.getMessage("v2.promptExpired"), null);
        } else if (next.get().version() == expected.version()) {
            output.send(message.getChatId(), messages.getMessage("amount".equals(expected.state())
                    ? "v2.money" : "repay.badReason"), null);
        } else {
            show(next.get(), member, output);
        }
    }

    private void show(InteractionSession row, Member member, TelegramOutput output) {
        Draft draft = read(row);
        var buttons = new ArrayList<MenuViews.Button>();
        String text;
        String prompt = null;
        switch (row.state()) {
            case "recipient" -> {
                var recipients = requests.recipients(member);
                text = messages.getMessage("repay.recipient") + (recipients.isEmpty() ? "\n" + messages.getMessage("v2.empty") : "");
                int end = Math.min(recipients.size(), (draft.page() + 1) * 8);
                for (int i = draft.page() * 8; i < end; i++) {
                    Member candidate = recipients.get(i);
                    buttons.add(new MenuViews.Button("menu.value", "to:" + Long.toString(candidate.getId(), 36), candidate.getDisplayName()));
                }
                if (draft.page() > 0) {
                    buttons.add(new MenuViews.Button("catalog.previous", "page:" + (draft.page() - 1)));
                }
                if (end < recipients.size()) {
                    buttons.add(new MenuViews.Button("catalog.next", "page:" + (draft.page() + 1)));
                }
            }
            case "amount", "reason" -> {
                text = messages.getMessage("menu.repay");
                prompt = messages.getMessage("amount".equals(row.state()) ? "repay.amount" : "repay.reason");
                buttons.add(new MenuViews.Button("v2.retry", "retry"));
            }
            case "preview" -> {
                var recipient = requests.findMember(member, draft.recipient()).orElse(null);
                text = recipient == null ? messages.getMessage("v2.denied")
                        : messages.getMessage("repay.preview", recipient.getDisplayName(), money.format(draft.amount()));
                if (recipient != null) {
                    buttons.add(new MenuViews.Button("repay.send", "confirm"));
                }
            }
            case "reversalPreview" -> {
                text = messages.getMessage("repay.reversalPreview", draft.reason());
                buttons.add(new MenuViews.Button("repay.send", "confirm"));
            }
            default -> {
                if (!row.state().startsWith("resume:")) {
                    throw new IllegalStateException("Unexpected repayment step");
                }
                text = messages.getMessage("repay.savedDraft");
                buttons.add(new MenuViews.Button("menu.resume", "resume"));
            }
        }
        if (!row.state().startsWith("resume:")) {
            buttons.add(new MenuViews.Button("menu.back", "back"));
        }
        buttons.add(new MenuViews.Button("menu.cancel", "cancel"));
        buttons.add(new MenuViews.Button("menu.home", "home"));
        views.send(row, kind(), text, buttons, output);
        if (prompt != null) {
            var sent = output.send(row.chatId(), prompt, ForceReplyKeyboard.builder().forceReply(true).build());
            if (sent != null && sent.getMessageId() != null) {
                sessions.bindPrompt(row, sent.getMessageId());
            }
        }
    }

    public void retryDue(TelegramOutput output) {
        requests.expirePending();
        requests.due().forEach(request -> deliver(request, output));
    }

    private boolean deliver(Request request, TelegramOutput output) {
        boolean outcome = !"PENDING".equals(request.status());
        if (!allowedChats.allows(request.chat()) || !requests.claimDelivery(request.id(), outcome)) {
            return false;
        }
        List<InlineKeyboardRow> buttons = new ArrayList<>();
        String text;
        if (outcome) {
            text = outcome(request);
            if (request.isPayment() && "CONFIRMED".equals(request.status())) {
                buttons.add(eventButton("purchase.correct", request, "correct"));
            }
        } else {
            text = request.isPayment() ? messages.getMessage("repay.request", request.recipient(), request.sender(), money.format(request.amount()))
                    : messages.getMessage("repay.reversalRequest", request.recipient(), request.sender(), money.format(request.amount()), request.reason());
            buttons.add(eventButton("repay.accept", request, "accept"));
            buttons.add(eventButton("repay.reject", request, "reject"));
            buttons.add(eventButton("menu.cancel", request, "cancel"));
        }
        var sent = output.send(request.chat(), text, buttons.isEmpty() ? null : MenuViews.keyboard(buttons));
        if (sent != null && sent.getMessageId() != null) {
            requests.delivered(request.id(), outcome, sent.getMessageId());
            return true;
        }
        return false;
    }

    private InlineKeyboardRow eventButton(String key, Request request, String action) {
        return MenuViews.row(messages.getMessage(key), "v2:r:" + request.token() + ":" + action);
    }

    private String outcome(Request request) {
        return switch (request.status()) {
            case "CONFIRMED" -> request.isPayment() ? messages.getMessage("repay.confirmed", request.expenseId().toString())
                    : messages.getMessage("repay.reversed");
            case "REJECTED" -> messages.getMessage("repay.rejected", request.sender(), request.recipient());
            case "EXPIRED" -> messages.getMessage("repay.expired", request.sender());
            case "VOID" -> messages.getMessage("repay.cancelled", request.sender());
            default -> messages.getMessage("repay.sent");
        };
    }

    private Draft read(InteractionSession row) {
        try {
            return json.readValue(row.payload(), Draft.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid repayment draft", failure);
        }
    }

    private String write(Draft draft) {
        try {
            return json.writeValueAsString(draft);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not save repayment draft", failure);
        }
    }

    private record Draft(Long recipient, BigDecimal amount, Long original, String reason, int page) {}
}
