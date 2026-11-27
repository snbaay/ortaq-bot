package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.MemberRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchaseSharingTest {
    @Test
    void defaultsToEveryoneAndEnforcesHouseholdAndBuyerMembership() {
        var household = new Household(-100L, "Home");
        var buyer = new Member(household, 100L, "Buyer");
        buyer.setId(1L);
        var roommate = new Member(household, 101L, "Roommate");
        roommate.setId(2L);
        var members = mock(MemberRepository.class);
        when(members.findByHousehold(household)).thenReturn(List.of(buyer, roommate));
        var purchases = new PurchaseService(members);

        assertThat(purchases.participants(buyer, PurchaseDraft.EMPTY)).containsExactly(buyer, roommate);
        assertThat(purchases.participants(buyer, purchases.toggleParticipant(buyer, PurchaseDraft.EMPTY, 2L)))
                .containsExactly(buyer);
        assertThat(purchases.participants(buyer, PurchaseDraft.EMPTY.sharing(false, List.of(2L))))
                .containsExactly(buyer, roommate);
        assertThat(purchases.participants(buyer, PurchaseDraft.EMPTY.sharing(true, null))).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> purchases.toggleParticipant(buyer, PurchaseDraft.EMPTY, 3L));
        assertThatIllegalArgumentException().isThrownBy(() ->
                purchases.participants(buyer, PurchaseDraft.EMPTY.sharing(false, List.of(3L))));
    }
}
