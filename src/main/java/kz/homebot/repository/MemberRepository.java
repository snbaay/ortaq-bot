package kz.homebot.repository;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByHouseholdAndTelegramUserId(Household household, Long telegramUserId);
    List<Member> findByHousehold(Household household);
}
