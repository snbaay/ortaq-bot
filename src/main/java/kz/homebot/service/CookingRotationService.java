package kz.homebot.service;

import kz.homebot.domain.CookingDuty;
import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.repository.CookingDutyRepository;
import kz.homebot.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class CookingRotationService {
    private final CookingDutyRepository duties;
    private final MemberRepository members;
    private final NextCookPicker picker;

    public CookingRotationService(CookingDutyRepository duties, MemberRepository members, NextCookPicker picker) {
        this.duties = duties; this.members = members; this.picker = picker;
    }

    @Transactional
    public CookingDuty getOrCreateDuty(Household household, LocalDate date) {
        return duties.findByHouseholdAndDutyDate(household, date).orElseGet(() -> {
            Long previousId = duties.findFirstByHouseholdAndDutyDateBeforeOrderByDutyDateDesc(household, date)
                    .map(d -> d.getMember().getId()).orElse(null);
            Member next = findNext(household, previousId);
            return duties.save(new CookingDuty(household, next, date));
        });
    }

    @Transactional
    public CookingDuty skip(Household household, LocalDate date) {
        CookingDuty duty = getOrCreateDuty(household, date);
        duty.setMember(findNext(household, duty.getMember().getId()));
        return duties.save(duty);
    }

    @Transactional(readOnly = true)
    public List<Member> preview(Household household, Long todayCookId, int nextDays) {
        List<Member> ordered = members.findByHousehold(household).stream()
                .sorted(java.util.Comparator.comparing(Member::getId)).toList();
        List<Long> ids = ordered.stream().map(Member::getId).toList();
        java.util.ArrayList<Member> result = new java.util.ArrayList<>();
        Long current = todayCookId;
        for (int i = 0; i < nextDays; i++) {
            current = picker.next(ids, current);
            Long chosen = current;
            result.add(ordered.stream().filter(m -> m.getId().equals(chosen)).findFirst().orElseThrow());
        }
        return result;
    }

    private Member findNext(Household household, Long previousId) {
        List<Member> ordered = members.findByHousehold(household).stream()
                .sorted(java.util.Comparator.comparing(Member::getId)).toList();
        long id = picker.next(ordered.stream().map(Member::getId).toList(), previousId);
        return ordered.stream().filter(m -> m.getId() == id).findFirst().orElseThrow();
    }
}
