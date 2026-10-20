package kz.homebot.service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Pure Java utility. Formats BigDecimal amounts as Kazakh-style money strings.
 * Rules:
 * - space as thousands separator
 * - comma as decimal separator
 * - drop ",00" suffix (whole tenge)
 * - append " ₸"
 * Examples: 1200.00 -> "1 200 ₸", 33.34 -> "33,34 ₸", 0.50 -> "0,50 ₸"
 */
public class MoneyFormatter {

    public String format(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("amount must not be null");
        }

        BigDecimal scaled = amount.setScale(2, java.math.RoundingMode.HALF_UP);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');

        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        String formatted = df.format(scaled);

        // Drop ",00" if amount is a whole number
        if (formatted.endsWith(",00")) {
            formatted = formatted.substring(0, formatted.length() - 3);
        }

        return formatted + " ₸";
    }
}
