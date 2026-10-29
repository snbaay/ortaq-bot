package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class PaymentCountdownTest {
    private final PaymentCountdown countdown = new PaymentCountdown();
    @Test void sameDay() { assertThat(countdown.daysLeft(LocalDate.of(2026, 10, 5), 5)).isZero(); }
    @Test void tomorrow() { assertThat(countdown.daysLeft(LocalDate.of(2026, 10, 4), 5)).isEqualTo(1); }
    @Test void nextMonth() { assertThat(countdown.daysLeft(LocalDate.of(2026, 10, 6), 5)).isEqualTo(30); }
    @Test void thirtyDayMonth() { assertThat(countdown.daysLeft(LocalDate.of(2026, 4, 29), 31)).isEqualTo(1); }
    @Test void february() { assertThat(countdown.daysLeft(LocalDate.of(2026, 2, 27), 31)).isEqualTo(1); }
    @Test void leapFebruary() { assertThat(countdown.daysLeft(LocalDate.of(2028, 2, 28), 29)).isEqualTo(1); }
    @Test void nonLeapFebruary() { assertThat(countdown.daysLeft(LocalDate.of(2027, 2, 28), 29)).isZero(); }
}
