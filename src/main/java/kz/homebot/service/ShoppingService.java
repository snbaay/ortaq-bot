package kz.homebot.service;

import kz.homebot.domain.Household;
import kz.homebot.domain.Member;
import kz.homebot.domain.ShoppingItem;
import kz.homebot.repository.ShoppingItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
public class ShoppingService {
    private final ShoppingItemRepository items;
    public ShoppingService(ShoppingItemRepository items) { this.items = items; }

    @Transactional
    public List<ShoppingItem> add(Household household, Member member, String input) {
        List<String> names = Arrays.stream(input.split(",", -1)).map(String::trim).toList();
        if (names.isEmpty() || names.stream().anyMatch(s -> s.isEmpty() || s.length() > 60)
                || items.countByHouseholdAndBoughtFalse(household) + names.size() > 50) {
            throw new IllegalArgumentException("Shopping list limit exceeded or invalid item");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Almaty"));
        return names.stream().map(s -> items.save(new ShoppingItem(household, s, member, now))).toList();
    }

    @Transactional(readOnly = true)
    public List<ShoppingItem> open(Household household) {
        return items.findByHouseholdAndBoughtFalseOrderByIdAsc(household);
    }

    @Transactional
    public boolean buy(Household household, Member buyer, Long id) {
        var item = items.findByIdAndHousehold(id, household).orElse(null);
        if (item == null || item.isBought()) return false;
        item.markBought(buyer, LocalDateTime.now(ZoneId.of("Asia/Almaty")));
        items.save(item);
        return true;
    }
}
