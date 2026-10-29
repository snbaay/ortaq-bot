package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(NoteService.class)
class NoteServiceTest {
    @Autowired NoteService notes;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test void addListDeleteOnlyInSameHousehold() {
        Household home = households.save(new Household(-51L, "Home"));
        Household other = households.save(new Household(-52L, "Other"));
        Member author = members.save(new Member(home, 1L, "Author"));
        var note = notes.add(home, author, "  WiFi password is on router  ");
        assertThat(note.getText()).isEqualTo("WiFi password is on router");
        assertThat(notes.latest(home)).hasSize(1);
        assertThat(notes.delete(other, note.getId())).isFalse();
        assertThat(notes.delete(home, note.getId())).isTrue();
        assertThat(notes.latest(home)).isEmpty();
    }

    @Test void rejectsLongText() {
        Household home = households.save(new Household(-53L, "Home"));
        Member author = members.save(new Member(home, 1L, "Author"));
        assertThatThrownBy(() -> notes.add(home, author, "x".repeat(301)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
