package kz.homebot.repository;

import kz.homebot.domain.Household;
import kz.homebot.domain.PaymentDeadline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentDeadlineRepository extends JpaRepository<PaymentDeadline, Long> {
    List<PaymentDeadline> findByHouseholdAndActiveTrue(Household household);
    long countByHouseholdAndActiveTrue(Household household);
    Optional<PaymentDeadline> findByIdAndHouseholdAndActiveTrue(Long id, Household household);
}
