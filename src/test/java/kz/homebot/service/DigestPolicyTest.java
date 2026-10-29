package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class DigestPolicyTest {
    private final DigestPolicy policy = new DigestPolicy();
    private final LocalDate today = LocalDate.of(2026, 10, 29);

    @Test void beforeNine() { assertThat(policy.shouldSend(today.atTime(8, 59), null)).isFalse(); }
    @Test void atNine() { assertThat(policy.shouldSend(today.atTime(9, 0), null)).isTrue(); }
    @Test void afterNine() { assertThat(policy.shouldSend(today.atTime(15, 0), today.minusDays(1))).isTrue(); }
    @Test void alreadySent() { assertThat(policy.shouldSend(today.atTime(10, 0), today)).isFalse(); }
    @Test void nullPrevious() { assertThat(policy.shouldSend(today.atTime(9, 1), null)).isTrue(); }
    @Test void afterMidnight() { assertThat(policy.shouldSend(today.atTime(0, 1), today.minusDays(1))).isFalse(); }
}
