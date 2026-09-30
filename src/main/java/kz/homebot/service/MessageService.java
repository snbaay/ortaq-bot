package kz.homebot.service;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class MessageService {

    private static final Locale KAZAKH_LOCALE = Locale.of("kk");

    private final MessageSource messageSource;

    public MessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String getMessage(String code, Object... args) {
        return messageSource.getMessage(code, args, KAZAKH_LOCALE);
    }
}
