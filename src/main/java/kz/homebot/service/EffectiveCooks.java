package kz.homebot.service;

import java.util.LinkedHashSet;
import java.util.List;

public class EffectiveCooks {
    public record Swap(Long fromMemberId, Long toMemberId) {}

    public List<Long> apply(List<Long> scheduled, List<Swap> swaps) {
        LinkedHashSet<Long> result = new LinkedHashSet<>(scheduled);
        for (Swap swap : swaps) {
            if (result.remove(swap.fromMemberId())) result.add(swap.toMemberId());
        }
        return List.copyOf(result);
    }
}
