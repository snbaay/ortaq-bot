package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class ChoreAgeTest {
    private final ChoreAge age = new ChoreAge();
    @Test void sameDay() { assertThat(age.daysAgo(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 29))).isZero(); }
    @Test void fiveDays() { assertThat(age.daysAgo(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 24))).isEqualTo(5); }
}
