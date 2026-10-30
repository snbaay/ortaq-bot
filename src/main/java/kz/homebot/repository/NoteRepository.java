package kz.homebot.repository;

import kz.homebot.domain.Household;
import kz.homebot.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findTop10ByHouseholdAndDeletedFalseOrderByIdDesc(Household household);
    Optional<Note> findByIdAndHouseholdAndDeletedFalse(Long id, Household household);
}
