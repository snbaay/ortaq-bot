package kz.homebot;

import kz.homebot.domain.*;
import kz.homebot.repository.*;
import kz.homebot.service.MonthlyReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(MonthlyReportService.class)
class MonthlyReportServiceTest {
    @Autowired MonthlyReportService reports;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;
    @Autowired ExpenseRepository expenses;
    @Autowired ExpenseShareRepository shares;

    @Test void includesMonthBoundariesAndExcludesRepayments() {
        Household home = households.save(new Household(-81L, "Home"));
        Member asan = members.save(new Member(home, 1L, "Asan"));
        Member berik = members.save(new Member(home, 2L, "Berik"));
        add(home, asan, berik, "100.00", LocalDateTime.of(2026, 9, 1, 0, 0), false);
        add(home, asan, berik, "200.00", LocalDateTime.of(2026, 9, 30, 23, 59, 59), false);
        add(home, asan, berik, "300.00", LocalDateTime.of(2026, 10, 1, 0, 0), false);
        add(home, berik, asan, "50.00", LocalDateTime.of(2026, 9, 15, 12, 0), true);
        var report = reports.report(home, YearMonth.of(2026, 9));
        assertThat(report.total()).isEqualByComparingTo("300.00");
        assertThat(report.top()).hasSize(2);
        assertThat(report.members()).filteredOn(t -> t.member().getId().equals(asan.getId()))
                .singleElement().satisfies(t -> {
                    assertThat(t.paid()).isEqualByComparingTo("300.00");
                    assertThat(t.consumed()).isEqualByComparingTo("0.00");
                });
    }

    private void add(Household home, Member payer, Member consumer, String amount,
                     LocalDateTime date, boolean settlement) {
        Expense expense = expenses.save(new Expense(home, payer, new BigDecimal(amount), "Item", date, false, settlement));
        shares.save(new ExpenseShare(expense, consumer, new BigDecimal(amount)));
    }
}
