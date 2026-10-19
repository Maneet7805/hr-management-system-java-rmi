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
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ProfilePanelTest {

    private static final Session SESSION = new Session("token-1", 7L, "Alice Tan", Role.EMPLOYEE, false);
    private static final Employee ALICE = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
            "012-3456789", "alice@bhel.example", "1 Jalan Satu", false);

    private final RecordingNavigator navigator = new RecordingNavigator();

    private ProfilePanel loaded(StubService stub) throws Exception {
        ProfilePanel panel = onEdt(() -> new ProfilePanel(stub.service(), navigator, SESSION));
        awaitTrue("profile loaded", () -> panel.saveButton().isEnabled());
        return panel;
    }

    @Test
    void theProfileIsLoadedAndShown() throws Exception {
        StubService stub = new StubService().returns("getMyProfile", ALICE);

        ProfilePanel panel = loaded(stub);

        assertEquals("Alice Tan", onEdt(() -> panel.nameLabel().getText()));
        assertEquals("AB123456", onEdt(() -> panel.icLabel().getText()));
        assertEquals("Employee", onEdt(() -> panel.roleLabel().getText()));
        assertEquals("012-3456789", onEdt(() -> panel.phoneField().getText()));
        assertEquals("alice@bhel.example", onEdt(() -> panel.emailField().getText()));
        assertEquals("1 Jalan Satu", onEdt(() -> panel.addressField().getText()));
        assertEquals(List.of("token-1"), stub.lastArguments("getMyProfile"));
    }

    @Test
    void theFormCannotBeUsedUntilTheProfileHasLoaded() throws Exception {
        StubService stub = new StubService().returns("getMyProfile", ALICE);
        ProfilePanel panel = onEdt(() -> new ProfilePanel(stub.service(), navigator, SESSION));

        assertFalse(onEdt(() -> panel.saveButton().isEnabled()));
        awaitTrue("profile loaded", () -> panel.saveButton().isEnabled());
    }

    @Test
    void savingSendsTheTypedValuesAndConfirms() throws Exception {
        Employee updated = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
                "011-1111111", "new@bhel.example", "2 Jalan Dua", false);
        StubService stub = new StubService().returns("getMyProfile", ALICE).returns("updateMyContact", updated);
        ProfilePanel panel = loaded(stub);

        onEdt(() -> {
            panel.phoneField().setText("011-1111111");
            panel.emailField().setText("new@bhel.example");
            panel.addressField().setText("2 Jalan Dua");
            panel.saveButton().doClick();
        });
        awaitTrue("saved message", () -> panel.statusText().getText().equals("Saved."));

        assertEquals(List.of("token-1", "011-1111111", "new@bhel.example", "2 Jalan Dua"),
                stub.lastArguments("updateMyContact"));
        assertTrue(onEdt(() -> panel.saveButton().isEnabled()));
    }

    @Test
    void theServersReasonForRefusingIsShownAndTheTypedValuesStay() throws Exception {
        StubService stub = new StubService().returns("getMyProfile", ALICE)
                .returns("updateMyContact", new ValidationException("Email must contain '@'."));
        ProfilePanel panel = loaded(stub);

        onEdt(() -> {
            panel.emailField().setText("not-an-email");
            panel.saveButton().doClick();
        });
        awaitTrue("the reason", () -> panel.statusText().getText().equals("Email must contain '@'."));

        assertEquals("not-an-email", onEdt(() -> panel.emailField().getText()));
        assertTrue(onEdt(() -> panel.saveButton().isEnabled()), "the person can correct it and try again");
    }

    @Test
    void anExpiredSessionLeadsBackToTheLoginScreen() throws Exception {
        StubService stub = new StubService().returns("getMyProfile", ALICE)
                .returns("updateMyContact", new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        ProfilePanel panel = loaded(stub);

        onEdt(() -> panel.saveButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void whenTheProfileCannotBeLoadedTheMessageIsShownAndSavingStaysOff() throws Exception {
        StubService stub = new StubService().returns("getMyProfile",
                new com.bhel.hrm.common.exception.ServiceUnavailableException("The service is temporarily unavailable. Try again later."));
        ProfilePanel panel = onEdt(() -> new ProfilePanel(stub.service(), navigator, SESSION));

        awaitTrue("the message", () -> panel.statusText().getText().startsWith("The service is temporarily unavailable"));

        assertFalse(onEdt(() -> panel.saveButton().isEnabled()), "nothing to save before the details are known");
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        StubService stub = new StubService().returns("getMyProfile", ALICE).returns("updateMyContact", ALICE);
        ProfilePanel panel = loaded(stub);

        onEdt(() -> panel.saveButton().doClick());
        awaitTrue("saved", () -> panel.statusText().getText().equals("Saved."));

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
    }
}
