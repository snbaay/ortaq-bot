package kz.homebot;

import kz.homebot.bot.CommandContext;
import kz.homebot.bot.HistoryHandler;
import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.bot.Sender;
import kz.homebot.domain.Member;
import kz.homebot.repository.ExpenseRepository;
import kz.homebot.service.DigestJob;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MemberRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration")
class FinancialCommandReliabilityTest {
    @MockBean HomeTelegramBot bot;
    @MockBean DigestJob digestJob;
    @Autowired MemberRegistrationService registration;
    @Autowired ExpenseService expenses;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired HistoryHandler history;

    @Test
    void historyRendersPayerOutsideServiceTransactionAndOtherHouseholdCannotDelete() {
        Member payer = registration.registerMember(-721L, "Home", 721L, "Asan", null);
        var expense = expenses.addExpense(payer.getHousehold(), payer, new BigDecimal("125.00"), "Нан").expense();
        Member outsider = registration.registerMember(-722L, "Other", 722L, "Dana", null);
        List<String> replies = new ArrayList<>();

        history.handle(new CommandContext(-721L, "group", new Sender(721L, "Asan", null),
                "tarih", "", List.of(), replies::add));
        history.handle(new CommandContext(-722L, "group", new Sender(722L, "Dana", null),
                "oshiru", expense.getId().toString(), List.of(), replies::add));

        assertThat(replies.get(0)).contains("Asan", "Нан");
        assertThat(replies.get(1)).contains("табылмады");
        assertThat(expenseRepository.findById(expense.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(outsider.getHousehold().getId()).isNotEqualTo(payer.getHousehold().getId());
    }

    @Test
    void serviceRejectsCrossHouseholdPayerAndInvalidAmountWithoutWriting() {
        Member first = registration.registerMember(-723L, "Home", 723L, "Ali", null);
        Member second = registration.registerMember(-724L, "Other", 724L, "Dana", null);
        long before = expenseRepository.count();

        assertThatThrownBy(() -> expenses.addExpense(first.getHousehold(), second,
                new BigDecimal("100.00"), "Wrong payer")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> expenses.addExpense(first.getHousehold(), first,
                new BigDecimal("0.00"), "Zero")).isInstanceOf(IllegalArgumentException.class);
        assertThat(expenseRepository.count()).isEqualTo(before);
    }
}
