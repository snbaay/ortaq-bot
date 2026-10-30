package kz.homebot.service;

import kz.homebot.domain.Expense;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.ExpenseRepository;
import kz.homebot.repository.ExpenseShareRepository;
import kz.homebot.repository.MemberRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MonthlyReportService {
    public record MemberTotal(Member member, BigDecimal paid, BigDecimal consumed) {}
    public record Report(YearMonth month, BigDecimal total, List<MemberTotal> members, List<Expense> top) {}
    private final ExpenseRepository expenses;
    private final ExpenseShareRepository shares;
    private final MemberRepository members;
    public MonthlyReportService(ExpenseRepository expenses, ExpenseShareRepository shares, MemberRepository members) {
        this.expenses = expenses; this.shares = shares; this.members = members;
    }
    @Transactional(readOnly = true)
    public Report report(Household household, YearMonth month) {
        var start = month.atDay(1).atStartOfDay();
        var end = month.plusMonths(1).atDay(1).atStartOfDay();
        Map<Long, BigDecimal> paid = expenses.monthlyPaid(household, start, end).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));
        Map<Long, BigDecimal> consumed = shares.monthlyConsumed(household, start, end).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (BigDecimal) row[1]));
        List<MemberTotal> totals = members.findByHousehold(household).stream()
                .map(m -> new MemberTotal(m, paid.getOrDefault(m.getId(), BigDecimal.ZERO),
                        consumed.getOrDefault(m.getId(), BigDecimal.ZERO))).toList();
        return new Report(month, expenses.monthlyTotal(household, start, end), totals,
                expenses.monthlyTop(household, start, end, PageRequest.of(0, 3)));
    }
}
