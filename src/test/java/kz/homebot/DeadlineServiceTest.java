package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.DeadlineService;
import kz.homebot.service.PaymentCountdown;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({DeadlineService.class, PaymentCountdown.class})
class DeadlineServiceTest {
    @Autowired DeadlineService deadlines;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test void ordersByDaysAndSoftDeletesOnlyWithinHome() {
        Household home = households.save(new Household(-71L, "Home"));
        Household other = households.save(new Household(-72L, "Other"));
        Member member = members.save(new Member(home, 1L, "Member"));
        var later = deadlines.add(home, member, 20, new BigDecimal("150000"), "Rent");
        var sooner = deadlines.add(home, member, 5, null, "Internet");
        assertThat(deadlines.upcoming(home, LocalDate.of(2026, 10, 4)))
                .extracting(d -> d.getId()).containsExactly(sooner.getId(), later.getId());
        assertThat(deadlines.delete(other, sooner.getId())).isFalse();
        assertThat(deadlines.delete(home, sooner.getId())).isTrue();
        assertThat(deadlines.upcoming(home, LocalDate.of(2026, 10, 4))).hasSize(1);
    }

    @Test void validatesLimits() {
        Household home = households.save(new Household(-73L, "Home"));
        Member member = members.save(new Member(home, 1L, "Member"));
        assertThatThrownBy(() -> deadlines.add(home, member, 32, null, "Rent"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deadlines.add(home, member, 1, null, "x".repeat(41)))
                .isInstanceOf(IllegalArgumentException.class);
        for (int i = 0; i < 20; i++) deadlines.add(home, member, i + 1, null, "Bill " + i);
        assertThatThrownBy(() -> deadlines.add(home, member, 21, null, "Extra"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
