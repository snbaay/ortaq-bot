package kz.homebot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

public class PaymentCountdown {
    public long daysLeft(LocalDate today, int dayOfMonth) {
        if (dayOfMonth < 1 || dayOfMonth > 31) throw new IllegalArgumentException("Day must be 1..31");
        YearMonth month = YearMonth.from(today);
        LocalDate due = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        if (due.isBefore(today)) {
            month = month.plusMonths(1);
            due = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        }
        return ChronoUnit.DAYS.between(today, due);
    }
}
