package kz.homebot.bot.callback;

import kz.homebot.bot.TelegramOutput;
import kz.homebot.config.AllowedChats;
import kz.homebot.domain.Member;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class CallbackRouter {
    private static final Logger log = LoggerFactory.getLogger(CallbackRouter.class);
    private static final Pattern DATA = Pattern.compile("v2:([a-z]):([a-zA-Z0-9_:-]{1,59})");

    private final AllowedChats allowedChats;
    private final MemberRegistrationService members;
    private final ProcessedUpdates updates;
    private final List<Handler> handlers;
    private final MessageService messages;

    public CallbackRouter(AllowedChats allowedChats, MemberRegistrationService members, ProcessedUpdates updates,
                          List<Handler> handlers, MessageService messages) {
        this.allowedChats = allowedChats;
        this.members = members;
        this.updates = updates;
        this.handlers = handlers;
        this.messages = messages;
    }

    public void route(String bot, Update update, TelegramOutput output) {
        var query = update.getCallbackQuery();
        if (query == null || query.getId() == null) {
            return;
        }
        var answer = new Answer(query.getId(), output);
        try {
            if (!(query.getMessage() instanceof Message message) || message.getChat() == null
                    || message.getForwardOrigin() != null || message.getForwardDate() != null
                    || message.getFrom() == null || !Boolean.TRUE.equals(message.getFrom().getIsBot())
                    || !bot.equalsIgnoreCase(message.getFrom().getUserName())
                    || !allowedChats.allows(message.getChatId())) {
                answer.send(messages.getMessage("v2.denied"));
                return;
            }
            if (!message.isGroupMessage() && !message.isSuperGroupMessage()) {
                answer.send(messages.getMessage("v2.groupOnly"));
                return;
            }
            var user = query.getFrom();
            if (user == null || user.getId() == null || user.getId() <= 0 || Boolean.TRUE.equals(user.getIsBot())) {
                answer.send(messages.getMessage("v2.denied"));
                return;
            }
            var member = members.findRegisteredMember(message.getChatId(), user.getId());
            if (member.isEmpty()) {
                answer.send(messages.getMessage("v2.denied"));
                return;
            }
            var data = DATA.matcher(query.getData() == null ? "" : query.getData());
            if (!data.matches() || !updates.claim(bot, update.getUpdateId(), query.getId())) {
                answer.send(messages.getMessage("v2.stale"));
                return;
            }
            String kind = data.group(1);
            Handler handler = handlers.stream().filter(candidate -> candidate.kind().equals(kind)).findFirst().orElse(null);
            if (handler == null) {
                answer.send(messages.getMessage("v2.stale"));
                return;
            }
            handler.handle(new Call(member.get(), message, data.group(2), bot, update.getUpdateId(), output, answer));
        } catch (RuntimeException failure) {
            log.warn("Callback processing failed: {}", failure.getClass().getSimpleName());
            answer.send(messages.getMessage("v2.retry"));
        } finally {
            answer.send(null);
        }
    }

    public interface Handler {
        String kind();
        void handle(Call call);
    }

    public record Call(Member member, Message message, String data, String bot, Integer updateId,
                       TelegramOutput output, Answer answer) {}

    public static final class Answer {
        private final String id;
        private final TelegramOutput output;
        private boolean sent;

        private Answer(String id, TelegramOutput output) {
            this.id = id;
            this.output = output;
        }

        public void send(String text) {
            if (!sent) {
                sent = true;
                output.answer(id, text);
            }
        }
    }
}
