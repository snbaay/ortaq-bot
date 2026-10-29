package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeekdayParserTest {
    static WeekdayParser parser() {
        return new WeekdayParser(Map.of(
                DayOfWeek.MONDAY, "дүйсенбі,понедельник,пн,mon",
                DayOfWeek.TUESDAY, "сейсенбі,вторник,вт,tue",
                DayOfWeek.WEDNESDAY, "сәрсенбі,среда,ср,wed",
                DayOfWeek.THURSDAY, "бейсенбі,четверг,чт,thu",
                DayOfWeek.FRIDAY, "жұма,пятница,пт,fri",
                DayOfWeek.SATURDAY, "сенбі,суббота,сб,sat",
                DayOfWeek.SUNDAY, "жексенбі,воскресенье,вс,sun"));
    }

    @Test void kazakhNames() {
        var parser = parser();
        assertThat(parser.parse("дүйсенбі")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(parser.parse("жексенбі")).isEqualTo(DayOfWeek.SUNDAY);
    }
    @Test void russianFullAndShort() {
        var parser = parser();
        assertThat(parser.parse("ПОНЕДЕЛЬНИК")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(parser.parse("вт")).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(parser.parse("ср")).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(parser.parse("чт")).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(parser.parse("пт")).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(parser.parse("сб")).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(parser.parse("вс")).isEqualTo(DayOfWeek.SUNDAY);
    }
    @Test void englishShortAndNumbers() {
        var parser = parser();
        for (int i = 1; i <= 7; i++) assertThat(parser.parse(Integer.toString(i))).isEqualTo(DayOfWeek.of(i));
        assertThat(parser.parse("MON")).isEqualTo(DayOfWeek.MONDAY);
        assertThat(parser.parse("sun")).isEqualTo(DayOfWeek.SUNDAY);
    }
    @Test void unknownRejected() {
        assertThatThrownBy(() -> parser().parse("yesterday")).isInstanceOf(IllegalArgumentException.class);
    }
}
