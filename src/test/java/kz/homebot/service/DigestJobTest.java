package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.domain.PaymentDeadline;
import kz.homebot.repository.HouseholdRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DigestJobTest {
    @Mock HouseholdRepository households;
    @Mock CookingScheduleService schedules;
    @Mock DigestPolicy policy;
    @Mock MessageService messages;
    @Mock DeadlineService deadlines;
    @Mock PaymentCountdown countdown;
    @Mock TelegramClient client;

    @Test void dayOffKeepsPaymentReminderWithoutCookingText() throws Exception {
        Household home = new Household(-301L, "Home");
        PaymentDeadline deadline = mock(PaymentDeadline.class);
        when(deadline.getDayOfMonth()).thenReturn(5);
        when(deadline.getTitle()).thenReturn("Rent");
        when(households.findAll()).thenReturn(List.of(home));
        when(policy.shouldSend(any(), any())).thenReturn(true);
        when(schedules.effectiveCooks(eq(home), any(LocalDate.class))).thenReturn(List.of());
        when(deadlines.upcoming(eq(home), any(LocalDate.class))).thenReturn(List.of(deadline));
        when(countdown.daysLeft(any(LocalDate.class), eq(5))).thenReturn(1L);
        when(messages.getMessage("digest.morningNoCooking")).thenReturn("Good morning");
        when(messages.getMessage("deadline.tomorrow")).thenReturn("tomorrow");
        when(messages.getMessage("deadline.reminder", "Rent", "tomorrow")).thenReturn("Rent tomorrow");
        new DigestJob(households, schedules, policy, messages, deadlines, countdown, client).sendDueDigests();
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertThat(sent.getValue().getText()).isEqualTo("Good morning\nRent tomorrow");
        assertThat(home.getLastDigestDate()).isNotNull();
    }

    @Test void cooksAreMentionedByUsernameWhenAvailable() throws Exception {
        Household home = new Household(-302L, "Home");
        Member ali = new Member(home, 1L, "Ali"); ali.setTelegramUsername("ali");
        Member berik = new Member(home, 2L, "Berik");
        when(households.findAll()).thenReturn(List.of(home));
        when(policy.shouldSend(any(), any())).thenReturn(true);
        when(schedules.effectiveCooks(eq(home), any(LocalDate.class))).thenReturn(List.of(ali, berik));
        when(deadlines.upcoming(eq(home), any(LocalDate.class))).thenReturn(List.of());
        when(messages.getMessage("digest.morning", "@ali, Berik")).thenReturn("@ali, Berik cook today");
        new DigestJob(households, schedules, policy, messages, deadlines, countdown, client).sendDueDigests();
        ArgumentCaptor<SendMessage> sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertThat(sent.getValue().getText()).contains("@ali, Berik");
    }
}
