package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.BalanceService;
import kz.homebot.service.DebtCalculator;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.ExpenseSplitter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ExpenseService.class, BalanceService.class, ExpenseSplitter.class})
class RepaymentFlowTest {
    @Autowired ExpenseService expenses;
    @Autowired BalanceService balances;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test
    void fullAndPartialRepaymentAndUndo() {
        Household home = households.save(new Household(-10L, "Home"));
        Member asan = members.save(new Member(home, 1L, "Asan"));
        Member berik = members.save(new Member(home, 2L, "Berik"));
        expenses.addExpense(home, asan, new BigDecimal("1200.00"), "Food");
        assertThat(balances.getBalances(home).get(berik.getId())).isEqualByComparingTo("-600.00");

        var partial = expenses.recordRepayment(home, berik, asan, new BigDecimal("200.00"), "Repayment");
        assertThat(balances.getBalances(home).get(berik.getId())).isEqualByComparingTo("-400.00");
        assertThat(expenses.getHistory(home)).hasSize(2);
        assertThat(expenses.deleteEntryById(home, asan, partial.getId()))
                .isEqualTo(ExpenseService.DeleteResult.NOT_OWNER);
        assertThat(expenses.deleteEntryById(home, berik, partial.getId()))
                .isEqualTo(ExpenseService.DeleteResult.SUCCESS);
        assertThat(balances.getBalances(home).get(berik.getId())).isEqualByComparingTo("-600.00");

        expenses.recordRepayment(home, berik, asan, new BigDecimal("600.00"), "Repayment");
        assertThat(new DebtCalculator().calculate(balances.getBalances(home))).isEmpty();
        assertThat(expenses.deleteLastEntry(home, berik)).isPresent();
        assertThat(balances.getBalances(home).get(berik.getId())).isEqualByComparingTo("-600.00");
    }

    @Test
    void selectedMembersIncludePayerAndSplitExactly() {
        Household home = households.save(new Household(-11L, "Other Home"));
        Member payer = members.save(new Member(home, 11L, "Payer"));
        Member selected = members.save(new Member(home, 12L, "Selected"));
        Member excluded = members.save(new Member(home, 13L, "Excluded"));

        var result = expenses.addExpense(home, payer, new BigDecimal("100.01"), "Pizza",
                java.util.List.of(selected));
        assertThat(result.members()).extracting(Member::getId)
                .containsExactly(payer.getId(), selected.getId());
        assertThat(result.shares()).containsExactly(new BigDecimal("50.01"), new BigDecimal("50.00"));
        assertThat(balances.getBalances(home).get(excluded.getId())).isEqualByComparingTo("0.00");
    }
}
