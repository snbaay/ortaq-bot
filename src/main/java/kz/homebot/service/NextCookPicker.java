package kz.homebot.service;

import java.util.List;

public class NextCookPicker {
    public long next(List<Long> memberIds, Long previousId) {
        if (memberIds == null || memberIds.isEmpty()) {
            throw new IllegalArgumentException("No members");
        }
        List<Long> sorted = memberIds.stream().sorted().toList();
        if (previousId == null) {
            return sorted.get(0);
        }
        int index = sorted.indexOf(previousId);
        return sorted.get(index < 0 ? 0 : (index + 1) % sorted.size());
    }
}
