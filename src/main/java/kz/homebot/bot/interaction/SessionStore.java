package kz.homebot.bot.interaction;

import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.domain.Member;
import kz.homebot.repository.UtcTimestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;

@Repository
public class SessionStore {
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);
    private static final Duration PROMPT_LIFETIME = Duration.ofMinutes(10);
    private static final RowMapper<InteractionSession> ROW = (rs, row) -> new InteractionSession(
            rs.getString("id"), rs.getLong("household_id"), rs.getObject("telegram_user_id", Long.class),
            rs.getLong("chat_id"), rs.getString("kind"), rs.getString("state"), rs.getString("payload_json"),
            rs.getObject("prompt_message_id", Integer.class), UtcTimestamp.instant(rs.getObject("prompt_expires_at", LocalDateTime.class)),
            rs.getObject("menu_message_id", Integer.class), rs.getInt("version"),
            UtcTimestamp.instant(rs.getObject("expires_at", LocalDateTime.class)),
            UtcTimestamp.instant(rs.getObject("closed_at", LocalDateTime.class)));

    private final JdbcTemplate jdbc;
    private final AtomicLong nextCleanup = new AtomicLong();

    public SessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public InteractionSession open(Member member, long chat, String kind, String state, String payload) {
        long household = member.getHousehold().getId();
        long user = member.getTelegramUserId();
        Instant now = Instant.now();
        cleanup(now);
        // Serialize creation even if there is no existing row to lock.
        jdbc.queryForObject("SELECT id FROM household WHERE id = ? AND telegram_chat_id = ? FOR UPDATE",
                Long.class, household, chat);
        if (!isMember(household, user)) {
            throw new IllegalArgumentException("Unknown household member");
        }
        return active(household, user, kind, now).orElseGet(() -> insert(household, user, chat, kind, state, payload, now));
    }

    @Transactional
    public InteractionSession shared(Member member, long chat, String kind, String state) {
        Instant now = Instant.now();
        cleanup(now);
        long household = member.getHousehold().getId();
        if (!isMember(household, member.getTelegramUserId())
                || jdbc.queryForObject("SELECT telegram_chat_id FROM household WHERE id = ?", Long.class, household) != chat) {
            throw new IllegalArgumentException("Unknown household chat or member");
        }
        return insert(household, null, chat, kind, state, "{}", now);
    }

    @Transactional
    public InteractionSession navigate(Member member, long chat, String kind, String state, String payload) {
        var session = open(member, chat, kind, state, payload);
        return advance(session, state, payload, false);
    }

    @Transactional
    public <T> T withOwned(Member member, long chat, String kind, String state, String payload,
                          Function<InteractionSession, T> action) {
        var row = open(member, chat, kind, state, payload);
        var current = lock(row.id()).orElseThrow();
        if (!current.activeAt(Instant.now())) {
            current = insert(current.householdId(), current.userId(), chat, kind, state, payload, Instant.now());
        }
        return action.apply(current);
    }

    public boolean bindMenu(InteractionSession session, int messageId) {
        return jdbc.update("""
                UPDATE interaction_session SET menu_message_id = ? WHERE id = ? AND version = ?
                AND menu_message_id IS NULL AND closed_at IS NULL AND expires_at > ?
                """, messageId, session.id(), session.version(), UtcTimestamp.from(Instant.now())) == 1;
    }

    @Transactional
    public <T> Optional<T> withButton(CallbackRouter.Call call, String kind,
                                      BiFunction<InteractionSession, String, T> action) {
        return validateButton(call, kind, false, action);
    }

    @Transactional
    public <T> Optional<T> withConfirmation(CallbackRouter.Call call, String kind,
                                            BiFunction<InteractionSession, String, T> action) {
        return validateButton(call, kind, true, action);
    }

    private <T> Optional<T> validateButton(CallbackRouter.Call call, String kind, boolean allowReplay,
                                           BiFunction<InteractionSession, String, T> action) {
        String[] fields = call.data().split(":", 3);
        if (fields.length != 3 || !fields[0].matches("[0-9a-f]{32}") || !fields[1].matches("[0-9a-z]{1,6}")) {
            return Optional.empty();
        }
        long user = call.member().getTelegramUserId();
        Instant now = Instant.now();
        return lock(fields[0]).filter(row -> row.kind().equals(kind) && row.expiresAt().isAfter(now)
                && row.chatId() == call.message().getChatId()
                && row.householdId() == call.member().getHousehold().getId()
                && row.menuId() != null && row.menuId().equals(call.message().getMessageId())
                && (row.activeAt(now) && Integer.toString(row.version(), 36).equals(fields[1])
                    || allowReplay && row.closedAt() != null && "confirmed".equals(row.state())
                    && "confirm".equals(fields[2]) && Integer.toString(row.version() - 1, 36).equals(fields[1]))
                && (row.userId() == null ? !allowReplay : row.userId() == user)
                && isMember(row.householdId(), user)).map(row -> action.apply(row, fields[2]));
    }

    public Optional<InteractionSession> active(long household, long user, String kind, Instant now) {
        return jdbc.query("""
                SELECT * FROM interaction_session WHERE household_id = ? AND telegram_user_id = ?
                AND kind = ? AND closed_at IS NULL AND expires_at > ?
                """, ROW, household, user, kind, UtcTimestamp.from(now)).stream().findFirst();
    }

    protected InteractionSession insert(long household, Long user, long chat, String kind, String state,
                                        String payload, Instant now) {
        String id = UUID.randomUUID().toString().replace("-", "");
        Instant expiry = now.plus(SESSION_LIFETIME);
        jdbc.update("""
                INSERT INTO interaction_session
                (id, household_id, telegram_user_id, chat_id, kind, state, payload_json, version, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """, id, household, user, chat, kind, state, payload, UtcTimestamp.from(now), UtcTimestamp.from(expiry));
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
                WHERE id = ? AND version = ? AND prompt_message_id IS NULL AND closed_at IS NULL AND expires_at > ?
                """, messageId, UtcTimestamp.from(now.plus(PROMPT_LIFETIME)), session.id(), session.version(),
                UtcTimestamp.from(now)) == 1;
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
                """, state, payload, closed == null ? null : UtcTimestamp.from(closed), previous.id(), previous.version());
        if (updated != 1) {
            throw new IllegalStateException("Session version changed");
        }
        return new InteractionSession(previous.id(), previous.householdId(), previous.userId(), previous.chatId(),
                previous.kind(), state, payload, null, null, null, previous.version() + 1, previous.expiresAt(), closed);
    }

    public void finish(InteractionSession previous, String result) {
        int updated = jdbc.update("""
                UPDATE interaction_session SET state = 'confirmed', payload_json = ?, version = version + 1,
                prompt_message_id = NULL, prompt_expires_at = NULL, closed_at = ?
                WHERE id = ? AND version = ? AND closed_at IS NULL AND telegram_user_id IS NOT NULL
                """, result, UtcTimestamp.from(Instant.now()), previous.id(), previous.version());
        if (updated != 1) {
            throw new IllegalStateException("Session already finished");
        }
    }

    private void cleanup(Instant now) {
        long scheduled = nextCleanup.get();
        if (now.getEpochSecond() >= scheduled
                && nextCleanup.compareAndSet(scheduled, now.plus(Duration.ofDays(1)).getEpochSecond())) {
            jdbc.update("DELETE FROM interaction_session WHERE expires_at < ?", UtcTimestamp.from(now.minus(Duration.ofDays(7))));
        }
    }

    protected Optional<InteractionSession> lock(String id) {
        return jdbc.query("SELECT * FROM interaction_session WHERE id = ? FOR UPDATE", ROW, id).stream().findFirst();
    }

    protected boolean isMember(long household, long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member WHERE household_id = ? AND telegram_user_id = ?",
                Integer.class, household, user) > 0;
    }

}
