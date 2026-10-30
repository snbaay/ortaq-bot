package kz.homebot.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure Java class (no Spring, no JPA).
 * Greedy debt simplification algorithm:
 * 1. Find the biggest debtor (most negative balance) and biggest creditor (most positive).
 * 2. Transfer min(|debtor's debt|, creditor's credit) from debtor to creditor.
 * 3. Repeat until all balances are zero (within 0.01 precision).
 * Ties broken by smaller member id (deterministic).
 */
public class DebtCalculator {

    public record Transfer(Long fromMemberId, Long toMemberId, BigDecimal amount) {}

    /**
     * @param balances map of memberId -> balance (positive = creditor, negative = debtor)
     * @return minimal list of transfers to settle all debts
     */
    public List<Transfer> calculate(Map<Long, BigDecimal> balances) {
        if (balances == null || balances.isEmpty()) {
            return List.of();
        }

        // Work with a mutable copy, scale everything to 2 decimals
        // Use TreeMap for deterministic ordering by memberId (ascending)
        Map<Long, BigDecimal> bal = new TreeMap<>();
        for (Map.Entry<Long, BigDecimal> e : balances.entrySet()) {
            BigDecimal v = e.getValue().setScale(2, RoundingMode.HALF_UP);
            bal.put(e.getKey(), v);
        }

        List<Transfer> transfers = new ArrayList<>();
        BigDecimal epsilon = new BigDecimal("0.005");

        while (true) {
            // Find biggest debtor (most negative) — ties: smallest id wins (TreeMap already sorted)
            Long debtorId = null;
            BigDecimal maxDebt = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal> e : bal.entrySet()) {
                BigDecimal v = e.getValue();
                if (v.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal absV = v.negate();
                    if (absV.compareTo(maxDebt) > 0) {
                        maxDebt = absV;
                        debtorId = e.getKey();
                    }
                }
            }

            // Find biggest creditor (most positive) — ties: smallest id wins
            Long creditorId = null;
            BigDecimal maxCredit = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal> e : bal.entrySet()) {
                BigDecimal v = e.getValue();
                if (v.compareTo(BigDecimal.ZERO) > 0) {
                    if (v.compareTo(maxCredit) > 0) {
                        maxCredit = v;
                        creditorId = e.getKey();
                    }
                }
            }

            if (debtorId == null || creditorId == null) {
                break; // all settled
            }

            // Transfer the minimum of debt and credit
            BigDecimal transferAmount = maxDebt.min(maxCredit).setScale(2, RoundingMode.HALF_UP);
            if (transferAmount.compareTo(epsilon) < 0) {
                break; // negligible remainder
            }

            transfers.add(new Transfer(debtorId, creditorId, transferAmount));

            // Update balances
            bal.put(debtorId, bal.get(debtorId).add(transferAmount));
            bal.put(creditorId, bal.get(creditorId).subtract(transferAmount));
        }

        return transfers;
    }
}
