package kz.homebot;

import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration")
@Transactional
class FullFlowTest {
    @MockBean HomeTelegramBot bot;
    @MockBean DigestJob digestJob;
    @Autowired MemberRegistrationService registration;
    @Autowired ExpenseService expenses;
    @Autowired BalanceService balances;
    @Autowired DebtCalculator debts;
    @Autowired CookingScheduleService cooking;
    @Autowired PaymentCountdown countdown;

    @Test void householdFlow() {
        Member asan = registration.registerMember(-91L, "Home", 1L, "Asan", null);
        Member berik = registration.registerMember(-91L, "Home", 2L, "Berik", null);
        Member dana = registration.registerMember(-91L, "Home", 3L, "Dana", null);
        Household home = asan.getHousehold();
        expenses.addExpense(home, asan, new BigDecimal("900.00"), "Food");
        expenses.addExpense(home, berik, new BigDecimal("300.00"), "Pizza", List.of(asan));
        assertThat(balances.getBalances(home).get(asan.getId())).isEqualByComparingTo("450.00");
        assertThat(debts.calculate(balances.getBalances(home))).hasSize(2);

        var repayment = expenses.recordRepayment(home, dana, asan, new BigDecimal("300.00"), "Repayment");
        assertThat(balances.getBalances(home).get(dana.getId())).isEqualByComparingTo("0.00");
        assertThat(debts.calculate(balances.getBalances(home))).hasSize(1);
        assertThat(expenses.deleteEntryById(home, dana, repayment.getId()))
                .isEqualTo(ExpenseService.DeleteResult.SUCCESS);
        assertThat(balances.getBalances(home).get(dana.getId())).isEqualByComparingTo("-300.00");

        LocalDate monday = LocalDate.of(2026, 11, 2);
        cooking.setSchedule(home, "mon Asan Berik\ntue Dana\nsun -");
        assertThat(cooking.effectiveCooks(home, monday)).extracting(Member::getId)
                .containsExactly(asan.getId(), berik.getId());
        assertThat(cooking.effectiveCooks(home, monday.plusDays(1))).extracting(Member::getId)
                .containsExactly(dana.getId());
        cooking.handOver(home, monday, asan, "Dana");
        assertThat(cooking.effectiveCooks(home, monday)).extracting(Member::getId)
                .containsExactly(berik.getId(), dana.getId());
        assertThat(cooking.effectiveCooks(home, monday.plusWeeks(1))).extracting(Member::getId)
                .containsExactly(asan.getId(), berik.getId());
        assertThat(cooking.effectiveCooks(home, monday.plusDays(6))).isEmpty();
        assertThat(countdown.daysLeft(LocalDate.of(2026, 10, 29), 31)).isEqualTo(2);
    }
}
