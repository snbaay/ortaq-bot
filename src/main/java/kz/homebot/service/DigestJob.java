package kz.homebot.service;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class DigestJob {
    private static final Logger log = LoggerFactory.getLogger(DigestJob.class);
    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final CookingRotationService rotation;
    private final DigestPolicy policy;
    private final MessageService messages;
    private final TelegramClient client;

    public DigestJob(HouseholdRepository households, MemberRepository members, CookingRotationService rotation,
                     DigestPolicy policy, MessageService messages, @Value("${bot.token}") String botToken) {
        this.households = households; this.members = members; this.rotation = rotation;
        this.policy = policy; this.messages = messages; this.client = new OkHttpTelegramClient(botToken);
    }

    @Scheduled(fixedRate = 900000, initialDelay = 900000)
    public void sendDueDigests() {
        LocalDateTime now = LocalDateTime.now(ALMATY);
        for (var household : households.findAll()) {
            if (!policy.shouldSend(now, household.getLastDigestDate())
                    || members.findByHousehold(household).size() < 2) {
                continue;
            }
            try {
                var cook = rotation.getOrCreateDuty(household, now.toLocalDate()).getMember();
                client.execute(SendMessage.builder().chatId(household.getTelegramChatId())
                        .text(messages.getMessage("digest.morning", cook.getDisplayName())).build());
                household.setLastDigestDate(now.toLocalDate());
                households.save(household);
            } catch (TelegramApiException e) {
                log.warn("Digest delivery failed for chat {}: {}", household.getTelegramChatId(), e.getMessage());
            } catch (Exception e) {
                log.warn("Digest failed for chat {}: {}", household.getTelegramChatId(), e.getMessage());
            }
        }
    }
}
