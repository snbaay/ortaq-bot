package kz.homebot.service;

import kz.homebot.config.Limits;
import kz.homebot.domain.Member;
import kz.homebot.repository.MemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Service
public class PurchaseService {
    private final MemberRepository members;

    private final JdbcTemplate jdbc;
    private final ExpenseService expenses;
    private final MessageService messages;

    public PurchaseService(MemberRepository members, JdbcTemplate jdbc, ExpenseService expenses, MessageService messages) {
        this.members = members;
        this.jdbc = jdbc;
        this.expenses = expenses;
        this.messages = messages;
    }

    @Transactional
    public Receipt confirm(Member buyer, String key, PurchaseDraft draft) {
        long household = buyer.getHousehold().getId();
        // Lock the buyer before looking up the confirmation key. The session lock also
        // serializes Telegram callbacks, and the unique key protects every entry point.
        jdbc.queryForObject("""
                SELECT id FROM member WHERE id = ? AND household_id = ? AND telegram_user_id = ? FOR UPDATE
                """, Long.class, buyer.getId(), household, buyer.getTelegramUserId());
        var existing = jdbc.query("""
                SELECT id, expense_id FROM purchase WHERE confirmation_key = ? AND household_id = ? AND buyer_member_id = ?
                """, (row, number) -> new Receipt(row.getLong(1), row.getObject(2, Long.class)), key, household, buyer.getId());
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        if (key == null || !key.matches("[0-9a-f]{32}") || draft.lines().isEmpty() || draft.lines().size() > PurchaseDraft.MAX_LINES
                || draft.lines().stream().anyMatch(line -> !valid(line)) || draft.total().compareTo(Limits.MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Invalid purchase");
        }
        var participants = participants(buyer, draft);
        boolean shared = !draft.personal();
        Long expenseId = shared ? expenses.addExpense(buyer.getHousehold(), buyer, draft.total(),
                messages.getMessage("purchase.ledgerDescription", draft.lines().size()), participants).expense().getId() : null;
        var generated = new GeneratedKeyHolder();
        var now = Timestamp.from(Instant.now());
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO purchase(household_id, buyer_member_id, status, shared, total, purchased_at,
                        created_at, confirmed_at, expense_id, confirmation_key) VALUES (?, ?, 'CONFIRMED', ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, household);
            statement.setLong(2, buyer.getId());
            statement.setBoolean(3, shared);
            statement.setBigDecimal(4, draft.total());
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            statement.setTimestamp(7, now);
            statement.setObject(8, expenseId);
            statement.setString(9, key);
            return statement;
        }, generated);
        long receiptId = generated.getKey().longValue();
        for (int i = 0; i < draft.lines().size(); i++) {
            var line = draft.lines().get(i);
            jdbc.update("INSERT INTO purchase_item(purchase_id, catalog_key, label, price, position) VALUES (?, ?, ?, ?, ?)",
                    receiptId, line.product(), line.label(), line.price(), i);
        }
        for (var participant : participants) {
            jdbc.update("INSERT INTO purchase_participant(purchase_id, member_id) VALUES (?, ?)", receiptId, participant.getId());
        }
        return new Receipt(receiptId, expenseId);
    }

    private boolean valid(PurchaseDraft.Line line) {
        return line.label() != null && !line.label().isBlank() && line.label().codePointCount(0, line.label().length()) <= 60
                && line.price() != null && line.price().signum() > 0 && line.price().scale() <= 2
                && line.price().compareTo(Limits.MAX_AMOUNT) <= 0;
    }

    public record Receipt(long id, Long expenseId) {}

    public List<Member> householdMembers(Member buyer) {
        return members.findByHousehold(buyer.getHousehold()).stream().sorted(Comparator.comparing(Member::getId)).toList();
    }

    public List<Member> participants(Member buyer, PurchaseDraft draft) {
        if (draft.personal()) {
            return List.of();
        }
        var all = householdMembers(buyer);
        if (draft.participantIds() == null) {
            return all;
        }
        var selected = new HashSet<>(draft.participantIds());
        selected.add(buyer.getId());
        var result = all.stream().filter(member -> selected.contains(member.getId())).toList();
        if (result.size() != selected.size()) {
            throw new IllegalArgumentException("Purchase participants must belong to the household");
        }
        return result;
    }

    public PurchaseDraft toggleParticipant(Member buyer, PurchaseDraft draft, long memberId) {
        if (buyer.getId() == memberId) {
            return draft;
        }
        if (householdMembers(buyer).stream().noneMatch(member -> member.getId() == memberId)) {
            throw new IllegalArgumentException("Participant is from another household");
        }
        var ids = new HashSet<>(participants(buyer, draft).stream().map(Member::getId).toList());
        if (!ids.remove(memberId)) {
            ids.add(memberId);
        }
        ids.add(buyer.getId());
        return draft.sharing(false, ids.stream().sorted().toList());
    }
}
