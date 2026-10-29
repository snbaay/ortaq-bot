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
    @Autowired CookingRotationService cooking;
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

        LocalDate day = LocalDate.of(2026, 10, 29);
        assertThat(cooking.getOrCreateDuty(home, day).getMember().getId()).isEqualTo(asan.getId());
        assertThat(cooking.getOrCreateDuty(home, day.plusDays(1)).getMember().getId()).isEqualTo(berik.getId());
        assertThat(cooking.getOrCreateDuty(home, day.plusDays(2)).getMember().getId()).isEqualTo(dana.getId());
        assertThat(cooking.getOrCreateDuty(home, day.plusDays(3)).getMember().getId()).isEqualTo(asan.getId());
        assertThat(cooking.getOrCreateDuty(home, day.plusDays(4)).getMember().getId()).isEqualTo(berik.getId());
        assertThat(countdown.daysLeft(day, 31)).isEqualTo(2);
    }
}
