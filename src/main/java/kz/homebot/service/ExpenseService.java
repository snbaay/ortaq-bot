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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

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

    /**
     * Saves a settlement (repayment).
     * The payer is who paid back, and the single share belongs to the recipient for the full amount.
     */
    @Transactional
    public Expense recordRepayment(Household household, Member payer, Member recipient, BigDecimal amount, String description) {
        if (household == null || payer == null || recipient == null
                || !household.getId().equals(payer.getHousehold().getId())
                || !household.getId().equals(recipient.getHousehold().getId())
                || payer.getId().equals(recipient.getId())
                || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(new BigDecimal("100000000")) > 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid repayment");
        }
        Expense expense = new Expense(household, payer, amount.setScale(2), description,
                LocalDateTime.now(ZoneId.of("Asia/Almaty")), false, true);
        expense = expenseRepository.save(expense);

        ExpenseShare share = new ExpenseShare(expense, recipient, amount);
        expenseShareRepository.save(share);

        return expense;
    }

    /**
     * Returns the last 10 non-deleted entries (expenses and repayments).
     */
    @Transactional(readOnly = true)
    public List<Expense> getHistory(Household household) {
        return expenseRepository.findTop10ByHouseholdAndDeletedFalseOrderByIdDesc(household);
    }

    /**
     * Soft-deletes the sender's last non-deleted entry.
     */
    @Transactional
    public Optional<Expense> deleteLastEntry(Household household, Member member) {
        Optional<Expense> last = expenseRepository.findFirstByHouseholdAndPayerAndDeletedFalseOrderByIdDesc(household, member);
        last.ifPresent(e -> {
            e.setDeleted(true);
            expenseRepository.save(e);
        });
        return last;
    }

    public enum DeleteResult {
        SUCCESS,
        NOT_FOUND,
        NOT_OWNER
    }

    /**
     * Soft-deletes an entry by ID, only if it belongs to the given household and the payer is the sender.
     */
    @Transactional
    public DeleteResult deleteEntryById(Household household, Member member, Long id) {
        Optional<Expense> opt = expenseRepository.findByIdAndHousehold(id, household);
        if (opt.isEmpty() || opt.get().isDeleted()) {
            return DeleteResult.NOT_FOUND;
        }
        Expense expense = opt.get();
        if (!expense.getPayer().getId().equals(member.getId())) {
            return DeleteResult.NOT_OWNER;
        }
        expense.setDeleted(true);
        expenseRepository.save(expense);
        return DeleteResult.SUCCESS;
    }

    /** Result DTO carrying the saved expense, member list and their shares (same order). */
    public record ExpenseResult(Expense expense, List<Member> members, List<BigDecimal> shares) {}
}
