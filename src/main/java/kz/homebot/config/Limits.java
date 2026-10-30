package kz.homebot.config;

import java.math.BigDecimal;

public final class Limits {
    private Limits() {}
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    public static final int EXPENSE_DESCRIPTION = 100;
    public static final int SHOPPING_ITEM = 60;
    public static final int SHOPPING_OPEN = 50;
    public static final int NOTE = 300;
    public static final int CHORE = 100;
    public static final int DEADLINE_TITLE = 40;
    public static final int DEADLINES = 20;
    public static final int RANDOM_OPTIONS = 10;
    public static final int TELEGRAM_MESSAGE = 4096;
}
