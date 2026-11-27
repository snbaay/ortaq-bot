package kz.homebot.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProductCatalog {
    public static final int PAGE_SIZE = 8;
    public static final String CUSTOM = "custom";
    private static final List<Category> CATEGORIES = List.of(
            category("vegetables", "tomato", "cucumber", "potato", "onion", "carrot", "cabbage", "garlic"),
            category("grain", "pasta", "rice", "buckwheat", "flour"),
            category("dairy", "milk", "ayran", "cheese", "sourcream"),
            category("eggs", "eggs"),
            category("bread", "bread"),
            category("meat", "meat", "chicken"),
            category("basics", "oil", "sugar", "salt", "tea"),
            category("drinks", "drink"),
            category("home", "cleaner", "soap", "paper", "binbag"));

    public List<Category> categories() {
        return CATEGORIES;
    }

    public Optional<Category> category(String key) {
        return CATEGORIES.stream().filter(category -> category.key().equals(key)).findFirst();
    }

    public boolean contains(String product) {
        return CATEGORIES.stream().anyMatch(category -> category.products().contains(product));
    }

    private static Category category(String key, String... products) {
        return new Category(key, List.of(products));
    }

    public record Category(String key, List<String> products) {
        public List<String> page(int number) {
            if (number < 0 || number >= pageCount()) {
                return List.of();
            }
            int start = number * PAGE_SIZE;
            return products.subList(start, Math.min(products.size(), start + PAGE_SIZE));
        }

        public int pageCount() {
            return (products.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        }
    }
}
