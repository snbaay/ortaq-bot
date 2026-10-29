package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.User;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class MemberRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistrationService.class);

    private final HouseholdRepository householdRepository;
    private final MemberRepository memberRepository;

    public MemberRegistrationService(HouseholdRepository householdRepository, MemberRepository memberRepository) {
        this.householdRepository = householdRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Member registerMember(Long telegramChatId, String chatTitle, Long telegramUserId, String firstName, String lastName) {
        return registerMember(telegramChatId, chatTitle, telegramUserId, firstName, lastName, null);
    }

    @Transactional
    public Member registerMember(Long telegramChatId, String chatTitle, Long telegramUserId, String firstName,
                                 String lastName, String username) {
        Household household = householdRepository.findByTelegramChatId(telegramChatId)
                .orElseGet(() -> {
                    String title = (chatTitle != null && !chatTitle.isBlank()) ? chatTitle.trim() : "Ортақ үй";
                    Household newHousehold = new Household(telegramChatId, title, LocalDateTime.now());
                    return householdRepository.save(newHousehold);
                });

        if (chatTitle != null && !chatTitle.isBlank() && !chatTitle.trim().equals(household.getTitle())) {
            household.setTitle(chatTitle.trim());
            household = householdRepository.save(household);
        }

        String displayName = buildDisplayName(firstName, lastName);

        Optional<Member> existingMember = memberRepository.findByHouseholdAndTelegramUserId(household, telegramUserId);
        if (existingMember.isPresent()) {
            Member member = existingMember.get();
            if (!displayName.isBlank() && !displayName.equals(member.getDisplayName())) {
                member.setDisplayName(displayName);
            }
            member.setTelegramUsername(username);
            return memberRepository.save(member);
        }

        Member newMember = new Member(household, telegramUserId, displayName, LocalDateTime.now());
        newMember.setTelegramUsername(username);
        Member savedMember = memberRepository.save(newMember);
        log.info("Registered member {} in household {}", savedMember.getDisplayName(), household.getTitle());
        return savedMember;
    }

    @Transactional
    public Member registerMember(Long telegramChatId, String chatTitle, User telegramUser) {
        if (telegramUser == null) {
            throw new IllegalArgumentException("Telegram user must not be null");
        }
        return registerMember(telegramChatId, chatTitle, telegramUser.getId(), telegramUser.getFirstName(),
                telegramUser.getLastName(), telegramUser.getUserName());
    }

    @Transactional
    public Member registerOrGet(Long telegramChatId, String chatTitle, Long telegramUserId, String firstName, String lastName) {
        return registerMember(telegramChatId, chatTitle, telegramUserId, firstName, lastName);
    }

    @Transactional
    public Member registerOrGet(Long telegramChatId, String chatTitle, User telegramUser) {
        return registerMember(telegramChatId, chatTitle, telegramUser);
    }

    private String buildDisplayName(String firstName, String lastName) {
        boolean hasFirst = firstName != null && !firstName.isBlank();
        boolean hasLast = lastName != null && !lastName.isBlank();

        if (hasFirst && hasLast) {
            return firstName.trim() + " " + lastName.trim();
        } else if (hasFirst) {
            return firstName.trim();
        } else if (hasLast) {
            return lastName.trim();
        } else {
            return "Пайдаланушы";
        }
    }
}
