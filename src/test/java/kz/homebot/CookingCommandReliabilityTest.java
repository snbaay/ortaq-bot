package kz.homebot;

import kz.homebot.bot.CommandContext;
import kz.homebot.bot.CookingHandler;
import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.bot.Sender;
import kz.homebot.service.CookingScheduleService;
import kz.homebot.service.DigestJob;
import kz.homebot.service.MemberRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration")
class CookingCommandReliabilityTest {
    @MockBean HomeTelegramBot bot;
    @MockBean DigestJob digestJob;
    @Autowired MemberRegistrationService registration;
    @Autowired CookingScheduleService schedules;
    @Autowired CookingHandler handler;

    @Test
    void weekRendersCooksAfterServiceTransactionCloses() {
        var ali = registration.registerMember(-831L, "Home", 831L, "Ali", null);
        registration.registerMember(-831L, "Home", 832L, "Dana", null);
        schedules.setSchedule(ali.getHousehold(), "mon Ali\ntue Dana\nsun -");
        List<String> replies = new ArrayList<>();

        handler.handle(new CommandContext(-831L, "group", new Sender(831L, "Ali", null),
                "kezek", "", List.of(), replies::add));

        assertThat(replies).singleElement().asString().contains("Ali", "Dana");
    }

    @Test
    void duplicateHandoverIsRejectedWithoutChangingTodaysCooks() {
        var ali = registration.registerMember(-832L, "Home", 841L, "Ali", null, "ali");
        var dana = registration.registerMember(-832L, "Home", 842L, "Dana", null, "dana");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Almaty"));
        String day = today.getDayOfWeek().name().substring(0, 3).toLowerCase(Locale.ROOT);
        schedules.setSchedule(ali.getHousehold(), day + " @ali");
        List<String> replies = new ArrayList<>();
        CommandContext command = new CommandContext(-832L, "group", new Sender(841L, "Ali", "ali"),
                "otkizu", "@dana", List.of(), replies::add);

        handler.handle(command);
        handler.handle(command);

        assertThat(replies).hasSize(2);
        assertThat(replies.get(0)).contains("Dana");
        assertThat(replies.get(1)).doesNotContain("Dana");
        assertThat(schedules.effectiveCooks(ali.getHousehold(), today))
                .extracting(member -> member.getId()).containsExactly(dana.getId());
    }
}
