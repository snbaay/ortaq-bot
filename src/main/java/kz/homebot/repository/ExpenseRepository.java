package kz.homebot.repository;

import kz.homebot.domain.Expense;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByHouseholdAndDeletedFalse(Household household);

    List<Expense> findTop10ByHouseholdAndDeletedFalseOrderByIdDesc(Household household);

    Optional<Expense> findFirstByHouseholdAndPayerAndDeletedFalseOrderByIdDesc(Household household, Member payer);

    Optional<Expense> findByIdAndHousehold(Long id, Household household);

    /**
     * Returns [payerId, totalPaid] for each payer in a household (non-deleted expenses).
     */
    @Query("""
            SELECT e.payer.id, SUM(e.amount)
            FROM Expense e
            WHERE e.household = :household AND e.deleted = false
            GROUP BY e.payer.id
            """)
    List<Object[]> sumPaidByMember(@Param("household") Household household);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.household = :household "
            + "and e.deleted = false and e.settlement = false and e.createdAt >= :start and e.createdAt < :end")
    BigDecimal monthlyTotal(@Param("household") Household household,
                            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select e.payer.id, sum(e.amount) from Expense e where e.household = :household "
            + "and e.deleted = false and e.settlement = false and e.createdAt >= :start and e.createdAt < :end "
            + "group by e.payer.id")
    List<Object[]> monthlyPaid(@Param("household") Household household,
                               @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select e from Expense e where e.household = :household and e.deleted = false "
            + "and e.settlement = false and e.createdAt >= :start and e.createdAt < :end order by e.amount desc, e.id asc")
    List<Expense> monthlyTop(@Param("household") Household household,
                             @Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
                             Pageable pageable);
}
