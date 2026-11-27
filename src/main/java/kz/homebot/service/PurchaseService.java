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
import java.util.Optional;

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
                || draft.lines().stream().anyMatch(line -> !valid(line)) || draft.total().compareTo(Limits.MAX_AMOUNT) > 0
                || draft.photo() != null && !draft.photo().valid()) {
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
                        created_at, confirmed_at, expense_id, confirmation_key, receipt_file_id, receipt_mime,
                        receipt_bytes) VALUES (?, ?, 'CONFIRMED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            var photo = draft.photo();
            statement.setString(10, photo == null ? null : photo.fileId());
            statement.setString(11, photo == null ? null : photo.mime());
            statement.setObject(12, photo == null ? null : photo.bytes());
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

    @Transactional(readOnly = true)
    public Optional<ReceiptDetail> receipt(Member viewer, long id) {
        long home = viewer.getHousehold().getId();
        requireViewer(viewer);
        return jdbc.query("""
                SELECT p.*, m.display_name AS buyer_name FROM purchase p JOIN member m ON m.id = p.buyer_member_id
                WHERE p.id = ? AND p.household_id = ?
                """, (row, number) -> {
            String file = row.getString("receipt_file_id");
            return new ReceiptDetail(id, row.getLong("buyer_member_id"), row.getString("buyer_name"),
                    row.getString("status"), row.getBoolean("shared"), row.getBigDecimal("total"),
                    row.getTimestamp("purchased_at").toInstant(), row.getObject("expense_id", Long.class),
                    file == null ? null : new PurchaseDraft.Photo(file, row.getString("receipt_mime"), row.getLong("receipt_bytes")),
                    List.of(), List.of());
        }, id, home).stream().findFirst().map(receipt -> {
            var lines = jdbc.query("SELECT catalog_key, label, price FROM purchase_item WHERE purchase_id = ? ORDER BY position",
                    (row, number) -> new PurchaseDraft.Line(row.getString(1), row.getString(2), row.getBigDecimal(3)), id);
            var participants = jdbc.queryForList("""
                    SELECT m.display_name FROM purchase_participant part JOIN member m ON m.id = part.member_id
                    WHERE part.purchase_id = ? AND m.household_id = ? ORDER BY m.id
                    """, String.class, id, home);
            return new ReceiptDetail(receipt.id(), receipt.buyerId(), receipt.buyer(), receipt.status(), receipt.shared(),
                    receipt.total(), receipt.time(), receipt.expenseId(), receipt.photo(), lines, participants);
        });
    }

    public List<ReceiptSummary> history(Member viewer, int page) {
        requireViewer(viewer);
        return jdbc.query("""
                SELECT id, total, status FROM purchase WHERE household_id = ? ORDER BY id DESC LIMIT 9 OFFSET ?
                """, (row, number) -> new ReceiptSummary(row.getLong(1), row.getBigDecimal(2), row.getString(3)),
                viewer.getHousehold().getId(), page * 8L);
    }

    public List<RecentProduct> recent(Member viewer) {
        requireViewer(viewer);
        return jdbc.query("""
                SELECT id, catalog_key, label FROM (
                    SELECT i.id, i.catalog_key, i.label, p.id AS purchase_order, i.position,
                           ROW_NUMBER() OVER (PARTITION BY COALESCE(i.catalog_key, 'custom:' || LOWER(i.label))
                                              ORDER BY p.id DESC, i.position DESC) AS label_rank
                    FROM purchase_item i JOIN purchase p ON p.id = i.purchase_id
                    WHERE p.household_id = ? AND p.status = 'CONFIRMED'
                ) products WHERE label_rank = 1 ORDER BY purchase_order DESC, position DESC LIMIT 5
                """, (row, number) -> new RecentProduct(row.getLong(1), row.getString(2), row.getString(3)), viewer.getHousehold().getId());
    }

    private void requireViewer(Member viewer) {
        jdbc.queryForObject("""
                SELECT id FROM member WHERE household_id = ? AND id = ? AND telegram_user_id = ?
                """, Long.class, viewer.getHousehold().getId(), viewer.getId(), viewer.getTelegramUserId());
    }

    public record ReceiptDetail(long id, long buyerId, String buyer, String status, boolean shared,
                                java.math.BigDecimal total, Instant time, Long expenseId, PurchaseDraft.Photo photo,
                                List<PurchaseDraft.Line> lines, List<String> participants) {}
    public record ReceiptSummary(long id, java.math.BigDecimal total, String status) {}
    public record RecentProduct(long id, String catalogKey, String label) {}

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
