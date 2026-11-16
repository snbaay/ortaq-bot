package kz.homebot.bot;

import kz.homebot.repository.HouseholdRepository;
import kz.homebot.repository.MemberRepository;
import kz.homebot.service.MessageService;
import kz.homebot.service.RandomPicker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RandomHandlerTest {
    @Mock HouseholdRepository households;
    @Mock MemberRepository members;
    @Mock RandomPicker picker;
    @Mock MessageService messages;

    @Test
    void oversizedChoiceIsRejectedBeforePicking() {
        RandomHandler handler = new RandomHandler(households, members, picker, messages);
        when(messages.getMessage("random.usageChoose")).thenReturn("Usage");
        List<String> replies = new ArrayList<>();

        handler.handle(new CommandContext(-1L, "group", new Sender(1L, "Ali", null),
                "tandau", "x".repeat(201) + ", tea", List.of(), replies::add));

        assertThat(replies).containsExactly("Usage");
        verify(picker, never()).pick(anyList());
    }

    @Test
    void oversizedQuestionIsRejectedBeforeLookingUpMembers() {
        RandomHandler handler = new RandomHandler(households, members, picker, messages);
        when(messages.getMessage("random.questionTooLong")).thenReturn("Too long");
        List<String> replies = new ArrayList<>();

        handler.handle(new CommandContext(-1L, "group", new Sender(1L, "Ali", null),
                "kim", "x".repeat(201), List.of(), replies::add));

        assertThat(replies).containsExactly("Too long");
        verify(picker, never()).pick(anyList());
    }
}
