package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepaymentHandlerTest {
    @Mock ExpenseService expenses;
    @Mock HouseholdRepository households;
    @Mock MemberRepository members;
    @Mock MessageService messages;
    @Mock BalanceHandler balances;
    private RepaymentHandler handler;
    private Household home;
    private Member ali;
    private Member alina;

    @BeforeEach
    void setUp() {
        handler = new RepaymentHandler(expenses, households, members, messages, new MoneyFormatter(), balances);
        home = new Household(-501L, "Home");
        home.setId(501L);
        ali = new Member(home, 1L, "Ali");
        ali.setId(1L);
        alina = new Member(home, 2L, "Alina");
        alina.setId(2L);
    }

    @Test
    void rejectsInvalidAmountWithoutLedgerWrite() {
        stubMembers();
        when(messages.getMessage("tolem.usage")).thenReturn("Usage");
        List<String> replies = new ArrayList<>();

        handler.handle(context("Alina -5", replies));

        assertThat(replies).containsExactly("Usage");
        verify(expenses, never()).recordRepayment(any(), any(), any(), any(), any());
    }

    @Test
    void ambiguousPrefixIsNotMistakenForSelf() {
        stubMembers();
        when(messages.getMessage("tolem.ambiguous", "Ali, Alina")).thenReturn("Ambiguous");
        List<String> replies = new ArrayList<>();

        handler.handle(context("Al 100", replies));

        verify(expenses, never()).recordRepayment(any(), any(), any(), any(), any());
        assertThat(replies).containsExactly("Ambiguous");
    }

    private CommandContext context(String args, List<String> replies) {
        return new CommandContext(-501L, "group", new Sender(1L, "Ali", null),
                "tolem", args, List.of(), replies::add);
    }

    private void stubMembers() {
        when(households.findByTelegramChatId(-501L)).thenReturn(Optional.of(home));
        when(members.findByHouseholdAndTelegramUserId(home, 1L)).thenReturn(Optional.of(ali));
        when(members.findByHousehold(home)).thenReturn(List.of(ali, alina));
    }
}
