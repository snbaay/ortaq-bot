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
import kz.homebot.config.Limits;

@Service
public class ShoppingService {
    private final ShoppingItemRepository items;
    public ShoppingService(ShoppingItemRepository items) { this.items = items; }

    @Transactional
    public List<ShoppingItem> add(Household household, Member member, String input) {
        if (!sameHousehold(household, member) || input == null) {
            throw new IllegalArgumentException("Invalid shopping member or input");
        }
        List<String> names = Arrays.stream(input.split(",", -1)).map(String::trim).toList();
        if (names.isEmpty() || names.stream().anyMatch(s -> s.isEmpty() || s.length() > Limits.SHOPPING_ITEM)
                || items.countByHouseholdAndBoughtFalse(household) + names.size() > Limits.SHOPPING_OPEN) {
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
        if (!sameHousehold(household, buyer) || id == null) return false;
        var item = items.findByIdAndHousehold(id, household).orElse(null);
        if (item == null || item.isBought()) return false;
        item.markBought(buyer, LocalDateTime.now(ZoneId.of("Asia/Almaty")));
        items.save(item);
        return true;
    }

    private boolean sameHousehold(Household household, Member member) {
        return household != null && member != null && member.getHousehold() != null
                && household.getId() != null && household.getId().equals(member.getHousehold().getId());
    }
}
