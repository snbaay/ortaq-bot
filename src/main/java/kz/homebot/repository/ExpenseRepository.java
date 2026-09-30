package kz.homebot.repository;

import kz.homebot.domain.Expense;
import kz.homebot.domain.Household;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByHouseholdAndDeletedFalse(Household household);
}
