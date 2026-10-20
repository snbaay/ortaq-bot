package kz.homebot.service;

import kz.homebot.domain.Expense;
import kz.homebot.domain.ExpenseShare;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.ExpenseRepository;
import kz.homebot.repository.ExpenseShareRepository;
import kz.homebot.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final MemberRepository memberRepository;
    private final ExpenseSplitter expenseSplitter;

    public ExpenseService(ExpenseRepository expenseRepository,
                          ExpenseShareRepository expenseShareRepository,
                          MemberRepository memberRepository,
                          ExpenseSplitter expenseSplitter) {
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.memberRepository = memberRepository;
        this.expenseSplitter = expenseSplitter;
    }

    /**
     * Saves an expense and splits it equally among all current household members.
     * Returns the saved expense (with shares).
     */
    @Transactional
    public ExpenseResult addExpense(Household household, Member payer, BigDecimal amount, String description) {
        Expense expense = new Expense(household, payer, amount, description);
        expense = expenseRepository.save(expense);

        List<Member> members = memberRepository.findByHousehold(household);
        List<BigDecimal> shares = expenseSplitter.split(amount, members.size());

        for (int i = 0; i < members.size(); i++) {
            ExpenseShare share = new ExpenseShare(expense, members.get(i), shares.get(i));
            expenseShareRepository.save(share);
        }

        return new ExpenseResult(expense, members, shares);
    }

    /** Result DTO carrying the saved expense, member list and their shares (same order). */
    public record ExpenseResult(Expense expense, List<Member> members, List<BigDecimal> shares) {}
}
