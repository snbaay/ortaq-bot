package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.ExpenseRepository;
import kz.homebot.repository.ExpenseShareRepository;
import kz.homebot.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BalanceService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final MemberRepository memberRepository;

    public BalanceService(ExpenseRepository expenseRepository,
                          ExpenseShareRepository expenseShareRepository,
                          MemberRepository memberRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.memberRepository = memberRepository;
    }

    /** A member paired with their balance. */
    public record MemberBalance(Member member, BigDecimal balance) {}

    /**
     * Returns balance for each member: paid - owed (non-deleted expenses).
     * Sorted by balance descending (biggest creditor first).
     */
    @Transactional(readOnly = true)
    public List<MemberBalance> getMemberBalances(Household household) {
        List<Member> members = memberRepository.findByHousehold(household);

        Map<Long, BigDecimal> balances = new HashMap<>();
        for (Member m : members) {
            balances.put(m.getId(), BigDecimal.ZERO.setScale(2));
        }

        for (Object[] row : expenseRepository.sumPaidByMember(household)) {
            Long memberId = (Long) row[0];
            BigDecimal paid = (BigDecimal) row[1];
            balances.merge(memberId, paid, BigDecimal::add);
        }

        for (Object[] row : expenseShareRepository.sumSharesByMember(household)) {
            Long memberId = (Long) row[0];
            BigDecimal owed = (BigDecimal) row[1];
            balances.merge(memberId, owed.negate(), BigDecimal::add);
        }

        List<MemberBalance> result = new ArrayList<>();
        for (Member m : members) {
            result.add(new MemberBalance(m, balances.getOrDefault(m.getId(), BigDecimal.ZERO.setScale(2))));
        }
        result.sort((a, b) -> b.balance().compareTo(a.balance()));
        return result;
    }

    /**
     * Returns raw balance map memberId -> balance for DebtCalculator.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getBalances(Household household) {
        List<MemberBalance> memberBalances = getMemberBalances(household);
        Map<Long, BigDecimal> map = new HashMap<>();
        for (MemberBalance mb : memberBalances) {
            map.put(mb.member().getId(), mb.balance());
        }
        return map;
    }
}
