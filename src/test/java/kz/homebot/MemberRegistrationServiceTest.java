package kz.homebot;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.MemberRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.telegram.telegrambots.meta.api.objects.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@Import(MemberRegistrationService.class)
class MemberRegistrationServiceTest {

    @Autowired
    private MemberRegistrationService registrationService;

    @Autowired
    private HouseholdRepository householdRepository;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        householdRepository.deleteAll();
    }

    @Test
    void shouldRegisterNewHouseholdAndNewMember() {
        // Case 1: new household + new member
        Member member = registrationService.registerMember(-100123456789L, "Dostar Flat", 111L, "Ali", "Khan");

        assertNotNull(member);
        assertNotNull(member.getId());
        assertEquals(111L, member.getTelegramUserId());
        assertEquals("Ali Khan", member.getDisplayName());

        Household household = member.getHousehold();
        assertNotNull(household);
        assertNotNull(household.getId());
        assertEquals(-100123456789L, household.getTelegramChatId());
        assertEquals("Dostar Flat", household.getTitle());

        assertEquals(1, householdRepository.count());
        assertEquals(1, memberRepository.count());
    }

    @Test
    void shouldReturnSameMemberWhenSameUserRegistersTwice() {
        // Case 2: same user twice = one member
        Member first = registrationService.registerMember(-100123456789L, "Dostar Flat", 111L, "Ali", "Khan");
        Member second = registrationService.registerMember(-100123456789L, "Dostar Flat", 111L, "Ali", "Khan");

        assertEquals(first.getId(), second.getId());
        assertEquals("Ali Khan", second.getDisplayName());
        assertEquals(1, householdRepository.count());
        assertEquals(1, memberRepository.count());
    }

    @Test
    void shouldCreateTwoMembersForTwoUsersInSameHousehold() {
        // Case 3: two users = two members
        Member member1 = registrationService.registerMember(-100123456789L, "Dostar Flat", 111L, "Ali", "Khan");
        Member member2 = registrationService.registerMember(-100123456789L, "Dostar Flat", 222L, "Berik", "Serik");

        assertNotEquals(member1.getId(), member2.getId());
        assertEquals(member1.getHousehold().getId(), member2.getHousehold().getId());
        assertEquals(1, householdRepository.count());
        assertEquals(2, memberRepository.count());
    }

    @Test
    void shouldCreateTwoMembersForSameUserInTwoChats() {
        // Case 4: same user in two chats = two members
        Member flat1Member = registrationService.registerMember(-100111111111L, "Flat 1", 111L, "Ali", "Khan");
        Member flat2Member = registrationService.registerMember(-100222222222L, "Flat 2", 111L, "Ali", "Khan");

        assertNotEquals(flat1Member.getId(), flat2Member.getId());
        assertNotEquals(flat1Member.getHousehold().getId(), flat2Member.getHousehold().getId());
        assertEquals(-100111111111L, flat1Member.getHousehold().getTelegramChatId());
        assertEquals(-100222222222L, flat2Member.getHousehold().getTelegramChatId());
        assertEquals(2, householdRepository.count());
        assertEquals(2, memberRepository.count());
    }

    @Test
    void shouldRegisterMemberWithOnlyFirstName() {
        Member member = registrationService.registerMember(-100123456789L, "Dostar Flat", 333L, "Dana", null);

        assertEquals("Dana", member.getDisplayName());
        assertEquals(1, memberRepository.count());
    }

    @Test
    void shouldRegisterMemberFromTelegramUserObject() {
        User user = new User(444L, "Aruzhan", false);
        user.setLastName("Arman");

        Member member = registrationService.registerMember(-100123456789L, "Dostar Flat", user);

        assertNotNull(member);
        assertEquals(444L, member.getTelegramUserId());
        assertEquals("Aruzhan Arman", member.getDisplayName());
    }
}
