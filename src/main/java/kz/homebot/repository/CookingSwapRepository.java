package kz.homebot.repository;

import kz.homebot.domain.CookingSwap;
import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CookingSwapRepository extends JpaRepository<CookingSwap, Long> {
    List<CookingSwap> findByHouseholdAndDateOrderByIdAsc(Household household, LocalDate date);
}
