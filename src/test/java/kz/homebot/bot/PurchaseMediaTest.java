package kz.homebot.bot.menu;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseMediaTest {
    @Test
    void onlyBoundedTelegramPhotoReferencesAreAccepted() {
        var message = new Message();
        assertThat(PurchaseHandler.toPhoto(message)).isNull();
        var photo = new PhotoSize();
        photo.setFileId("test_opaque-id");
        photo.setFileSize(10 * 1024 * 1024);
        message.setPhoto(List.of(photo));
        assertThat(PurchaseHandler.toPhoto(message).fileId()).isEqualTo("test_opaque-id");

        photo.setFileSize(10 * 1024 * 1024 + 1);
        assertThat(PurchaseHandler.toPhoto(message)).isNull();
        photo.setFileSize(80);
        photo.setFileId("bad / path");
        assertThat(PurchaseHandler.toPhoto(message)).isNull();
    }
}
