package kz.homebot.service;

import kz.homebot.domain.Member;
import kz.homebot.repository.MemberRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Service
public class PurchaseService {
    private final MemberRepository members;

    public PurchaseService(MemberRepository members) {
        this.members = members;
    }

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
