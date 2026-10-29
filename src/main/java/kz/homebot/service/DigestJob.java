package kz.homebot.service;

import kz.homebot.repository.HouseholdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

@Component
public class DigestJob {
    private static final Logger log = LoggerFactory.getLogger(DigestJob.class);
    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
    private final HouseholdRepository households;
    private final CookingScheduleService schedules;
    private final DigestPolicy policy;
    private final MessageService messages;
    private final TelegramClient client;
    private final DeadlineService deadlines;
    private final PaymentCountdown countdown;

    @Autowired
    public DigestJob(HouseholdRepository households, CookingScheduleService schedules,
                     DigestPolicy policy, MessageService messages, DeadlineService deadlines,
                     PaymentCountdown countdown, @Value("${bot.token}") String botToken) {
        this(households, schedules, policy, messages, deadlines, countdown, new OkHttpTelegramClient(botToken));
    }

    public DigestJob(HouseholdRepository households, CookingScheduleService schedules,
                     DigestPolicy policy, MessageService messages, DeadlineService deadlines,
                     PaymentCountdown countdown, TelegramClient client) {
        this.households = households; this.schedules = schedules;
        this.policy = policy; this.messages = messages; this.deadlines = deadlines;
        this.countdown = countdown; this.client = client;
    }

    @Scheduled(fixedRate = 900000, initialDelay = 900000)
    public void sendDueDigests() {
        LocalDateTime now = LocalDateTime.now(ALMATY);
        for (var household : households.findAll()) {
            if (!policy.shouldSend(now, household.getLastDigestDate())) {
                continue;
            }
            try {
                var cooks = schedules.effectiveCooks(household, now.toLocalDate());
                StringBuilder text = new StringBuilder(cooks.isEmpty()
                        ? messages.getMessage("digest.morningNoCooking")
                        : messages.getMessage("digest.morning", cooks.stream()
                                .map(m -> m.getTelegramUsername() == null || m.getTelegramUsername().isBlank()
                                        ? m.getDisplayName() : "@" + m.getTelegramUsername())
                                .collect(Collectors.joining(", "))));
                for (var deadline : deadlines.upcoming(household, now.toLocalDate())) {
                    long days = countdown.daysLeft(now.toLocalDate(), deadline.getDayOfMonth());
                    if (days == 0 || days == 1 || days == 3) {
                        String remaining = days == 0 ? messages.getMessage("deadline.today")
                                : days == 1 ? messages.getMessage("deadline.tomorrow")
                                : messages.getMessage("deadline.days", days);
                        text.append("\n").append(messages.getMessage("deadline.reminder", deadline.getTitle(), remaining));
                    }
                }
                client.execute(SendMessage.builder().chatId(household.getTelegramChatId())
                        .text(text.toString()).build());
                household.setLastDigestDate(now.toLocalDate());
                households.save(household);
            } catch (TelegramApiException e) {
                log.warn("Digest delivery failed for chat {}: {}", household.getTelegramChatId(), e.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("Digest failed for chat {}: {}", household.getTelegramChatId(), e.getClass().getSimpleName());
            }
        }
    }
}
