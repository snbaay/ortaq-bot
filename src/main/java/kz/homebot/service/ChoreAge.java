package kz.homebot.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ChoreAge {
    public long daysAgo(LocalDate today, LocalDate choreDate) {
        return Math.max(0, ChronoUnit.DAYS.between(choreDate, today));
    }
}
