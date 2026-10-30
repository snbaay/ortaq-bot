package kz.homebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DebtCalculatorTest {

    private DebtCalculator calc;

    @BeforeEach
    void setUp() { calc = new DebtCalculator(); }

    // Helper: sum of all positive balances = sum of all transfers
    private BigDecimal totalTransfers(List<DebtCalculator.Transfer> transfers) {
        return transfers.stream()
                .map(DebtCalculator.Transfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalPositiveBalances(Map<Long, BigDecimal> balances) {
        return balances.values().stream()
                .filter(b -> b.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void twoPeople() {
        // A paid 100, B owes 100 -> B pays A 100
        var balances = Map.of(1L, new BigDecimal("100.00"), 2L, new BigDecimal("-100.00"));
        var transfers = calc.calculate(balances);
        assertEquals(1, transfers.size());
        assertEquals(2L, transfers.get(0).fromMemberId());
        assertEquals(1L, transfers.get(0).toMemberId());
        assertEquals(new BigDecimal("100.00"), transfers.get(0).amount());
    }

    @Test
    void threePeopleOnePayedForAll() {
        // A paid for all: balance A=+200, B=-100, C=-100
        var balances = Map.of(1L, new BigDecimal("200.00"),
                              2L, new BigDecimal("-100.00"),
                              3L, new BigDecimal("-100.00"));
        var transfers = calc.calculate(balances);
        assertEquals(2, transfers.size());
        // Both B and C pay A
        assertEquals(new BigDecimal("200.00"), totalTransfers(transfers));
        assertEquals(totalPositiveBalances(balances), totalTransfers(transfers));
        transfers.forEach(t -> assertEquals(1L, t.toMemberId()));
    }

    @Test
    void allZeroReturnsEmpty() {
        var balances = Map.of(1L, BigDecimal.ZERO, 2L, BigDecimal.ZERO);
        assertTrue(calc.calculate(balances).isEmpty());
    }

    @Test
    void emptyMapReturnsEmpty() {
        assertTrue(calc.calculate(Map.of()).isEmpty());
    }

    @Test
    void nullReturnsEmpty() {
        assertTrue(calc.calculate(null).isEmpty());
    }

    @Test
    void split1TiynAmong3() {
        // A paid 0.01, B and C each owe 0.00 or 0.01 - depends on splitter
        // Manually: total positive = 0.01, total negative = 0.01 distributed as 0.01 and 0.00
        // A=+0.01, B=-0.01, C=0.00
        var balances = Map.of(1L, new BigDecimal("0.01"),
                              2L, new BigDecimal("-0.01"),
                              3L, BigDecimal.ZERO.setScale(2));
        var transfers = calc.calculate(balances);
        assertEquals(1, transfers.size());
        assertEquals(new BigDecimal("0.01"), transfers.get(0).amount());
        assertEquals(totalPositiveBalances(balances), totalTransfers(transfers));
    }

    @Test
    void chainAowesBandBowesC() {
        // A=-100, B=0 (owes C 100 and A owes B 100 cancel out), C=+100
        // After simplification: A pays C directly
        var balances = Map.of(1L, new BigDecimal("-100.00"),
                              2L, BigDecimal.ZERO.setScale(2),
                              3L, new BigDecimal("100.00"));
        var transfers = calc.calculate(balances);
        assertEquals(1, transfers.size());
        assertEquals(1L, transfers.get(0).fromMemberId());
        assertEquals(3L, transfers.get(0).toMemberId());
        assertEquals(new BigDecimal("100.00"), transfers.get(0).amount());
    }

    @Test
    void twoCreditorsOneDebtor() {
        // A=+60, B=+40, C=-100
        var balances = Map.of(1L, new BigDecimal("60.00"),
                              2L, new BigDecimal("40.00"),
                              3L, new BigDecimal("-100.00"));
        var transfers = calc.calculate(balances);
        // C pays both A and B
        assertEquals(new BigDecimal("100.00"), totalTransfers(transfers));
        transfers.forEach(t -> assertEquals(3L, t.fromMemberId()));
        assertEquals(totalPositiveBalances(balances), totalTransfers(transfers));
    }

    @Test
    void deterministicTieBreak() {
        // Both members 2 and 3 have -50.00 -> the one with smaller id (2) should appear first
        var balances = Map.of(1L, new BigDecimal("100.00"),
                              2L, new BigDecimal("-50.00"),
                              3L, new BigDecimal("-50.00"));
        var transfers = calc.calculate(balances);
        assertEquals(2, transfers.size());
        // First debtor processed: id=2 (smaller)
        assertEquals(2L, transfers.get(0).fromMemberId());
        assertEquals(3L, transfers.get(1).fromMemberId());
    }

    @Test
    void totalTransfersEqualsPositiveBalances() {
        var balances = Map.of(1L, new BigDecimal("300.00"),
                              2L, new BigDecimal("-100.00"),
                              3L, new BigDecimal("-150.00"),
                              4L, new BigDecimal("-50.00"));
        var transfers = calc.calculate(balances);
        assertEquals(totalPositiveBalances(balances), totalTransfers(transfers));
    }
}
