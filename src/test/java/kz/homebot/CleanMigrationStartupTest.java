package kz.homebot;

import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.service.DigestJob;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.PurchaseDraft;
import kz.homebot.service.PurchaseService;
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
    void springStartsWithFlywayThenHibernateValidation() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", Integer.class))
                .isEqualTo(1);
    }
}
