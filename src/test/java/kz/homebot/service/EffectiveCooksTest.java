package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EffectiveCooksTest {
    private final EffectiveCooks cooks = new EffectiveCooks();
    @Test void noSwap() { assertThat(cooks.apply(List.of(1L, 2L), List.of())).containsExactly(1L, 2L); }
    @Test void withSwap() {
        assertThat(cooks.apply(List.of(1L, 2L), List.of(new EffectiveCooks.Swap(1L, 3L))))
                .containsExactly(2L, 3L);
    }
    @Test void dayOff() { assertThat(cooks.apply(List.of(), List.of())).isEmpty(); }
    @Test void duplicateReplacementIsShownOnce() {
        assertThat(cooks.apply(List.of(1L, 2L), List.of(new EffectiveCooks.Swap(1L, 2L))))
                .containsExactly(2L);
    }
}
