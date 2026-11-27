package kz.homebot;

import kz.homebot.bot.HomeTelegramBot;
import kz.homebot.bot.CommandContext;
import kz.homebot.bot.RepaymentHandler;
import kz.homebot.bot.Sender;
import kz.homebot.bot.TelegramOutput;
import kz.homebot.bot.callback.CallbackRouter;
import kz.homebot.bot.interaction.InteractionReplyRouter;
import kz.homebot.bot.menu.MenuViews;
import kz.homebot.domain.Member;
import kz.homebot.service.DigestJob;
import kz.homebot.service.MemberRegistrationService;
import kz.homebot.service.PurchaseDraft;
import kz.homebot.service.PurchaseService;
import kz.homebot.service.ExpenseService;
import kz.homebot.service.ShoppingService;
import kz.homebot.service.RepaymentRequestService;
import kz.homebot.service.RepaymentRequestService.Decision;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    @Autowired RepaymentRequestService repayments;
    @Autowired RepaymentHandler repaymentCommand;
    @Autowired MenuViews menus;
    @Autowired CallbackRouter callbacks;
    @Autowired InteractionReplyRouter promptReplies;

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
    void pendingRepaymentNeedsItsRecipientAndRacingTapsPostExactlyOneSettlement() throws Exception {
        var sender = registration.registerMember(-215L, "Repay", 84L, "Sender", null);
        var recipient = registration.registerMember(-215L, "Repay", 85L, "Recipient", null);
        var other = registration.registerMember(-215L, "Repay", 86L, "Other", null);
        var remote = registration.registerMember(-216L, "Other", 85L, "Recipient", null);
        var amount = new BigDecimal("17.25");
        var pending = repayments.submit(sender, recipient.getId(), amount, "test_bot:100");
        assertThat(repayments.submit(sender, recipient.getId(), amount, "test_bot:100").id()).isEqualTo(pending.id());
        assertThat(pending.expenseId()).isNull();
        assertThat(expenses.getHistory(sender.getHousehold())).isEmpty();
        assertThat(repayments.claimDelivery(pending.id(), false)).isTrue();
        assertThat(repayments.claimDelivery(pending.id(), false)).isFalse();
        repayments.delivered(pending.id(), false, 15);
        assertThat(repayments.decide(sender, -215, pending.token(), 15, Decision.ACCEPT)).isEmpty();
        assertThat(repayments.decide(other, -215, pending.token(), 15, Decision.ACCEPT)).isEmpty();
        assertThat(repayments.decide(remote, -216, pending.token(), 15, Decision.ACCEPT)).isEmpty();
        assertThat(repayments.decide(recipient, -215, pending.token(), 16, Decision.ACCEPT)).isEmpty();
        assertThat(expenses.getHistory(sender.getHousehold())).isEmpty();

        var go = new CountDownLatch(1);
        RepaymentRequestService.Request accepted;
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<RepaymentRequestService.Request> accept = () -> {
                go.await();
                return repayments.decide(recipient, -215, pending.token(), 15, Decision.ACCEPT).orElseThrow();
            };
            var first = executor.submit(accept);
            var second = executor.submit(accept);
            go.countDown();
            accepted = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS).expenseId()).isEqualTo(accepted.expenseId());
        }
        assertThat(expenses.getHistory(sender.getHousehold())).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_share WHERE expense_id = ?", Integer.class,
                accepted.expenseId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT share_amount FROM expense_share WHERE expense_id = ? AND member_id = ?",
                BigDecimal.class, accepted.expenseId(), recipient.getId())).isEqualByComparingTo(amount);

        assertThat(expenses.deleteEntryById(sender.getHousehold(), sender, accepted.expenseId())).isEqualTo(ExpenseService.DeleteResult.MANAGED);
        assertThat(repayments.forExpense(other, accepted.expenseId())).isEmpty();
        var proposed = repayments.correct(sender, accepted.id(), "duplicate", "test_bot:101").orElseThrow();
        assertThat(repayments.correct(recipient, accepted.id(), "other", "test_bot:102")).isEmpty();
        repayments.delivered(proposed.id(), false, 21);
        assertThat(repayments.decide(recipient, -215, proposed.token(), 21, Decision.REJECT).orElseThrow().status())
                .isEqualTo("REJECTED");
        assertThat(expenses.getHistory(sender.getHousehold())).hasSize(1);
        var reverse = repayments.correct(recipient, accepted.id(), "wrong amount", "test_bot:103").orElseThrow();
        repayments.delivered(reverse.id(), false, 22);
        assertThat(repayments.decide(recipient, -215, reverse.token(), 22, Decision.ACCEPT)).isEmpty();
        assertThat(expenses.getHistory(sender.getHousehold())).hasSize(1);
        assertThat(repayments.decide(sender, -215, reverse.token(), 22, Decision.ACCEPT).orElseThrow().status())
                .isEqualTo("CONFIRMED");
        assertThat(repayments.decide(sender, -215, reverse.token(), 22, Decision.ACCEPT).orElseThrow().status())
                .isEqualTo("CONFIRMED");
        assertThat(expenses.getHistory(sender.getHousehold())).isEmpty();
        assertThat(repayments.forMember(sender, accepted.id()).orElseThrow().status()).isEqualTo("VOID");
    }

    @Test
    void expiredAndSenderCancelledRepaymentsNeverPostAnExpense() {
        var sender = registration.registerMember(-217L, "Repay", 84L, "Sender", null);
        var recipient = registration.registerMember(-217L, "Repay", 85L, "Recipient", null);
        var expiry = repayments.submit(sender, recipient.getId(), new BigDecimal("13.83"), "test_bot:104");
        repayments.delivered(expiry.id(), false, 30);
        jdbc.update("UPDATE repayment_request SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' DAY WHERE id = ?", expiry.id());
        assertThat(repayments.decide(recipient, -217, expiry.token(), 30, Decision.ACCEPT).orElseThrow().status()).isEqualTo("EXPIRED");
        var cancelled = repayments.submit(sender, recipient.getId(), new BigDecimal("10.00"), "test_bot:105");
        repayments.delivered(cancelled.id(), false, 31);
        assertThat(repayments.decide(recipient, -217, cancelled.token(), 31, Decision.CANCEL)).isEmpty();
        assertThat(repayments.decide(sender, -217, cancelled.token(), 31, Decision.CANCEL).orElseThrow().status()).isEqualTo("VOID");
        assertThat(expenses.getHistory(sender.getHousehold())).isEmpty();
    }

    @Test
    void commandAndButtonBothUseAConfirmationPreviewAndCreateOnlyPendingRequests() {
        long chat = -218L;
        var sender = registration.registerMember(chat, "Repay interface", 84L, "Sender", null);
        var recipient = registration.registerMember(chat, "Repay interface", 85L, "Recipient", null);
        var telegram = new CapturedTelegram(chat);
        var ctx = new CommandContext(chat, "group", new Sender(84L, "Sender", null), "tolem", "Recipient 1250,50",
                List.of(), text -> telegram.output.send(chat, text, null), "/tolem Recipient 1250,50",
                new CommandContext.CommandSource("test_bot", 299, sender, telegram.output));
        repaymentCommand.handle(ctx);
        assertThat(pendingRequests(sender)).isZero();
        tap(telegram, sender, 300, "confirm");
        assertThat(pendingRequests(sender)).isEqualTo(1);

        menus.main(sender, chat, "menu", telegram.output);
        tap(telegram, sender, 301, "finance");
        tap(telegram, sender, 302, "repay");
        tap(telegram, sender, 303, "to:" + Long.toString(recipient.getId(), 36));
        Message amount = new Message();
        amount.setChat(telegram.chat);
        amount.setFrom(new User(84L, "Sender", false));
        amount.setReplyToMessage(telegram.messages.getLast());
        amount.setText("250,50");
        promptReplies.route(amount, telegram.output);
        assertThat(pendingRequests(sender)).isEqualTo(1);
        tap(telegram, sender, 304, "confirm");

        assertThat(pendingRequests(sender)).isEqualTo(2);
        assertThat(expenses.getHistory(sender.getHousehold())).isEmpty();
    }

    private int pendingRequests(Member sender) {
        return jdbc.queryForObject("SELECT count(*) FROM repayment_request WHERE household_id = ? AND status = 'PENDING'",
                Integer.class, sender.getHousehold().getId());
    }

    private void tap(CapturedTelegram telegram, Member sender, int updateId, String action) {
        Message screen = telegram.messages.getLast();
        String data = screen.getReplyMarkup().getKeyboard().stream().flatMap(List::stream)
                .map(button -> button.getCallbackData()).filter(token -> token.endsWith(":" + action))
                .findFirst().orElseThrow();
        var query = new CallbackQuery();
        query.setId("interface-" + updateId);
        query.setFrom(new User(sender.getTelegramUserId(), sender.getDisplayName(), false));
        query.setMessage(screen);
        query.setData(data);
        var update = new Update();
        update.setUpdateId(updateId);
        update.setCallbackQuery(query);
        callbacks.route("test_bot", update, telegram.output);
    }

    private static final class CapturedTelegram {
        private final TelegramOutput output = mock(TelegramOutput.class);
        private final List<Message> messages = new ArrayList<>();
        private final Chat chat;

        private CapturedTelegram(long id) {
            chat = Chat.builder().id(id).type("group").build();
            var bot = new User(10L, "Bot", true);
            bot.setUserName("test_bot");
            when(output.send(eq(id), anyString(), any())).thenAnswer(call -> {
                var message = new Message();
                message.setChat(chat);
                message.setFrom(bot);
                message.setMessageId(messages.size() + 1);
                message.setText(call.getArgument(1));
                if (call.getArgument(2) instanceof InlineKeyboardMarkup markup) {
                    message.setReplyMarkup(markup);
                }
                messages.add(message);
                return message;
            });
        }
    }

    @Test
    void springStartsWithFlywayThenHibernateValidation() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE", Integer.class))
                .isEqualTo(1);
    }
}
