package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ExpenseParser;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseHandlerTest {

    @Mock
    private ExpenseService expenseService;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MessageService messageService;

    private ExpenseHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExpenseHandler(
                new ExpenseParser(),
                expenseService,
                householdRepository,
                memberRepository,
                messageService,
                new MoneyFormatter()
        );
    }

    @Test
    void rejectsInPrivateChat() {
        List<String> replies = new ArrayList<>();
        when(messageService.getMessage("error.groupOnly")).thenReturn("Бұл команда тек топта жұмыс істейді.");

        CommandContext ctx = new CommandContext(
                123L, "private", new Sender(1L, "User", null), "shygyn", "630 су", List.of(), replies::add);

        handler.handle(ctx);

        assertThat(replies).containsExactly("Бұл команда тек топта жұмыс істейді.");
        verify(expenseService, never()).addExpense(any(), any(), any(), any());
    }

    @Test
    void createsExpenseInGroup() {
        List<String> replies = new ArrayList<>();
        Household household = new Household(-100L, "Flat", LocalDateTime.now());
        Member member1 = new Member(household, 1L, "Askar", LocalDateTime.now());
        Member member2 = new Member(household, 2L, "Berik", LocalDateTime.now());

        when(householdRepository.findByTelegramChatId(-100L)).thenReturn(Optional.of(household));
        when(memberRepository.findByHouseholdAndTelegramUserId(household, 1L)).thenReturn(Optional.of(member1));

        when(expenseService.addExpense(household, member1, new BigDecimal("630.00"), "су"))
                .thenReturn(new ExpenseService.ExpenseResult(null, List.of(member1, member2), List.of(new BigDecimal("315.00"), new BigDecimal("315.00"))));

        when(messageService.getMessage(eq("shygyn.added"), eq("630 ₸"), eq("су"), eq("Askar"), any()))
                .thenReturn("Шығын қосылды!");

        CommandContext ctx = new CommandContext(
                -100L, "group", new Sender(1L, "Askar", null), "shygyn", "630 су", List.of(), replies::add);

        handler.handle(ctx);

        assertThat(replies).containsExactly("Шығын қосылды!");
    }

    @Test
    void handlePlainExpenseInGroup() {
        List<String> replies = new ArrayList<>();
        Household household = new Household(-100L, "Flat", LocalDateTime.now());
        Member member = new Member(household, 1L, "Askar", LocalDateTime.now());

        when(householdRepository.findByTelegramChatId(-100L)).thenReturn(Optional.of(household));
        when(memberRepository.findByHouseholdAndTelegramUserId(household, 1L)).thenReturn(Optional.of(member));

        when(expenseService.addExpense(household, member, new BigDecimal("2500.50"), "хлеб"))
                .thenReturn(new ExpenseService.ExpenseResult(null, List.of(member), List.of(new BigDecimal("2500.50"))));

        when(messageService.getMessage(eq("shygyn.added"), eq("2 500,50 ₸"), eq("хлеб"), eq("Askar"), any()))
                .thenReturn("Шығын қосылды!");

        CommandContext ctx = new CommandContext(
                -100L, "group", new Sender(1L, "Askar", null), "", "", List.of(), replies::add);

        handler.handlePlainExpense(ctx, "2 500,50 хлеб");

        assertThat(replies).containsExactly("Шығын қосылды!");
    }

    @Test
    void mentionSelectsMemberAndPayer() {
        Household household = new Household(-100L, "Flat");
        household.setId(1L);
        Member payer = new Member(household, 1L, "Askar");
        payer.setId(1L);
        Member berik = new Member(household, 2L, "Berik");
        berik.setId(2L);
        berik.setTelegramUsername("berik");
        String raw = "/shygyn 3000 pizza @berik";
        MessageEntity mention = new MessageEntity("mention", raw.indexOf("@berik"), 6);
        when(householdRepository.findByTelegramChatId(-100L)).thenReturn(Optional.of(household));
        when(memberRepository.findByHouseholdAndTelegramUserId(household, 1L)).thenReturn(Optional.of(payer));
        when(memberRepository.findByHousehold(household)).thenReturn(List.of(payer, berik));
        when(expenseService.addExpense(household, payer, new BigDecimal("3000.00"), "pizza", List.of(berik)))
                .thenReturn(new ExpenseService.ExpenseResult(null, List.of(payer, berik),
                        List.of(new BigDecimal("1500.00"), new BigDecimal("1500.00"))));
        when(messageService.getMessage(eq("shygyn.added"), any(), any(), any(), any())).thenReturn("Added");
        List<String> replies = new ArrayList<>();
        handler.handle(new CommandContext(-100L, "group", new Sender(1L, "Askar", null), "shygyn",
                "3000 pizza @berik", List.of(mention), replies::add, raw));
        assertThat(replies).containsExactly("Added");
    }
}
