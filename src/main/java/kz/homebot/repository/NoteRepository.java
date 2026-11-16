package kz.homebot.repository;

import kz.homebot.domain.Household;
import kz.homebot.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note, Long> {
    @EntityGraph(attributePaths = "author")
    List<Note> findTop10ByHouseholdAndDeletedFalseOrderByIdDesc(Household household);
    Optional<Note> findByIdAndHouseholdAndDeletedFalse(Long id, Household household);
}
