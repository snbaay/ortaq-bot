package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class NextCookPickerTest {
    private final NextCookPicker picker = new NextCookPicker();

    @Test void firstDay() { assertThat(picker.next(List.of(3L, 1L, 2L), null)).isEqualTo(1L); }
    @Test void normalStep() { assertThat(picker.next(List.of(1L, 2L, 3L), 1L)).isEqualTo(2L); }
    @Test void wraps() { assertThat(picker.next(List.of(1L, 2L, 3L), 3L)).isEqualTo(1L); }
    @Test void listGrew() { assertThat(picker.next(List.of(1L, 2L, 3L), 2L)).isEqualTo(3L); }
    @Test void previousLeft() { assertThat(picker.next(List.of(1L, 3L), 2L)).isEqualTo(1L); }
}
