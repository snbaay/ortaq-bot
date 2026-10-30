package kz.homebot.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AllowedChatsTest {
    @Test void emptyAllowsAll() { assertThat(new AllowedChats("").allows(-1L)).isTrue(); }
    @Test void listRestrictsAndTrims() {
        AllowedChats chats = new AllowedChats(" -1, -2 ");
        assertThat(chats.allows(-1L)).isTrue();
        assertThat(chats.allows(-2L)).isTrue();
        assertThat(chats.allows(-3L)).isFalse();
    }
}
