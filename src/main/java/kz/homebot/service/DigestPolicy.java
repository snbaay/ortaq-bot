package kz.homebot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class DigestPolicy {
    public boolean shouldSend(LocalDateTime nowAlmaty, LocalDate lastDigestDate) {
        return !nowAlmaty.toLocalTime().isBefore(LocalTime.of(9, 0))
                && !nowAlmaty.toLocalDate().equals(lastDigestDate);
    }
}
