package kz.homebot.service;

import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleTextParserTest {
    private final ScheduleTextParser parser = new ScheduleTextParser(WeekdayParserTest.parser());
    @Test void singleLine() {
        assertThat(parser.parse("дүйсенбі @user1 @user2"))
                .containsEntry(DayOfWeek.MONDAY, "@user1 @user2");
    }
    @Test void multiLineAndDayOff() {
        assertThat(parser.parse("дүйсенбі: @user1 @user2\nсейсенбі: @user3\nжексенбі: -"))
                .containsEntry(DayOfWeek.MONDAY, "@user1 @user2")
                .containsEntry(DayOfWeek.TUESDAY, "@user3")
                .containsEntry(DayOfWeek.SUNDAY, "");
    }
    @Test void extraSpaces() {
        assertThat(parser.parse("  пн  :   @user1   @user2  \n  "))
                .containsEntry(DayOfWeek.MONDAY, "@user1 @user2");
    }
    @Test void unknownDayAndDuplicateRejected() {
        assertThatThrownBy(() -> parser.parse("tomorrow @user1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("пн @user1\nmon @user2")).isInstanceOf(IllegalArgumentException.class);
    }
}
