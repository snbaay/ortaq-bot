package kz.homebot.repository;

import kz.homebot.domain.Expense;
import kz.homebot.domain.ExpenseShare;
import kz.homebot.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpense(Expense expense);
    List<ExpenseShare> findByMember(Member member);
}
