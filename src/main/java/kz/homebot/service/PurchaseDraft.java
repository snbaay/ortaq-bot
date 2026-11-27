package kz.homebot.service;

import kz.homebot.config.Limits;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record PurchaseDraft(List<Line> lines, String product, String label, Integer index,
                            boolean personal, List<Long> participantIds, Photo photo) {
    public static final int MAX_LINES = 20;
    public static final PurchaseDraft EMPTY = new PurchaseDraft(List.of(), null, null, null, false, null, null);

    public PurchaseDraft {
        lines = List.copyOf(lines);
        participantIds = participantIds == null ? null : List.copyOf(participantIds);
    }

    public static BigDecimal parsePrice(String text) {
        if (text == null || !text.strip().matches("[0-9]{1,9}([,.][0-9]{1,2})?")) {
            return null;
        }
        var amount = new BigDecimal(text.strip().replace(',', '.')).setScale(2);
        return amount.signum() > 0 && amount.compareTo(Limits.MAX_AMOUNT) <= 0 ? amount : null;
    }

    public BigDecimal total() {
        return lines.stream().map(Line::price).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    public PurchaseDraft input(String product, String label, Integer index) {
        return new PurchaseDraft(lines, product, label, index, personal, participantIds, photo);
    }

    public PurchaseDraft price(BigDecimal price) {
        var newLines = new ArrayList<>(lines);
        if (index == null) {
            newLines.add(new Line(product, label, price));
        } else {
            var old = newLines.get(index);
            newLines.set(index, new Line(old.product(), old.label(), price));
        }
        return new PurchaseDraft(newLines, null, null, null, personal, participantIds, photo);
    }

    public PurchaseDraft remove() {
        var newLines = new ArrayList<>(lines);
        newLines.remove((int) index);
        return new PurchaseDraft(newLines, null, null, null, personal, participantIds, photo);
    }

    public PurchaseDraft sharing(boolean personal, List<Long> participants) {
        return new PurchaseDraft(lines, product, label, index, personal, participants, photo);
    }

    public PurchaseDraft attach(Photo receipt) {
        return new PurchaseDraft(lines, product, label, index, personal, participantIds, receipt);
    }

    public record Photo(String fileId, String mime, long bytes) {
        public boolean valid() {
            return fileId != null && fileId.matches("[A-Za-z0-9_-]{1,512}") && "image/jpeg".equals(mime)
                    && bytes > 0 && bytes <= 10L * 1024 * 1024;
        }
    }

    public record Line(String product, String label, BigDecimal price) {}
}
