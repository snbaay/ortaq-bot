package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.CookingScheduleService;
import kz.homebot.service.EffectiveCooks;
import kz.homebot.service.ScheduleTextParser;
import kz.homebot.service.WeekdayParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({CookingScheduleService.class, EffectiveCooks.class, ScheduleTextParser.class,
        CookingScheduleServiceTest.ParserConfig.class})
class CookingScheduleServiceTest {
    @TestConfiguration
    static class ParserConfig {
        @Bean WeekdayParser weekdays() {
            return new WeekdayParser(Map.of(
                    DayOfWeek.MONDAY, "дүйсенбі,пн,mon", DayOfWeek.TUESDAY, "сейсенбі,вт,tue",
                    DayOfWeek.WEDNESDAY, "сәрсенбі,ср,wed", DayOfWeek.THURSDAY, "бейсенбі,чт,thu",
                    DayOfWeek.FRIDAY, "жұма,пт,fri", DayOfWeek.SATURDAY, "сенбі,сб,sat",
                    DayOfWeek.SUNDAY, "жексенбі,вс,sun"));
        }
    }
    @Autowired CookingScheduleService schedules;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test void replacesOnlyMentionedDaysAndSupportsDayOff() {
        Household home = households.save(new Household(-201L, "Home"));
        Member ali = member(home, 1L, "Ali Khan", "ali");
        Member berik = member(home, 2L, "Berik", "berik");
        schedules.setSchedule(home, "дүйсенбі: @ali @berik\nсейсенбі: @berik\nжексенбі: -");
        assertThat(schedules.week(home, LocalDate.of(2026, 11, 2)).get(DayOfWeek.MONDAY))
                .extracting(Member::getId).containsExactly(ali.getId(), berik.getId());
        assertThat(schedules.week(home, LocalDate.of(2026, 11, 2)).get(DayOfWeek.SUNDAY)).isEmpty();
        schedules.setSchedule(home, "пн Ali Khan");
        var week = schedules.week(home, LocalDate.of(2026, 11, 2));
        assertThat(week.get(DayOfWeek.MONDAY)).extracting(Member::getId).containsExactly(ali.getId());
        assertThat(week.get(DayOfWeek.TUESDAY)).extracting(Member::getId).containsExactly(berik.getId());
    }

    @Test void badMultiLineInputChangesNothing() {
        Household home = households.save(new Household(-202L, "Home"));
        Member ali = member(home, 1L, "Ali", "ali");
        member(home, 2L, "Alina", "alina");
        schedules.setSchedule(home, "mon @ali");
        assertThatThrownBy(() -> schedules.setSchedule(home, "mon @alina\ntue @missing"))
                .isInstanceOfSatisfying(CookingScheduleService.ScheduleException.class,
                        e -> assertThat(e.issue()).isEqualTo(CookingScheduleService.Issue.UNKNOWN_MEMBER));
        assertThat(schedules.week(home, LocalDate.of(2026, 11, 2)).get(DayOfWeek.MONDAY))
                .extracting(Member::getId).containsExactly(ali.getId());
        assertThatThrownBy(() -> schedules.setSchedule(home, "tue Ali"))
                .isInstanceOfSatisfying(CookingScheduleService.ScheduleException.class,
                        e -> assertThat(e.issue()).isEqualTo(CookingScheduleService.Issue.AMBIGUOUS_MEMBER));
    }

    @Test void handoverAffectsOnlyTodayAndRequiresCook() {
        Household home = households.save(new Household(-203L, "Home"));
        Member ali = member(home, 1L, "Ali", "ali");
        Member berik = member(home, 2L, "Berik", "berik");
        Member dana = member(home, 3L, "Dana", "dana");
        LocalDate monday = LocalDate.of(2026, 11, 2);
        schedules.setSchedule(home, "mon @ali @berik\nsun -");
        assertThat(schedules.handOver(home, monday, ali, "@dana").getId()).isEqualTo(dana.getId());
        assertThat(schedules.effectiveCooks(home, monday)).extracting(Member::getId)
                .containsExactly(berik.getId(), dana.getId());
        assertThat(schedules.effectiveCooks(home, monday.plusWeeks(1))).extracting(Member::getId)
                .containsExactly(ali.getId(), berik.getId());
        assertThatThrownBy(() -> schedules.handOver(home, monday, ali, "@dana"))
                .isInstanceOfSatisfying(CookingScheduleService.ScheduleException.class,
                        e -> assertThat(e.issue()).isEqualTo(CookingScheduleService.Issue.NOT_COOKING));
        assertThat(schedules.effectiveCooks(home, monday.plusDays(6))).isEmpty();
        schedules.clear(home);
        assertThat(schedules.week(home, monday.plusWeeks(1)).values()).allSatisfy(cooks -> assertThat(cooks).isEmpty());
    }

    private Member member(Household household, Long id, String name, String username) {
        Member member = new Member(household, id, name);
        member.setTelegramUsername(username);
        return members.save(member);
    }
}
