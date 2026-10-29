package kz.homebot.repository;

import kz.homebot.domain.CookingDuty;
import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface CookingDutyRepository extends JpaRepository<CookingDuty, Long> {
    Optional<CookingDuty> findByHouseholdAndDutyDate(Household household, LocalDate date);
    Optional<CookingDuty> findFirstByHouseholdAndDutyDateBeforeOrderByDutyDateDesc(Household household, LocalDate date);
}
