package kz.homebot.repository;

import kz.homebot.domain.ChoreLog;
import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
import java.util.Optional;

public interface ChoreLogRepository extends JpaRepository<ChoreLog, Long> {
    @EntityGraph(attributePaths = "member")
    List<ChoreLog> findTop10ByHouseholdOrderByIdDesc(Household household);
    @EntityGraph(attributePaths = "member")
    Optional<ChoreLog> findFirstByHouseholdAndTextContainingIgnoreCaseOrderByIdDesc(Household household, String needle);
}
