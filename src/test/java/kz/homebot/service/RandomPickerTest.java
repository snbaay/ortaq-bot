package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomPickerTest {
    @Test void seededPickIsRepeatable() {
        var first = new RandomPicker(new Random(42));
        var second = new RandomPicker(new Random(42));
        List<String> options = List.of("a", "b", "c");
        for (int i = 0; i < 10; i++) assertThat(first.pick(options)).isEqualTo(second.pick(options));
    }
    @Test void seededCoinIsRepeatable() {
        var first = new RandomPicker(new Random(7));
        var second = new RandomPicker(new Random(7));
        for (int i = 0; i < 10; i++) assertThat(first.coin()).isEqualTo(second.coin());
    }
    @Test void emptyOptionsRejected() {
        assertThatThrownBy(() -> new RandomPicker(new Random()).pick(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
