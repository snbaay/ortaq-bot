package kz.homebot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class TelegramOutput {
    private static final Logger log = LoggerFactory.getLogger(TelegramOutput.class);
    private final TelegramClient client;

    public TelegramOutput(TelegramClient client) {
        this.client = client;
    }

    public void answer(String id, String text) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(id).text(text).build());
        } catch (TelegramApiException e) {
            log.debug("Could not answer callback: {}", e.getClass().getSimpleName());
        }
    }

    public Message send(Long chatId, String text, ReplyKeyboard keyboard) {
        try {
            return client.execute(SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build());
        } catch (TelegramApiException e) {
            log.warn("Could not send message to {}: {}", chatId, e.getClass().getSimpleName());
            return null;
        }
    }
}
