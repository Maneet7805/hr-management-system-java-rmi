package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.client.StubService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class FamilyPanelTest {

    private static final Session SESSION = new Session("token-1", 7L, "Alice Tan", Role.EMPLOYEE, false);
    private static final FamilyMember SPOUSE = new FamilyMember(1L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));
    private static final FamilyMember CHILD = new FamilyMember(2L, "Cara Tan", "Child", null);

    private final RecordingNavigator navigator = new RecordingNavigator();
    /** What the pretend server currently holds; the panel reads it again after every change. */
    private final List<FamilyMember> stored = Collections.synchronizedList(new ArrayList<>(List.of(SPOUSE, CHILD)));

    private StubService stub() {
        return new StubService().on("getMyFamily", args -> List.copyOf(stored));
    }

    private FamilyPanel loaded(StubService stub, boolean confirmAnswer) throws Exception {
        FamilyPanel panel = onEdt(() -> new FamilyPanel(stub.service(), navigator, SESSION, new StubConfirmer(confirmAnswer)));
        awaitTrue("family loaded", () -> panel.table().getRowCount() == stored.size());
        return panel;
    }

    private static void select(FamilyPanel panel, int row) throws Exception {
        onEdt(() -> panel.table().setRowSelectionInterval(row, row));
    }

    @Test
    void theFamilyIsLoadedIntoTheTable() throws Exception {
        FamilyPanel panel = loaded(stub(), true);

        assertEquals(2, onEdt(() -> panel.table().getRowCount()));
        assertEquals("Bob Tan", onEdt(() -> panel.table().getValueAt(0, 0)));
        assertEquals("1990-05-17", onEdt(() -> panel.table().getValueAt(0, 2)));
    }

    @Test
    void selectingARowFillsTheFormAndEnablesUpdateAndRemove() throws Exception {
        FamilyPanel panel = loaded(stub(), true);
        assertFalse(onEdt(() -> panel.updateButton().isEnabled()));
        assertFalse(onEdt(() -> panel.removeButton().isEnabled()));

        select(panel, 0);

        assertEquals("Bob Tan", onEdt(() -> panel.nameField().getText()));
        assertEquals("Spouse", onEdt(() -> panel.relationshipField().getText()));
        assertEquals("1990-05-17", onEdt(() -> panel.dobField().getText()));
        assertTrue(onEdt(() -> panel.updateButton().isEnabled()));
        assertTrue(onEdt(() -> panel.removeButton().isEnabled()));
    }

    @Test
    void addingSendsTheFormReloadsTheListAndClearsTheForm() throws Exception {
        StubService stub = stub().on("addFamilyMember", args -> {
            FamilyMember added = new FamilyMember(3L, (String) args[1], (String) args[2], (LocalDate) args[3]);
            stored.add(added);
            return added;
        });
        FamilyPanel panel = loaded(stub, true);

        onEdt(() -> {
            panel.nameField().setText("Dan Tan");
            panel.relationshipField().setText("Child");
            panel.dobField().setText("2015-01-02");
            panel.addButton().doClick();
        });
        awaitTrue("three rows", () -> panel.table().getRowCount() == 3);

        assertEquals(List.of("token-1", "Dan Tan", "Child", LocalDate.of(2015, 1, 2)), stub.lastArguments("addFamilyMember"));
        assertEquals("", onEdt(() -> panel.nameField().getText()));
        assertEquals("Added Dan Tan.", onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void aDateThatIsNotARealDateIsRefusedWithoutCallingTheServer() throws Exception {
        StubService stub = stub();
        FamilyPanel panel = loaded(stub, true);

        onEdt(() -> {
            panel.nameField().setText("Dan Tan");
            panel.relationshipField().setText("Child");
            panel.dobField().setText("2 Jan 2015");
            panel.addButton().doClick();
        });

        assertEquals("Date of birth must be a real date in the form yyyy-MM-dd, for example 2026-03-15.",
                onEdt(() -> panel.statusText().getText()));
        assertEquals(0, stub.count("addFamilyMember"));
    }

    @Test
    void aReasonFromTheServerIsShownAndNothingIsCleared() throws Exception {
        StubService stub = stub().returns("addFamilyMember", new ValidationException("Name must not be blank"));
        FamilyPanel panel = loaded(stub, true);

        onEdt(() -> {
            panel.relationshipField().setText("Child");
            panel.addButton().doClick();
        });
        awaitTrue("the reason", () -> panel.statusText().getText().equals("Name must not be blank"));

        assertEquals("Child", onEdt(() -> panel.relationshipField().getText()));
    }

    @Test
    void updatingSendsTheSelectedMembersIdWithTheEditedValues() throws Exception {
        StubService stub = stub().on("updateFamilyMember", args -> args[1]);
        FamilyPanel panel = loaded(stub, true);
        select(panel, 0);

        onEdt(() -> {
            panel.nameField().setText("Robert Tan");
            panel.updateButton().doClick();
        });
        awaitTrue("updated message", () -> panel.statusText().getText().equals("Updated."));

        FamilyMember sent = (FamilyMember) stub.lastArguments("updateFamilyMember").get(1);
        assertEquals(new FamilyMember(1L, "Robert Tan", "Spouse", LocalDate.of(1990, 5, 17)), sent);
    }

    @Test
    void anUpdateWithABlankNameIsRefusedBeforeAnythingIsSent() throws Exception {
        StubService stub = stub();
        FamilyPanel panel = loaded(stub, true);
        select(panel, 0);

        onEdt(() -> {
            panel.nameField().setText("  ");
            panel.updateButton().doClick();
        });

        assertTrue(onEdt(() -> panel.statusText().getText()).contains("Name"));
        assertEquals(0, stub.count("updateFamilyMember"));
    }

    @Test
    void removingAsksFirstAndDoesNothingIfThePersonSaysNo() throws Exception {
        StubService stub = stub().returns("removeFamilyMember", null);
        StubConfirmer no = new StubConfirmer(false);
        FamilyPanel panel = onEdt(() -> new FamilyPanel(stub.service(), navigator, SESSION, no));
        awaitTrue("family loaded", () -> panel.table().getRowCount() == 2);
        select(panel, 1);

        onEdt(() -> panel.removeButton().doClick());

        assertEquals(1, no.questions().size());
        assertTrue(no.questions().get(0).contains("Cara Tan"), no.questions().get(0));
        assertEquals(0, stub.count("removeFamilyMember"));
        assertEquals(2, onEdt(() -> panel.table().getRowCount()));
    }

    @Test
    void removingAfterConfirmationCallsTheServerAndReloads() throws Exception {
        StubService stub = stub().on("removeFamilyMember", args -> {
            stored.removeIf(member -> member.id() == (Long) args[1]);
            return null;
        });
        FamilyPanel panel = loaded(stub, true);
        select(panel, 1);

        onEdt(() -> panel.removeButton().doClick());
        awaitTrue("one row left", () -> panel.table().getRowCount() == 1);

        assertEquals(List.of("token-1", 2L), stub.lastArguments("removeFamilyMember"));
        assertEquals("Removed.", onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void aMemberThatIsAlreadyGoneIsReportedAndTheListIsRefreshed() throws Exception {
        StubService stub = stub().returns("removeFamilyMember", new NotFoundException("Family member not found."));
        FamilyPanel panel = loaded(stub, true);
        select(panel, 0);

        onEdt(() -> panel.removeButton().doClick());
        awaitTrue("the message", () -> panel.statusText().getText().equals("Family member not found."));
    }

    @Test
    void anExpiredSessionLeadsBackToTheLoginScreen() throws Exception {
        StubService stub = stub().returns("addFamilyMember", new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        FamilyPanel panel = loaded(stub, true);

        onEdt(() -> {
            panel.nameField().setText("Dan Tan");
            panel.relationshipField().setText("Child");
            panel.addButton().doClick();
        });
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        StubService stub = stub().on("addFamilyMember", args -> {
            FamilyMember added = new FamilyMember(3L, (String) args[1], (String) args[2], null);
            stored.add(added);
            return added;
        });
        FamilyPanel panel = loaded(stub, true);
        onEdt(() -> {
            panel.nameField().setText("Dan Tan");
            panel.relationshipField().setText("Child");
            panel.addButton().doClick();
        });
        awaitTrue("three rows", () -> panel.table().getRowCount() == 3);

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
    }
}
