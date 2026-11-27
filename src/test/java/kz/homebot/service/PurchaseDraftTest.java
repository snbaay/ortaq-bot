package kz.homebot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseDraftTest {
    @Test
    void onlyPositiveBoundedAmountsWithTwoFractionalDigitsAreAccepted() {
        for (String invalid : new String[]{"0", "-1", "1250.000", "1e3", "100000000.01", "12,34.56", "630 су"}) {
            assertThat(PurchaseDraft.parsePrice(invalid)).isNull();
        }
        assertThat(PurchaseDraft.parsePrice("1250,50")).isEqualByComparingTo("1250.50");
        assertThat(PurchaseDraft.parsePrice("100000000.00")).isEqualByComparingTo("100000000.00");
    }

    @Test
    void countsExactTengeAndEditsOrRemovesOnlyTheSelectedLine() {
        var draft = PurchaseDraft.EMPTY.input("bread", "нан", null).price(PurchaseDraft.parsePrice("100.01"))
                .input(null, "Шырын", null).price(PurchaseDraft.parsePrice("23,29"));
        assertThat(draft.total()).isEqualByComparingTo("123.30");
        var edited = draft.input(null, "Шырын", 1).price(PurchaseDraft.parsePrice("200"));
        assertThat(edited.total()).isEqualByComparingTo("300.01");
        assertThat(edited.input("bread", "нан", 0).remove().total()).isEqualByComparingTo("200.00");
        assertThat(draft.total()).isEqualByComparingTo("123.30");
    }
}
