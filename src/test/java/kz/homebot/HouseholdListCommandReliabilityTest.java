package kz.homebot;

import kz.homebot.bot.*;
import kz.homebot.repository.NoteRepository;
import kz.homebot.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration")
class HouseholdListCommandReliabilityTest {
    @MockBean HomeTelegramBot bot;
    @MockBean DigestJob digestJob;
    @Autowired MemberRegistrationService registration;
    @Autowired NoteService notes;
    @Autowired NoteRepository noteRows;
    @Autowired ChoreService chores;
    @Autowired ShoppingService shopping;
    @Autowired NoteHandler noteHandler;
    @Autowired ChoreHandler choreHandler;
    @Autowired ShoppingHandler shoppingHandler;

    @Test
    void detachedAuthorsAndChoreMembersRenderAndAnotherAuthorCannotDelete() {
        var ali = registration.registerMember(-941L, "Home", 941L, "Ali", null);
        var dana = registration.registerMember(-941L, "Home", 942L, "Dana", null);
        var note = notes.add(ali.getHousehold(), ali, "Shared note");
        chores.add(ali.getHousehold(), ali, "Cleaned table");
        List<String> replies = new ArrayList<>();

        noteHandler.handle(command(-941L, 942L, "Dana", "jazba", "", replies));
        choreHandler.handle(command(-941L, 942L, "Dana", "ister", "", replies));
        choreHandler.handle(command(-941L, 942L, "Dana", "songy", "table", replies));
        noteHandler.handle(command(-941L, 942L, "Dana", "jazba_oshiru", note.getId().toString(), replies));

        assertThat(replies.get(0)).contains("Ali", "Shared note");
        assertThat(replies.get(1)).contains("Ali", "Cleaned table");
        assertThat(replies.get(2)).contains("Ali", "Cleaned table");
        assertThat(noteRows.findById(note.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(replies.get(3)).contains("рұқсатың жоқ");
        assertThat(dana.getId()).isNotEqualTo(ali.getId());
    }

    @Test
    void boughtShoppingItemNeverCreatesExpenseAndCannotBeBoughtAcrossHouseholds() {
        var buyer = registration.registerMember(-942L, "Home", 943L, "Buyer", null);
        var outsider = registration.registerMember(-943L, "Other", 944L, "Outsider", null);
        var item = shopping.add(buyer.getHousehold(), buyer, "milk").get(0);
        List<String> replies = new ArrayList<>();

        shoppingHandler.handle(command(-943L, 944L, "Outsider", "satyp", item.getId().toString(), replies));
        shoppingHandler.handle(command(-942L, 943L, "Buyer", "satyp", item.getId().toString(), replies));

        assertThat(replies).hasSize(2);
        assertThat(shopping.open(buyer.getHousehold())).isEmpty();
        assertThat(outsider.getHousehold().getId()).isNotEqualTo(buyer.getHousehold().getId());
    }

    private CommandContext command(long chat, long user, String name, String cmd, String args, List<String> replies) {
        return new CommandContext(chat, "group", new Sender(user, name, null), cmd, args,
                List.of(), replies::add);
    }
}
