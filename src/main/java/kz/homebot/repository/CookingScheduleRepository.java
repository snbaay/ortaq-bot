package kz.homebot.repository;

import kz.homebot.domain.CookingSchedule;
import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.DayOfWeek;
import java.util.List;

public interface CookingScheduleRepository extends JpaRepository<CookingSchedule, Long> {
    List<CookingSchedule> findByHousehold(Household household);
    List<CookingSchedule> findByHouseholdAndDayOfWeek(Household household, DayOfWeek day);
    void deleteByHouseholdAndDayOfWeek(Household household, DayOfWeek day);
    void deleteByHousehold(Household household);
}
