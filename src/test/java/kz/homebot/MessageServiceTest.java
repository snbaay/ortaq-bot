package kz.homebot;

import kz.homebot.config.AppConfig;
import kz.homebot.service.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageServiceTest {

    private final MessageSource messageSource = new AppConfig().messageSource();
    private final MessageService messageService = new MessageService(messageSource);

    @Test
    void shouldLoadKazakhCommandDescriptions() {
        assertEquals("Бастау", messageService.getMessage("command.start.description"));
        assertEquals("Шығын қосу", messageService.getMessage("command.shygyn.description"));
        assertEquals("Кім кімге қарыз", messageService.getMessage("command.qaryz.description"));
        assertEquals("Баланс", messageService.getMessage("command.balans.description"));
        assertEquals("Қарызды жабу", messageService.getMessage("command.tolem.description"));
        assertEquals("Жазбаны өшіру", messageService.getMessage("command.oshiru.description"));
    }

    @Test
    void shouldLoadKazakhGreeting() {
        String greeting = messageService.getMessage("bot.start.greeting");
        assertNotNull(greeting);
        assertTrue(greeting.contains("Сәлем!"));
    }
}
