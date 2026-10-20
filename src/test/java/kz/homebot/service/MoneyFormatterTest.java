package kz.homebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyFormatterTest {

    private MoneyFormatter fmt;

    @BeforeEach
    void setUp() { fmt = new MoneyFormatter(); }

    @Test
    void wholeNumberDropsDecimal() {
        assertEquals("1 200 ₸", fmt.format(new BigDecimal("1200.00")));
    }

    @Test
    void withFractional() {
        assertEquals("33,34 ₸", fmt.format(new BigDecimal("33.34")));
    }

    @Test
    void small() {
        assertEquals("0,50 ₸", fmt.format(new BigDecimal("0.50")));
    }

    @Test
    void largeAmount() {
        assertEquals("1 000 000 ₸", fmt.format(new BigDecimal("1000000.00")));
    }

    @Test
    void zeroTenge() {
        assertEquals("0 ₸", fmt.format(BigDecimal.ZERO));
    }
}
