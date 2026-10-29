package kz.homebot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import kz.homebot.config.Limits;

/**
 * Pure Java class. Parses a raw text string into an amount + description.
 * Accepts "." and "," as decimal separator, spaces inside number.
 * Valid examples: "630 water", "2 500,50 bread".
 */
public class ExpenseParser {

    public record ParseResult(BigDecimal amount, String description) {}

    /**
     * Parse text that starts with (optional "/shygyn ") then amount then description.
     * Returns ParseResult or throws IllegalArgumentException with a user-friendly message.
     */
    public ParseResult parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty");
        }

        String s = text.trim();

        // Strip /shygyn prefix (with optional @BotName suffix)
        if (s.startsWith("/shygyn")) {
            // Remove "/shygyn" and optional "@..." suffix up to the first space
            int spaceIdx = s.indexOf(' ');
            if (spaceIdx < 0) {
                throw new IllegalArgumentException("no_description");
            }
            s = s.substring(spaceIdx).trim();
        }

        if (s.isEmpty()) {
            throw new IllegalArgumentException("no_description");
        }

        // Amount token: first "word" which may contain digits, spaces (thousand sep), dots, commas
        // We greedily consume chars that look like part of a number: digits, '.', ',', space
        // but stop when we hit a letter (start of description)
        // Strategy: collect leading number chars, then rest is description
        int i = 0;
        while (i < s.length() && isNumberChar(s.charAt(i))) {
            i++;
        }

        // Trim trailing spaces from number part
        String numberPart = s.substring(0, i).trim();
        String descPart = s.substring(i).trim();

        if (numberPart.isEmpty()) {
            throw new IllegalArgumentException("no_amount");
        }
        if (descPart.isEmpty()) {
            throw new IllegalArgumentException("no_description");
        }

        if (numberPart.contains(".") && numberPart.contains(",")) {
            throw new IllegalArgumentException("both_separators");
        }
        if (descPart.length() > Limits.EXPENSE_DESCRIPTION) {
            throw new IllegalArgumentException("description_too_long");
        }

        // Normalize number: remove spaces (thousand separators), replace comma with dot
        String normalized = numberPart.replace(" ", "").replace(",", ".");

        BigDecimal amount;
        try {
            amount = new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad_format");
        }

        // Validate: must be positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("not_positive");
        }

        BigDecimal maxAmount = Limits.MAX_AMOUNT;
        if (amount.compareTo(maxAmount) > 0) {
            throw new IllegalArgumentException("amount_too_large");
        }

        // Validate: max 2 decimal places
        // setScale(2, UNNECESSARY) throws ArithmeticException if scale > 2
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("too_many_decimals");
        }

        return new ParseResult(amount, descPart);
    }

    private boolean isNumberChar(char c) {
        return Character.isDigit(c) || c == '.' || c == ',' || c == ' ';
    }
}
