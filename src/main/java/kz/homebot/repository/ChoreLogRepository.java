package kz.homebot.repository;

import kz.homebot.domain.ChoreLog;
import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChoreLogRepository extends JpaRepository<ChoreLog, Long> {
    List<ChoreLog> findTop10ByHouseholdOrderByIdDesc(Household household);
    Optional<ChoreLog> findFirstByHouseholdAndTextContainingIgnoreCaseOrderByIdDesc(Household household, String needle);
}
