package kz.homebot.repository;

import kz.homebot.domain.Expense;
import kz.homebot.domain.ExpenseShare;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpense(Expense expense);
    List<ExpenseShare> findByMember(Member member);

    /**
     * Returns [memberId, totalShareAmount] for each member in a household (non-deleted expenses).
     */
    @Query("""
            SELECT es.member.id, SUM(es.shareAmount)
            FROM ExpenseShare es
            WHERE es.expense.household = :household AND es.expense.deleted = false
            GROUP BY es.member.id
            """)
    List<Object[]> sumSharesByMember(@Param("household") Household household);

    @Query("select es.member.id, sum(es.shareAmount) from ExpenseShare es "
            + "where es.expense.household = :household and es.expense.deleted = false "
            + "and es.expense.settlement = false and es.expense.createdAt >= :start "
            + "and es.expense.createdAt < :end group by es.member.id")
    List<Object[]> monthlyConsumed(@Param("household") Household household,
                                   @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
