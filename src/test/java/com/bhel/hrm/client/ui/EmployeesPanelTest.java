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
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class EmployeesPanelTest {

    private static final Session HR = new Session("token-hr", 9L, "Hana Lim", Role.HR, false);
    private static final Employee HANA = new Employee(9L, "Hana", "Lim", "HR900001", Role.HR, null, null, null, false);
    private static final String TEMPORARY = "Temp-abc-2345";

    private final RecordingNavigator navigator = new RecordingNavigator();
    private final StubClipboard clipboard = new StubClipboard();
    private final List<Employee> stored = Collections.synchronizedList(new ArrayList<>(List.of(HANA)));

    private StubService stub() {
        return new StubService().on("listEmployees", args -> List.copyOf(stored));
    }

    private StubService stubThatRegisters() {
        return stub().on("registerEmployee", args -> {
            Employee created = new Employee(20L + stored.size(), (String) args[1], (String) args[2], (String) args[3],
                    Role.EMPLOYEE, null, null, null, true);
            stored.add(created);
            return new RegistrationResult(created, TEMPORARY);
        });
    }

    private EmployeesPanel loaded(StubService stub) throws Exception {
        EmployeesPanel panel = onEdt(() -> new EmployeesPanel(stub.service(), navigator, HR, clipboard));
        awaitTrue("employees loaded", () -> panel.table().getRowCount() == stored.size());
        return panel;
    }

    private static void fill(EmployeesPanel panel, String first, String last, String ic) throws Exception {
        onEdt(() -> {
            panel.firstNameField().setText(first);
            panel.lastNameField().setText(last);
            panel.icField().setText(ic);
        });
    }

    @Test
    void everyoneIsListed() throws Exception {
        EmployeesPanel panel = loaded(stub());

        assertEquals("Hana Lim", onEdt(() -> panel.table().getValueAt(0, 0)));
        assertEquals("HR900001", onEdt(() -> panel.table().getValueAt(0, 1)));
        assertEquals("HR staff", onEdt(() -> panel.table().getValueAt(0, 2)));
    }

    @Test
    void registeringSendsTrimmedDetailsAndShowsTheTemporaryPasswordOnceAndNotInTheStatusLine() throws Exception {
        StubService stub = stubThatRegisters();
        EmployeesPanel panel = loaded(stub);
        fill(panel, "  Alice ", " Tan", " AB123456 ");

        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("password shown", () -> panel.passwordPanel().isVisible());

        assertEquals(List.of("token-hr", "Alice", "Tan", "AB123456"), stub.lastArguments("registerEmployee"));
        assertEquals(TEMPORARY, onEdt(() -> panel.passwordField().getText()));
        String status = onEdt(() -> panel.statusText().getText());
        assertTrue(status.startsWith("Registered Alice Tan."), status);
        assertFalse(status.contains(TEMPORARY), "the password belongs in its own box, not in a message that gets logged or copied");
        awaitTrue("list reloaded", () -> panel.table().getRowCount() == 2);
        assertEquals("", onEdt(() -> panel.firstNameField().getText()), "ready for the next person");
    }

    @Test
    void doneHidesAndClearsThePassword() throws Exception {
        EmployeesPanel panel = loaded(stubThatRegisters());
        fill(panel, "Alice", "Tan", "AB123456");
        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("password shown", () -> panel.passwordPanel().isVisible());

        onEdt(() -> panel.doneButton().doClick());

        assertFalse(onEdt(() -> panel.passwordPanel().isVisible()));
        assertEquals("", onEdt(() -> panel.passwordField().getText()));
    }

    @Test
    void startingAnotherRegistrationHidesTheOldPasswordEvenIfTheNewOneIsRefused() throws Exception {
        StubService stub = stubThatRegisters();
        EmployeesPanel panel = loaded(stub);
        fill(panel, "Alice", "Tan", "AB123456");
        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("password shown", () -> panel.passwordPanel().isVisible());
        stub.returns("registerEmployee", new ValidationException("An employee with that IC/passport number already exists."));

        fill(panel, "Bob", "Lee", "AB123456");
        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("the refusal", () -> panel.statusText().getText().contains("already exists"));

        assertFalse(onEdt(() -> panel.passwordPanel().isVisible()));
        assertEquals("", onEdt(() -> panel.passwordField().getText()));
    }

    @Test
    void theTemporaryPasswordDisappearsWhenThePersonLeavesTheTab() throws Exception {
        EmployeesPanel panel = loaded(stubThatRegisters());
        fill(panel, "Alice", "Tan", "AB123456");
        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("password shown", () -> panel.passwordPanel().isVisible());

        onEdt(() -> panel.setVisible(false));   // what the tabbed pane does when another tab is chosen

        awaitTrue("password cleared", () -> panel.passwordField().getText().isEmpty());
        assertFalse(onEdt(() -> panel.passwordPanel().isVisible()));
    }

    @Test
    void copyPutsThePasswordOnTheClipboard() throws Exception {
        EmployeesPanel panel = loaded(stubThatRegisters());
        fill(panel, "Alice", "Tan", "AB123456");
        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("password shown", () -> panel.passwordPanel().isVisible());

        onEdt(() -> panel.copyButton().doClick());

        assertEquals(List.of(TEMPORARY), clipboard.copied());
    }

    @Test
    void emptyFieldsAreRefusedWithoutCallingTheServer() throws Exception {
        StubService stub = stubThatRegisters();
        EmployeesPanel panel = loaded(stub);
        fill(panel, "Alice", "", "AB123456");

        onEdt(() -> panel.registerButton().doClick());

        assertEquals("Enter the first name, last name and IC/passport number.", onEdt(() -> panel.statusText().getText()));
        assertEquals(0, stub.count("registerEmployee"));
    }

    @Test
    void aRefusalIsShownTheFormKeepsWhatWasTypedAndNoPasswordAppears() throws Exception {
        StubService stub = stub().returns("registerEmployee",
                new ValidationException("An employee with that IC/passport number already exists."));
        EmployeesPanel panel = loaded(stub);
        fill(panel, "Alice", "Tan", "HR900001");

        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("the refusal", () -> panel.statusText().getText()
                .equals("An employee with that IC/passport number already exists."));

        assertEquals("HR900001", onEdt(() -> panel.icField().getText()));
        assertFalse(onEdt(() -> panel.passwordPanel().isVisible()));
        assertTrue(onEdt(() -> panel.registerButton().isEnabled()));
    }

    @Test
    void anExpiredSessionLeadsBackToTheLoginScreen() throws Exception {
        StubService stub = stub().returns("registerEmployee", new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        EmployeesPanel panel = loaded(stub);
        fill(panel, "Alice", "Tan", "AB123456");

        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        StubService stub = stubThatRegisters();
        EmployeesPanel panel = loaded(stub);
        fill(panel, "Alice", "Tan", "AB123456");
        onEdt(() -> panel.registerButton().doClick());
        awaitTrue("list reloaded", () -> panel.table().getRowCount() == 2);

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
    }
}
