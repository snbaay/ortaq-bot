package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.domain.Note;
import kz.homebot.repository.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import kz.homebot.config.Limits;

@Service
public class NoteService {
    public enum DeleteResult { DELETED, NOT_FOUND, NOT_OWNER }
    private final NoteRepository notes;
    public NoteService(NoteRepository notes) { this.notes = notes; }

    @Transactional
    public Note add(Household household, Member author, String text) {
        String cleaned = text == null ? "" : text.trim();
        if (author == null || author.getHousehold() == null || household == null
                || !household.getId().equals(author.getHousehold().getId())
                || cleaned.isEmpty() || cleaned.length() > Limits.NOTE) throw new IllegalArgumentException("Invalid note");
        return notes.save(new Note(household, author, cleaned, LocalDateTime.now(ZoneId.of("Asia/Almaty"))));
    }

    @Transactional(readOnly = true)
    public List<Note> latest(Household household) {
        return notes.findTop10ByHouseholdAndDeletedFalseOrderByIdDesc(household);
    }

    @Transactional
    public DeleteResult delete(Household household, Member requester, Long id) {
        if (requester == null || requester.getHousehold() == null || household == null
                || !household.getId().equals(requester.getHousehold().getId())) return DeleteResult.NOT_FOUND;
        var note = notes.findByIdAndHouseholdAndDeletedFalse(id, household).orElse(null);
        if (note == null) return DeleteResult.NOT_FOUND;
        if (!note.getAuthor().getId().equals(requester.getId())) return DeleteResult.NOT_OWNER;
        note.setDeleted(true);
        notes.save(note);
        return DeleteResult.DELETED;
    }
}
