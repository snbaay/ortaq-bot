package kz.homebot.service;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogTest {
    @Test
    void categoriesAndProductsAreOrderedLocalizedAndPageBounded() throws Exception {
        var catalog = new ProductCatalog();
        var labels = new Properties();
        try (var input = new InputStreamReader(getClass().getResourceAsStream("/messages_kk.properties"),
                StandardCharsets.UTF_8)) {
            labels.load(input);
        }
        assertThat(catalog.categories()).extracting(ProductCatalog.Category::key).containsExactly(
                "vegetables", "grain", "dairy", "eggs", "bread", "meat", "basics", "drinks", "home");
        assertThat(catalog.category("vegetables").orElseThrow().page(0)).containsExactly(
                "tomato", "cucumber", "potato", "onion", "carrot", "cabbage", "garlic");
        assertThat(catalog.categories()).allSatisfy(category -> {
            assertThat(labels.getProperty("catalog.category." + category.key())).isNotBlank();
            assertThat(category.page(0)).hasSizeLessThanOrEqualTo(8).allSatisfy(product ->
                    assertThat(labels.getProperty("catalog.product." + product)).isNotBlank());
        });
        var longCategory = new ProductCatalog.Category("example", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
        assertThat(longCategory.page(0)).hasSize(8);
        assertThat(longCategory.page(1)).containsExactly("9");
        assertThat(longCategory.page(2)).isEmpty();
        assertThat(labels.getProperty("catalog.custom")).isEqualTo("✍️ Басқа өнім");
        assertThat(catalog.contains("missing")).isFalse();
    }
}
