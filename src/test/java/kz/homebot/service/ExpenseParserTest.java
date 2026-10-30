package kz.homebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExpenseParserTest {

    private ExpenseParser parser;

    @BeforeEach
    void setUp() { parser = new ExpenseParser(); }

    @Test
    void validDotDecimal() {
        var r = parser.parse("/shygyn 630.50 су");
        assertEquals(new BigDecimal("630.50"), r.amount());
        assertEquals("су", r.description());
    }

    @Test
    void validCommaDecimal() {
        var r = parser.parse("/shygyn 630,50 хлеб");
        assertEquals(new BigDecimal("630.50"), r.amount());
        assertEquals("хлеб", r.description());
    }

    @Test
    void validSpacesInAmount() {
        // "2 500,50 хлеб" — spaces as thousands separators
        var r = parser.parse("2 500,50 хлеб");
        assertEquals(new BigDecimal("2500.50"), r.amount());
        assertEquals("хлеб", r.description());
    }

    @Test
    void validPlainInteger() {
        var r = parser.parse("630 су");
        assertEquals(new BigDecimal("630.00"), r.amount());
        assertEquals("су", r.description());
    }

    @Test
    void validShygynCommand() {
        var r = parser.parse("/shygyn 1000 нан");
        assertEquals(new BigDecimal("1000.00"), r.amount());
        assertEquals("нан", r.description());
    }

    @Test
    void rejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("0 нан"));
    }

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("-100 нан"));
    }

    @Test
    void rejectsThreeDecimals() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("10.123 нан"));
    }

    @Test
    void rejectsBothSeparators() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1.250,50 нан"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("1,250.50 нан"));
    }

    @Test
    void rejectsAboveMax() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("100000001 нан"));
    }

    @Test
    void rejectsNoDescription() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("/shygyn 100"));
    }

    @Test
    void rejectsTooLongDescription() {
        String longDesc = "a".repeat(101);
        assertThrows(IllegalArgumentException.class, () -> parser.parse("500 " + longDesc));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
    }

    @Test
    void descriptionCanHaveSpaces() {
        var r = parser.parse("/shygyn 500 ет пен нан");
        assertEquals(new BigDecimal("500.00"), r.amount());
        assertEquals("ет пен нан", r.description());
    }
}
