package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.CookingRotationService;
import kz.homebot.service.NextCookPicker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CookingRotationService.class, NextCookPicker.class})
class CookingRotationServiceTest {
    @Autowired CookingRotationService rotation;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test void skipsAndContinuesFromNewCook() {
        Household home = households.save(new Household(-30L, "Home"));
        Member first = members.save(new Member(home, 1L, "First"));
        Member second = members.save(new Member(home, 2L, "Second"));
        Member third = members.save(new Member(home, 3L, "Third"));
        LocalDate day = LocalDate.of(2026, 10, 29);
        assertThat(rotation.getOrCreateDuty(home, day).getMember().getId()).isEqualTo(first.getId());
        assertThat(rotation.skip(home, day).getMember().getId()).isEqualTo(second.getId());
        assertThat(rotation.getOrCreateDuty(home, day).getMember().getId()).isEqualTo(second.getId());
        assertThat(rotation.getOrCreateDuty(home, day.plusDays(1)).getMember().getId()).isEqualTo(third.getId());
    }
}
