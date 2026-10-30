package kz.homebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseSplitterTest {

    private ExpenseSplitter splitter;

    @BeforeEach
    void setUp() { splitter = new ExpenseSplitter(); }

    private BigDecimal sum(List<BigDecimal> list) {
        return list.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void evenSplit() {
        List<BigDecimal> shares = splitter.split(new BigDecimal("90.00"), 3);
        assertEquals(3, shares.size());
        shares.forEach(s -> assertEquals(new BigDecimal("30.00"), s));
        assertEquals(new BigDecimal("90.00"), sum(shares));
    }

    @Test
    void split100By3() {
        // 100.00 / 3 -> 33.34, 33.33, 33.33
        List<BigDecimal> shares = splitter.split(new BigDecimal("100.00"), 3);
        assertEquals(new BigDecimal("33.34"), shares.get(0));
        assertEquals(new BigDecimal("33.33"), shares.get(1));
        assertEquals(new BigDecimal("33.33"), shares.get(2));
        assertEquals(new BigDecimal("100.00"), sum(shares));
    }

    @Test
    void split1TiynBy3() {
        // 0.01 / 3 -> 0.01, 0.00, 0.00
        List<BigDecimal> shares = splitter.split(new BigDecimal("0.01"), 3);
        assertEquals(new BigDecimal("0.01"), shares.get(0));
        assertEquals(new BigDecimal("0.00"), shares.get(1));
        assertEquals(new BigDecimal("0.00"), shares.get(2));
        assertEquals(new BigDecimal("0.01"), sum(shares));
    }

    @Test
    void onePerson() {
        List<BigDecimal> shares = splitter.split(new BigDecimal("50.00"), 1);
        assertEquals(1, shares.size());
        assertEquals(new BigDecimal("50.00"), shares.get(0));
        assertEquals(new BigDecimal("50.00"), sum(shares));
    }

    @Test
    void twoPeopleOddTiyn() {
        // 10.01 / 2 -> 5.01, 5.00
        List<BigDecimal> shares = splitter.split(new BigDecimal("10.01"), 2);
        assertEquals(new BigDecimal("5.01"), shares.get(0));
        assertEquals(new BigDecimal("5.00"), shares.get(1));
        assertEquals(new BigDecimal("10.01"), sum(shares));
    }

    @Test
    void rejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split(BigDecimal.ZERO, 3));
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split(new BigDecimal("-5.00"), 3));
    }

    @Test
    void rejectsNullAmount() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split(null, 3));
    }

    @Test
    void rejectsZeroPeople() {
        assertThrows(IllegalArgumentException.class, () -> splitter.split(new BigDecimal("10.00"), 0));
    }
}

