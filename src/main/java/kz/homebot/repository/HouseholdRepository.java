package kz.homebot.repository;

import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findByTelegramChatId(Long telegramChatId);
}
