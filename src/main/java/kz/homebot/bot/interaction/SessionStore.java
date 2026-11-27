package kz.homebot.bot.interaction;

import kz.homebot.domain.Member;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Repository
public class SessionStore {
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);
    private static final Duration PROMPT_LIFETIME = Duration.ofMinutes(10);
    private static final RowMapper<InteractionSession> ROW = (rs, row) -> new InteractionSession(
            rs.getString("id"), rs.getLong("household_id"), rs.getObject("telegram_user_id", Long.class),
            rs.getLong("chat_id"), rs.getString("kind"), rs.getString("state"), rs.getString("payload_json"),
            rs.getObject("prompt_message_id", Integer.class), instant(rs.getTimestamp("prompt_expires_at")),
            rs.getObject("menu_message_id", Integer.class), rs.getInt("version"),
            instant(rs.getTimestamp("expires_at")), instant(rs.getTimestamp("closed_at")));

    private final JdbcTemplate jdbc;

    public SessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public InteractionSession open(Member member, long chat, String kind, String state, String payload) {
        long household = member.getHousehold().getId();
        long user = member.getTelegramUserId();
        Instant now = Instant.now();
        // Serialize creation even if there is no existing row to lock.
        jdbc.queryForObject("SELECT id FROM household WHERE id = ? AND telegram_chat_id = ? FOR UPDATE",
                Long.class, household, chat);
        if (!isMember(household, user)) {
            throw new IllegalArgumentException("Unknown household member");
        }
        return active(household, user, kind, now).orElseGet(() -> insert(household, user, chat, kind, state, payload, now));
    }

    public Optional<InteractionSession> active(long household, long user, String kind, Instant now) {
        return jdbc.query("""
                SELECT * FROM interaction_session WHERE household_id = ? AND telegram_user_id = ?
                AND kind = ? AND closed_at IS NULL AND expires_at > ?
                """, ROW, household, user, kind, Timestamp.from(now)).stream().findFirst();
    }

    protected InteractionSession insert(long household, Long user, long chat, String kind, String state,
                                        String payload, Instant now) {
        String id = UUID.randomUUID().toString().replace("-", "");
        Instant expiry = now.plus(SESSION_LIFETIME);
        jdbc.update("""
                INSERT INTO interaction_session
                (id, household_id, telegram_user_id, chat_id, kind, state, payload_json, version, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, id, household, user, chat, kind, state, payload, Timestamp.from(now), Timestamp.from(expiry));
        return new InteractionSession(id, household, user, chat, kind, state, payload, null, null, null, 0, expiry, null);
    }

    public Optional<InteractionSession> reply(long chat, long user, int prompt) {
        return jdbc.query("""
                SELECT * FROM interaction_session WHERE chat_id = ? AND telegram_user_id = ? AND prompt_message_id = ?
                ORDER BY created_at DESC
                """, ROW, chat, user, prompt).stream().findFirst();
    }

    public boolean bindPrompt(InteractionSession session, int messageId) {
        Instant now = Instant.now();
        return jdbc.update("""
                UPDATE interaction_session SET prompt_message_id = ?, prompt_expires_at = ?
                WHERE id = ? AND version = ? AND closed_at IS NULL AND expires_at > ?
                """, messageId, Timestamp.from(now.plus(PROMPT_LIFETIME)), session.id(), session.version(),
                Timestamp.from(now)) == 1;
    }

    @Transactional
    public <T> Optional<T> withReply(InteractionSession expected, Function<InteractionSession, T> action) {
        Instant now = Instant.now();
        return lock(expected.id()).filter(row -> row.version() == expected.version()
                && row.activeAt(now) && row.promptExpiresAt() != null && row.promptExpiresAt().isAfter(now)
                && Objects.equals(row.promptId(), expected.promptId())
                && isMember(row.householdId(), row.userId())).map(action);
    }

    public InteractionSession advance(InteractionSession previous, String state, String payload, boolean close) {
        Instant closed = close ? Instant.now() : null;
        int updated = jdbc.update("""
                UPDATE interaction_session SET state = ?, payload_json = ?, version = version + 1,
                menu_message_id = NULL, prompt_message_id = NULL, prompt_expires_at = NULL, closed_at = ?
                WHERE id = ? AND version = ? AND closed_at IS NULL
                """, state, payload, closed == null ? null : Timestamp.from(closed), previous.id(), previous.version());
        if (updated != 1) {
            throw new IllegalStateException("Session version changed");
        }
        return new InteractionSession(previous.id(), previous.householdId(), previous.userId(), previous.chatId(),
                previous.kind(), state, payload, null, null, null, previous.version() + 1, previous.expiresAt(), closed);
    }

    protected Optional<InteractionSession> lock(String id) {
        return jdbc.query("SELECT * FROM interaction_session WHERE id = ? FOR UPDATE", ROW, id).stream().findFirst();
    }

    protected boolean isMember(long household, long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE household_id = ? AND telegram_user_id = ?",
                Integer.class, household, user) > 0;
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
