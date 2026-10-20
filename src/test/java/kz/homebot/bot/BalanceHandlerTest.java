package kz.homebot.bot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.BalanceService;
import kz.homebot.service.DebtCalculator;
import kz.homebot.service.MessageService;
import kz.homebot.service.MoneyFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceHandlerTest {

    @Mock
    private BalanceService balanceService;

    @Mock
    private HouseholdRepository householdRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MessageService messageService;

    private BalanceHandler handler;

    @BeforeEach
    void setUp() {
        handler = new BalanceHandler(
                balanceService,
                new DebtCalculator(),
                householdRepository,
                memberRepository,
                messageService,
                new MoneyFormatter()
        );
    }

    @Test
    void balansShowsMembersSorted() {
        List<String> replies = new ArrayList<>();
        Household household = new Household(-100L, "Flat", LocalDateTime.now());
        Member asan = new Member(household, 1L, "Асан", LocalDateTime.now());
        Member berik = new Member(household, 2L, "Берік", LocalDateTime.now());

        when(householdRepository.findByTelegramChatId(-100L)).thenReturn(Optional.of(household));
        when(messageService.getMessage("balans.header")).thenReturn("📊 Баланс:");
        when(balanceService.getMemberBalances(household)).thenReturn(List.of(
                new BalanceService.MemberBalance(asan, new BigDecimal("1200.00")),
                new BalanceService.MemberBalance(berik, new BigDecimal("-600.00"))
        ));

        CommandContext ctx = new CommandContext(
                -100L, "group", new Sender(1L, "Асан", null), "balans", "", List.of(), replies::add);

        handler.handle(ctx);

        assertThat(replies).hasSize(1);
        String reply = replies.get(0);
        assertThat(reply).contains("📊 Баланс:");
        assertThat(reply).contains("Асан: +1 200 ₸");
        assertThat(reply).contains("Берік: -600 ₸");
    }

    @Test
    void qaryzShowsTransfers() {
        List<String> replies = new ArrayList<>();
        Household household = new Household(-100L, "Flat", LocalDateTime.now());
        Member asan = new Member(household, 1L, "Асан", LocalDateTime.now());
        asan.setId(10L);
        Member berik = new Member(household, 2L, "Берік", LocalDateTime.now());
        berik.setId(20L);

        when(householdRepository.findByTelegramChatId(-100L)).thenReturn(Optional.of(household));
        when(balanceService.getBalances(household)).thenReturn(Map.of(
                10L, new BigDecimal("600.00"),
                20L, new BigDecimal("-600.00")
        ));
        when(memberRepository.findByHousehold(household)).thenReturn(List.of(asan, berik));
        when(messageService.getMessage("qaryz.header")).thenReturn("💸 Кім кімге қарыз:");

        CommandContext ctx = new CommandContext(
                -100L, "group", new Sender(1L, "Асан", null), "qaryz", "", List.of(), replies::add);

        handler.handle(ctx);

        assertThat(replies).hasSize(1);
        String reply = replies.get(0);
        assertThat(reply).contains("💸 Кім кімге қарыз:");
        assertThat(reply).contains("Берік → Асан: 600 ₸");
    }

    @Test
    void qaryzAllEvenWhenNoDebts() {
        List<String> replies = new ArrayList<>();
        Household household = new Household(-100L, "Flat", LocalDateTime.now());

        when(householdRepository.findByTelegramChatId(-100L)).thenReturn(Optional.of(household));
        when(balanceService.getBalances(household)).thenReturn(Map.of());
        when(messageService.getMessage("debts.allEven")).thenReturn("Қарыз жоқ, бәрі тең 🎉");

        CommandContext ctx = new CommandContext(
                -100L, "group", new Sender(1L, "Асан", null), "qaryz", "", List.of(), replies::add);

        handler.handle(ctx);

        assertThat(replies).containsExactly("Қарыз жоқ, бәрі тең 🎉");
    }
}
