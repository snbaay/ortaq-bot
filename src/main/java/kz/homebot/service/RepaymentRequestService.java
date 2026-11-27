package kz.homebot.service;

import kz.homebot.config.Limits;
import kz.homebot.domain.Member;
import kz.homebot.repository.MemberRepository;
import kz.homebot.repository.UtcTimestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class RepaymentRequestService {
    private static final String COLUMNS = """
            SELECT r.*, h.telegram_chat_id, sender.display_name AS sender_name, recipient.display_name AS recipient_name
            FROM repayment_request r JOIN household h ON h.id = r.household_id
            JOIN member sender ON sender.id = r.sender_member_id
            JOIN member recipient ON recipient.id = r.recipient_member_id
            """;
    private static final RowMapper<Request> ROW = (row, number) -> new Request(row.getLong("id"),
            row.getString("token"), row.getLong("household_id"), row.getLong("telegram_chat_id"),
            row.getLong("sender_member_id"), row.getString("sender_name"),
            row.getLong("recipient_member_id"), row.getString("recipient_name"), row.getBigDecimal("amount"),
            row.getString("kind"), row.getObject("correction_of_request_id", Long.class), row.getString("reason"),
            row.getString("status"), UtcTimestamp.instant(row.getObject("expires_at", LocalDateTime.class)),
            row.getObject("expense_id", Long.class), row.getObject("announcement_message_id", Integer.class),
            row.getObject("outcome_message_id", Integer.class));

    private final JdbcTemplate jdbc;
    private final MemberRepository members;
    private final ExpenseService expenses;
    private final MessageService messages;

    public RepaymentRequestService(JdbcTemplate jdbc, MemberRepository members, ExpenseService expenses, MessageService messages) {
        this.jdbc = jdbc;
        this.members = members;
        this.expenses = expenses;
        this.messages = messages;
    }

    @Transactional
    public Request submit(Member sender, long recipient, BigDecimal amount, String source) {
        checkMember(sender, true);
        var previous = bySource(sender, source);
        if (previous.isPresent()) {
            return previous.get();
        }
        if (recipient == sender.getId() || findMember(sender, recipient).isEmpty()
                || amount == null || amount.signum() <= 0 || amount.scale() > 2 || amount.compareTo(Limits.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Invalid repayment request");
        }
        return insert(sender, recipient, amount, "PAYMENT", null, null, source);
    }

    @Transactional
    public Optional<Request> correct(Member actor, long originalId, String reason, String source) {
        checkMember(actor, false);
        var original = lock(actor.getHousehold().getId(), originalId).orElse(null);
        if (original == null || !original.isPayment() || !original.involves(actor.getId())
                || !original.status().equals("CONFIRMED")) {
            return Optional.empty();
        }
        var previous = bySource(actor, source);
        if (previous.isPresent()) {
            return previous;
        }
        if (reason == null || reason.isBlank() || reason.length() > 200) {
            throw new IllegalArgumentException("Invalid correction reason");
        }
        if (jdbc.queryForObject("""
                SELECT count(*) FROM repayment_request WHERE correction_of_request_id = ?
                AND status = 'PENDING' AND expires_at > ?
                """, Integer.class, originalId, UtcTimestamp.from(Instant.now())) > 0) {
            return Optional.empty();
        }
        long recipient = original.senderId() == actor.getId() ? original.recipientId() : original.senderId();
        return Optional.of(insert(actor, recipient, original.amount(), "REVERSAL", originalId, reason, source));
    }

    @Transactional
    public Optional<Request> decide(Member actor, long chat, String token, int message, Decision decision) {
        checkMember(actor, false);
        var request = byToken(actor, chat, token, message, false)
                .flatMap(candidate -> lock(candidate.householdId(), candidate.id())).orElse(null);
        if (request == null || (decision == Decision.CANCEL ? request.senderId() : request.recipientId()) != actor.getId()) {
            return Optional.empty();
        }
        if (!"PENDING".equals(request.status())) {
            return "EXPIRED".equals(request.status()) || decision == Decision.ACCEPT && "CONFIRMED".equals(request.status())
                    ? Optional.of(request) : Optional.empty();
        }
        String status;
        Long expenseId = null;
        Instant now = Instant.now();
        if (!request.expiresAt().isAfter(now)) {
            status = "EXPIRED";
        } else if (decision == Decision.CANCEL) {
            status = "VOID";
        } else if (decision == Decision.REJECT) {
            status = "REJECTED";
        } else if (request.isPayment()) {
            var sender = findMember(actor, request.senderId()).orElseThrow();
            var expense = expenses.recordRepayment(actor.getHousehold(), sender, actor, request.amount(),
                    messages.getMessage("expense.settlement.description"));
            expense.setLinkedWorkflow("repayment");
            expenseId = expense.getId();
            status = "CONFIRMED";
        } else {
            var original = lock(request.householdId(), request.correctionOf()).orElseThrow();
            if (!"CONFIRMED".equals(original.status()) || original.expenseId() == null
                    || !original.involves(actor.getId())
                    || !expenses.reverseLinked(actor.getHousehold(), original.expenseId(), "repayment")) {
                return Optional.empty();
            }
            jdbc.update("UPDATE repayment_request SET status = 'VOID', version = version + 1 WHERE id = ?", original.id());
            status = "CONFIRMED";
        }
        changeStatus(request.id(), status, expenseId, now);
        return byId(request.id());
    }

    @Transactional(readOnly = true)
    public Optional<Request> forCorrection(Member actor, long chat, String token, int message) {
        checkMember(actor, false);
        return byToken(actor, chat, token, message, true).filter(request -> request.isPayment()
                && "CONFIRMED".equals(request.status()) && request.involves(actor.getId()));
    }

    public Optional<Request> forExpense(Member actor, long expense) {
        checkMember(actor, false);
        return jdbc.query(COLUMNS + " WHERE r.household_id = ? AND r.expense_id = ? AND r.status = 'CONFIRMED'",
                ROW, actor.getHousehold().getId(), expense).stream().filter(request -> request.involves(actor.getId())).findFirst();
    }

    public Optional<Request> forMember(Member actor, long id) {
        checkMember(actor, false);
        return byId(id).filter(request -> request.householdId() == actor.getHousehold().getId() && request.involves(actor.getId()));
    }

    public List<Member> recipients(Member sender) {
        return members.findByHousehold(sender.getHousehold()).stream().filter(member -> !member.getId().equals(sender.getId()))
                .sorted(java.util.Comparator.comparing(Member::getId)).toList();
    }

    public Optional<Member> findMember(Member actor, long id) {
        return members.findById(id).filter(member -> member.getHousehold().getId().equals(actor.getHousehold().getId()));
    }

    @Transactional
    public void expirePending() {
        var now = UtcTimestamp.from(Instant.now());
        jdbc.update("""
                UPDATE repayment_request SET status = 'EXPIRED', decided_at = ?, next_outcome_at = ?, version = version + 1
                WHERE status = 'PENDING' AND expires_at <= ?
                """, now, now, now);
    }

    public List<Request> due() {
        var now = UtcTimestamp.from(Instant.now());
        return jdbc.query(COLUMNS + """
                WHERE (r.status = 'PENDING' AND r.announcement_message_id IS NULL AND r.announcement_attempts < 3
                    AND r.next_announcement_at <= ? AND r.expires_at > ?)
                OR (r.status <> 'PENDING' AND r.outcome_message_id IS NULL AND r.outcome_attempts < 3
                    AND r.next_outcome_at <= ? AND r.decided_at > ?)
                ORDER BY r.id LIMIT 20
                """, ROW, now, now, now, UtcTimestamp.from(Instant.now().minus(Duration.ofDays(1))));
    }

    public boolean claimDelivery(long id, boolean outcome) {
        String type = outcome ? "outcome" : "announcement";
        String state = outcome ? "status <> 'PENDING'" : "status = 'PENDING'";
        String sql = ("UPDATE repayment_request SET %1$s_attempts = %1$s_attempts + 1, next_%1$s_at = ?"
                + " WHERE id = ? AND %1$s_message_id IS NULL AND %1$s_attempts < 3 AND next_%1$s_at <= ? AND %2$s")
                .formatted(type, state);
        Instant now = Instant.now();
        return jdbc.update(sql, UtcTimestamp.from(now.plusSeconds(60)), id, UtcTimestamp.from(now)) == 1;
    }

    public void delivered(long id, boolean outcome, int message) {
        String type = outcome ? "outcome" : "announcement";
        jdbc.update("UPDATE repayment_request SET " + type + "_message_id = ? WHERE id = ? AND "
                + type + "_message_id IS NULL", message, id);
    }

    private void changeStatus(long id, String status, Long expenseId, Instant now) {
        jdbc.update("""
                UPDATE repayment_request SET status = ?, expense_id = ?, decided_at = ?, next_outcome_at = ?,
                    version = version + 1 WHERE id = ?
                """, status, expenseId, UtcTimestamp.from(now), UtcTimestamp.from(now), id);
    }

    private Optional<Request> byToken(Member actor, long chat, String token, int message, boolean correction) {
        return jdbc.query(COLUMNS + " WHERE r.token = ? AND r.household_id = ? AND h.telegram_chat_id = ?", ROW,
                        token, actor.getHousehold().getId(), chat).stream().findFirst()
                .filter(row -> Objects.equals(correction ? row.outcomeId() : row.announcementId(), message));
    }

    private Optional<Request> byId(long id) {
        return jdbc.query(COLUMNS + " WHERE r.id = ?", ROW, id).stream().findFirst();
    }

    private Optional<Request> bySource(Member actor, String source) {
        return jdbc.query(COLUMNS + " WHERE r.source_update_id = ? AND r.household_id = ? AND r.sender_member_id = ?",
                ROW, source, actor.getHousehold().getId(), actor.getId()).stream().findFirst();
    }

    private Optional<Request> lock(long household, long id) {
        var locked = jdbc.queryForList("SELECT id FROM repayment_request WHERE household_id = ? AND id = ? FOR UPDATE",
                Long.class, household, id);
        return locked.isEmpty() ? Optional.empty() : byId(id);
    }

    private void checkMember(Member actor, boolean lock) {
        jdbc.queryForObject("""
                SELECT id FROM member WHERE id = ? AND household_id = ? AND telegram_user_id = ?
                """ + (lock ? " FOR UPDATE" : ""), Long.class, actor.getId(), actor.getHousehold().getId(), actor.getTelegramUserId());
    }

    private Request insert(Member actor, long recipient, BigDecimal amount, String kind, Long original, String reason, String source) {
        Instant now = Instant.now();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO repayment_request(household_id, sender_member_id, recipient_member_id, amount,
                        kind, correction_of_request_id, reason, source_update_id, token, status, created_at,
                        expires_at, next_announcement_at, next_outcome_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, actor.getHousehold().getId());
            statement.setLong(2, actor.getId());
            statement.setLong(3, recipient);
            statement.setBigDecimal(4, amount);
            statement.setString(5, kind);
            statement.setObject(6, original);
            statement.setString(7, reason);
            statement.setString(8, source);
            statement.setString(9, UUID.randomUUID().toString().replace("-", ""));
            statement.setObject(10, UtcTimestamp.from(now));
            statement.setObject(11, UtcTimestamp.from(now.plus(Duration.ofDays(1))));
            statement.setObject(12, UtcTimestamp.from(now));
            statement.setObject(13, UtcTimestamp.from(now));
            return statement;
        }, key);
        return byId(key.getKey().longValue()).orElseThrow();
    }

    public enum Decision { ACCEPT, REJECT, CANCEL }

    public record Request(long id, String token, long householdId, long chat, long senderId, String sender,
                          long recipientId, String recipient, BigDecimal amount, String kind, Long correctionOf,
                          String reason, String status, Instant expiresAt, Long expenseId,
                          Integer announcementId, Integer outcomeId) {
        public boolean isPayment() {
            return "PAYMENT".equals(kind);
        }

        public boolean involves(long memberId) {
            return senderId == memberId || recipientId == memberId;
        }
    }
}
