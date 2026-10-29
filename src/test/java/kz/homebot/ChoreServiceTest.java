package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ChoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ChoreService.class)
class ChoreServiceTest {
    @Autowired ChoreService chores;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test void findsLatestCaseInsensitiveAndOnlySameHome() {
        Household home = households.save(new Household(-61L, "Home"));
        Household other = households.save(new Household(-62L, "Other"));
        Member member = members.save(new Member(home, 1L, "Member"));
        chores.add(home, member, "Changed water filter");
        chores.add(home, member, "Cleaned kitchen");
        var latest = chores.add(home, member, "Replaced WATER filter");
        assertThat(chores.findLast(home, "water FILTER")).get().isEqualTo(latest);
        assertThat(chores.findLast(other, "water")).isEmpty();
        assertThat(chores.latest(home)).hasSize(3);
    }
}
