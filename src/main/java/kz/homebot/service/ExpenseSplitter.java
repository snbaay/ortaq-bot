package kz.homebot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure Java class (no Spring, no JPA).
 * Splits an amount equally among N people.
 * Leftover tiyn (0.01) distributed one-by-one to first members.
 */
public class ExpenseSplitter {

    /**
     * Split amount among peopleCount people.
     * Returns a list of size peopleCount whose sum equals amount exactly.
     */
    public List<BigDecimal> split(BigDecimal amount, int peopleCount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (peopleCount <= 0) {
            throw new IllegalArgumentException("People count must be positive");
        }

        // Scale to 2 decimals
        BigDecimal scaled = amount.setScale(2, RoundingMode.UNNECESSARY);

        // Base share: floor division in tiyn (cents)
        // Work in tiyn (integer) to avoid rounding issues
        long totalTiyn = scaled.movePointRight(2).longValueExact();
        long base = totalTiyn / peopleCount;
        long remainder = totalTiyn % peopleCount;

        List<BigDecimal> shares = new ArrayList<>(peopleCount);
        for (int i = 0; i < peopleCount; i++) {
            long tiyn = base + (i < remainder ? 1 : 0);
            shares.add(BigDecimal.valueOf(tiyn).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY));
        }
        return shares;
    }
}
