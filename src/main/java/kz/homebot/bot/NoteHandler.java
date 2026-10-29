package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.MessageService;
import kz.homebot.service.NoteService;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class NoteHandler implements CommandHandler {
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final NoteService notes;
    private final MessageService messages;
    public NoteHandler(HouseholdRepository households, MemberRepository members,
                       NoteService notes, MessageService messages) {
        this.households = households; this.members = members; this.notes = notes; this.messages = messages;
    }
    @Override public List<String> commands() { return List.of("jazba", "jazba_oshiru"); }
    @Override public void handle(CommandContext ctx) {
        if (!ctx.isGroup()) { ctx.reply(messages.getMessage("error.groupOnly")); return; }
        var household = households.findByTelegramChatId(ctx.chatId()).orElse(null);
        if (household == null) return;
        if ("jazba_oshiru".equals(ctx.command())) {
            try {
                long id = Long.parseLong(ctx.args().trim());
                ctx.reply(messages.getMessage(notes.delete(household, id) ? "note.deleted" : "note.notFound"));
            } catch (NumberFormatException e) { ctx.reply(messages.getMessage("note.usageDelete")); }
            return;
        }
        if (!ctx.args().isBlank()) {
            var author = members.findByHouseholdAndTelegramUserId(household, ctx.sender().id()).orElse(null);
            if (author == null) return;
            try { ctx.reply(messages.getMessage("note.added", notes.add(household, author, ctx.args()).getId())); }
            catch (IllegalArgumentException e) { ctx.reply(messages.getMessage("note.invalid")); }
            return;
        }
        var latest = notes.latest(household);
        if (latest.isEmpty()) { ctx.reply(messages.getMessage("note.empty")); return; }
        StringBuilder reply = new StringBuilder(messages.getMessage("note.header"));
        for (var note : latest) reply.append("\n#").append(note.getId()).append(" ")
                .append(note.getText()).append(" — ").append(note.getAuthor().getDisplayName());
        ctx.reply(reply.toString());
    }
}
