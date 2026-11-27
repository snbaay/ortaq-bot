package kz.homebot.repository;

import kz.homebot.domain.Household;
import kz.homebot.domain.ShoppingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {
    List<ShoppingItem> findByHouseholdAndBoughtFalseOrderByIdAsc(Household household);
    long countByHouseholdAndBoughtFalse(Household household);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ShoppingItem> findByIdAndHousehold(Long id, Household household);
}
