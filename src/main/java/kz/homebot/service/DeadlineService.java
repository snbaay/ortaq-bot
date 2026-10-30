package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.domain.PaymentDeadline;
import kz.homebot.repository.PaymentDeadlineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import kz.homebot.config.Limits;

@Service
public class DeadlineService {
    private final PaymentDeadlineRepository deadlines;
    private final PaymentCountdown countdown;
    public DeadlineService(PaymentDeadlineRepository deadlines, PaymentCountdown countdown) {
        this.deadlines = deadlines; this.countdown = countdown;
    }
    @Transactional
    public PaymentDeadline add(Household household, Member creator, int day, BigDecimal amount, String title) {
        String cleaned = title == null ? "" : title.trim();
        if (day < 1 || day > 31 || cleaned.isEmpty() || cleaned.length() > Limits.DEADLINE_TITLE
                || deadlines.countByHouseholdAndActiveTrue(household) >= Limits.DEADLINES
                || (amount != null && (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(Limits.MAX_AMOUNT) > 0 || amount.scale() > 2))) {
            throw new IllegalArgumentException("Invalid deadline");
        }
        return deadlines.save(new PaymentDeadline(household, cleaned, day,
                amount == null ? null : amount.setScale(2), creator));
    }
    @Transactional(readOnly = true)
    public List<PaymentDeadline> upcoming(Household household, LocalDate today) {
        return deadlines.findByHouseholdAndActiveTrue(household).stream()
                .sorted(Comparator.comparingLong(d -> countdown.daysLeft(today, d.getDayOfMonth()))).toList();
    }
    @Transactional
    public boolean delete(Household household, Long id) {
        var deadline = deadlines.findByIdAndHouseholdAndActiveTrue(id, household).orElse(null);
        if (deadline == null) return false;
        deadline.setActive(false);
        deadlines.save(deadline);
        return true;
    }
}
