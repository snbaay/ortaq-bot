package kz.homebot;

import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.service.DigestJob;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.PurchaseDraft;
import kz.homebot.service.PurchaseService;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.ShoppingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration",
        "spring.datasource.url=jdbc:h2:mem:clean_migration_startup;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class CleanMigrationStartupTest {
    @MockBean HomeTelegramBot bot;
    @MockBean DigestJob digestJob;
    @Autowired JdbcTemplate jdbc;
    @Autowired MemberRegistrationService registration;
    @Autowired PurchaseService purchases;
    @Autowired ShoppingService shopping;
    @Autowired ExpenseService expenses;

    @Test
    void racingConfirmationsReturnOneReceiptAndOneExactLedgerExpense() throws Exception {
        var buyer = registration.registerMember(-210L, "Purchase", 81L, "Buyer", null);
        registration.registerMember(-210L, "Purchase", 82L, "Roommate", null);
        var draft = PurchaseDraft.EMPTY.input("bread", "нан", null).price(new BigDecimal("10.01"));
        var go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<PurchaseService.Receipt> confirm = () -> {
                go.await();
                return purchases.confirm(buyer, "a".repeat(32), draft);
            };
            var first = executor.submit(confirm);
            var second = executor.submit(confirm);
            go.countDown();
            var receipt = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE household_id = ?", Integer.class,
                    buyer.getHousehold().getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(share_amount) FROM expense_share WHERE expense_id = ?",
                    BigDecimal.class, receipt.expenseId())).isEqualByComparingTo("10.01");
        }
        assertThat(purchases.confirm(buyer, "b".repeat(32), draft.sharing(true, null)).expenseId()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE household_id = ?", Integer.class,
                buyer.getHousehold().getId())).isEqualTo(1);
    }

    @Test
    void aShareWriteFailureRollsBackTheReceiptAndTheExpense() {
        var buyer = registration.registerMember(-211L, "Rollback", 81L, "Buyer", null);
        var draft = PurchaseDraft.EMPTY.input("bread", "нан", null).price(new BigDecimal("19.99"));
        jdbc.execute("ALTER TABLE expense_share ADD CONSTRAINT test_reject_share CHECK (share_amount <> 19.99)");
        try {
            assertThatThrownBy(() -> purchases.confirm(buyer, "c".repeat(32), draft)).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("ALTER TABLE expense_share DROP CONSTRAINT test_reject_share");
        }
        long household = buyer.getHousehold().getId();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE household_id = ?", Integer.class, household)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase WHERE household_id = ?", Integer.class, household)).isZero();
    }

    @Test
    void receiptsAndRecentPicksAreScopedAndExcludeVoids() {
        var buyer = registration.registerMember(-212L, "Receipt", 84L, "Buyer", null);
        var outsider = registration.registerMember(-213L, "Other", 84L, "Other", null);
        var draft = PurchaseDraft.EMPTY.sharing(true, null).attach(new PurchaseDraft.Photo("fake_file", "image/jpeg", 42));
        long id = 0;
        int key = 0;
        for (String item : java.util.List.of("нан", "сүт", "күріш", "сабын", "шай", "Нан", "өшкен")) {
            id = purchases.confirm(buyer, "%032x".formatted(++key),
                    draft.input(null, item, null).price(new BigDecimal("12.34"))).id();
        }
        jdbc.update("UPDATE purchase SET status = 'VOID' WHERE id = ?", id);
        assertThat(purchases.recent(buyer)).extracting(PurchaseService.RecentProduct::label)
                .containsExactly("Нан", "шай", "сабын", "күріш", "сүт");
        var receipt = purchases.receipt(buyer, id).orElseThrow();
        assertThat(receipt.total()).isEqualByComparingTo("12.34");
        assertThat(receipt.buyer()).isEqualTo("Buyer");
        assertThat(receipt.photo().fileId()).isEqualTo("fake_file");
        assertThat(receipt.lines()).extracting(PurchaseDraft.Line::label).containsExactly("өшкен");
        assertThat(purchases.receipt(outsider, id)).isEmpty();
        assertThat(purchases.recent(outsider)).isEmpty();
    }

    @Test
    void listMarkingAndBuyerOnlyVoidKeepTheLegacyLedgerConsistent() {
        var buyer = registration.registerMember(-214L, "List", 84L, "Buyer", null);
        var roommate = registration.registerMember(-214L, "List", 85L, "Roommate", null);
        var household = buyer.getHousehold();
        long listId = shopping.add(household, buyer, "сүт").getFirst().getId();
        var draft = PurchaseDraft.EMPTY.input("milk", "сүт", null).price(new BigDecimal("25.50"))
                .shopping(java.util.List.of(listId));
        var receipt = purchases.confirm(buyer, "d".repeat(32), draft);
        assertThat(shopping.open(household)).isEmpty();
        assertThat(shopping.buy(household, buyer, listId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_shopping_item WHERE purchase_id = ?",
                Integer.class, receipt.id())).isEqualTo(1);

        assertThat(purchases.voidReceipt(roommate, receipt.id())).isFalse();
        assertThat(expenses.deleteEntryById(household, buyer, receipt.expenseId())).isEqualTo(ExpenseService.DeleteResult.MANAGED);
        assertThat(purchases.voidReceipt(buyer, receipt.id())).isTrue();
        assertThat(purchases.voidReceipt(buyer, receipt.id())).isTrue();
        assertThat(purchases.receipt(buyer, receipt.id()).orElseThrow().status()).isEqualTo("VOID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE household_id = ? AND NOT deleted",
                Integer.class, household.getId())).isZero();
        assertThat(purchases.recent(buyer)).isEmpty();
    }

    @Test
    void springStartsWithFlywayThenHibernateValidation() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", Integer.class))
                .isEqualTo(1);
    }
}
