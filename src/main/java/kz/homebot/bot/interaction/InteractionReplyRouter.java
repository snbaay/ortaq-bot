package kz.homebot.bot.interaction;

import kz.homebot.bot.TelegramOutput;
import kz.homebot.domain.Member;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.MessageService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.List;

@Component
public class InteractionReplyRouter {
    private final SessionStore sessions;
    private final MemberRegistrationService members;
    private final MessageService messages;
    private final List<Handler> handlers;

    public InteractionReplyRouter(SessionStore sessions, MemberRegistrationService members,
                                  MessageService messages, List<Handler> handlers) {
        this.sessions = sessions;
        this.members = members;
        this.messages = messages;
        this.handlers = handlers;
    }

    public void route(Message message, TelegramOutput output) {
        if (message.getReplyToMessage() == null || message.getReplyToMessage().getMessageId() == null) {
            return;
        }
        var member = members.findRegisteredMember(message.getChatId(), message.getFrom().getId());
        if (member.isEmpty()) {
            return;
        }
        var session = sessions.reply(message.getChatId(), message.getFrom().getId(),
                message.getReplyToMessage().getMessageId());
        if (session.isEmpty() || !member.get().getHousehold().getId().equals(session.get().householdId())) {
            return;
        }
        InteractionSession row = session.get();
        Instant now = Instant.now();
        if (!row.activeAt(now) || row.promptExpiresAt() == null || !row.promptExpiresAt().isAfter(now)) {
            output.send(message.getChatId(), messages.getMessage("v2.promptExpired"), null);
            return;
        }
        handlers.stream().filter(handler -> handler.replyKind().equals(row.kind())).findFirst()
                .ifPresent(handler -> handler.reply(row, member.get(), message, output));
    }

    public interface Handler {
        String replyKind();
        void reply(InteractionSession session, Member member, Message message, TelegramOutput output);
    }
}
