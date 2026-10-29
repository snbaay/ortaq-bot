package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.ShoppingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(ShoppingService.class)
class ShoppingServiceTest {
    @Autowired ShoppingService shopping;
    @Autowired HouseholdRepository households;
    @Autowired MemberRepository members;

    @Test void addBuyAndHouseholdIsolation() {
        Household first = households.save(new Household(-41L, "First"));
        Household other = households.save(new Household(-42L, "Other"));
        Member buyer = members.save(new Member(first, 1L, "Buyer"));
        var created = shopping.add(first, buyer, "milk, bread, eggs");
        assertThat(created).hasSize(3);
        assertThat(shopping.buy(other, buyer, created.get(0).getId())).isFalse();
        assertThat(shopping.buy(first, buyer, created.get(0).getId())).isTrue();
        assertThat(shopping.open(first)).extracting(item -> item.getText())
                .containsExactly("bread", "eggs");
    }

    @Test void validatesLengthAndCapacity() {
        Household home = households.save(new Household(-43L, "Home"));
        Member member = members.save(new Member(home, 1L, "Buyer"));
        assertThatThrownBy(() -> shopping.add(home, member, "x".repeat(61)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shopping.add(home, member, "milk, ,bread"))
                .isInstanceOf(IllegalArgumentException.class);
        shopping.add(home, member, String.join(",", java.util.Collections.nCopies(50, "item")));
        assertThatThrownBy(() -> shopping.add(home, member, "extra"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
