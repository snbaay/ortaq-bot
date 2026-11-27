package kz.homebot.bot.callback;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ProcessedUpdates {
    private final JdbcTemplate jdbc;
    private final AtomicLong nextCleanup = new AtomicLong();

    public ProcessedUpdates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(String bot, Integer updateId, String callbackId) {
        if (updateId == null) {
            return false;
        }
        Instant now = Instant.now();
        long scheduled = nextCleanup.get();
        if (now.getEpochSecond() >= scheduled
                && nextCleanup.compareAndSet(scheduled, now.plus(Duration.ofDays(1)).getEpochSecond())) {
            jdbc.update("DELETE FROM processed_update WHERE created_at < ?", Timestamp.from(now.minus(Duration.ofDays(7))));
        }
        try {
            jdbc.update("INSERT INTO processed_update(bot_identity, update_id, callback_id, created_at) VALUES (?, ?, ?, ?)",
                    bot.toLowerCase(Locale.ROOT), updateId, callbackId, Timestamp.from(now));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
}
