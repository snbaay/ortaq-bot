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

@Service
public class NoteService {
    private final NoteRepository notes;
    public NoteService(NoteRepository notes) { this.notes = notes; }

    @Transactional
    public Note add(Household household, Member author, String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.isEmpty() || cleaned.length() > 300) throw new IllegalArgumentException("Invalid note");
        return notes.save(new Note(household, author, cleaned, LocalDateTime.now(ZoneId.of("Asia/Almaty"))));
    }

    @Transactional(readOnly = true)
    public List<Note> latest(Household household) {
        return notes.findTop10ByHouseholdAndDeletedFalseOrderByIdDesc(household);
    }

    @Transactional
    public boolean delete(Household household, Long id) {
        var note = notes.findByIdAndHouseholdAndDeletedFalse(id, household).orElse(null);
        if (note == null) return false;
        note.setDeleted(true);
        notes.save(note);
        return true;
    }
}
