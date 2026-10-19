package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ScriptedService;
import com.bhel.hrm.client.StubService;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ScreenHostTest {

    private static final Session EMPLOYEE = new Session("token-1", 7L, "Alice Tan", Role.EMPLOYEE, false);
    private static final Session TEMPORARY = new Session("token-2", 8L, "Bob Lee", Role.EMPLOYEE, true);
    private static final Employee ALICE = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
            null, null, null, false);

    private static JComponent shown(ScreenHost host) throws Exception {
        return onEdt(host::currentScreen);
    }

    @Test
    void itStartsOnTheLoginScreen() throws Exception {
        ScreenHost host = onEdt(() -> new ScreenHost(new ScriptedService().service()));

        assertInstanceOf(LoginPanel.class, shown(host));
    }

    @Test
    void theNavigatorShowsTheRequestedScreen() throws Exception {
        ScreenHost host = onEdt(() -> new ScreenHost(StubService.forHome(ALICE).service()));

        onEdt(() -> host.showChangePassword(TEMPORARY));
        assertInstanceOf(ChangePasswordPanel.class, shown(host));

        onEdt(() -> host.showHome(EMPLOYEE));
        assertInstanceOf(HomePanel.class, shown(host));

        onEdt(() -> host.showLogin("Your session has expired. Please log in again."));
        LoginPanel login = assertInstanceOf(LoginPanel.class, shown(host));
        assertEquals("Your session has expired. Please log in again.", onEdt(() -> login.statusText().getText()));
    }

    @Test
    void eachVisitToTheLoginScreenStartsWithEmptyFields() throws Exception {
        ScreenHost host = onEdt(() -> new ScreenHost(StubService.forHome(ALICE).service()));
        LoginPanel first = (LoginPanel) shown(host);
        onEdt(() -> first.idField().setText("AB123456"));

        onEdt(() -> host.showHome(EMPLOYEE));
        onEdt(() -> host.showLogin(null));

        LoginPanel second = (LoginPanel) shown(host);
        assertNotSame(first, second, "nothing typed earlier may be left behind for the next person");
        assertEquals("", onEdt(() -> second.idField().getText()));
    }

    @Test
    void aSuccessfulLoginThroughTheLoginScreenLeadsToTheHomeScreen() throws Exception {
        ScreenHost host = onEdt(() -> new ScreenHost(StubService.forHome(ALICE).returns("login", EMPLOYEE).service()));
        LoginPanel login = (LoginPanel) shown(host);
        onEdt(() -> {
            login.idField().setText("AB123456");
            login.passwordField().setText("a password");
            login.loginButton().doClick();
        });

        awaitTrue("home screen", () -> host.currentScreen() instanceof HomePanel);

        assertTrue(onEdt(() -> ((HomePanel) host.currentScreen()).welcomeLabel().getText()).contains("Alice Tan"));
    }

    @Test
    void closingTheWindowEndsTheLoginOnTheServerOffTheEventThreadAndThenCarriesOn() throws Exception {
        StubService scripted = StubService.forHome(ALICE);
        ScreenHost host = onEdt(() -> new ScreenHost(scripted.service()));
        onEdt(() -> host.showHome(EMPLOYEE));
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

        onEdt(() -> host.endSession(() -> closed.set(true)));
        awaitTrue("window closed", closed::get);

        assertEquals(1, scripted.count("logout"));
        assertEquals(0, scripted.callsOnEventThread());
    }

    @Test
    void closingTheWindowWhileNobodyIsLoggedInJustCarriesOn() throws Exception {
        ScriptedService scripted = new ScriptedService();
        ScreenHost host = onEdt(() -> new ScreenHost(scripted.service()));
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

        onEdt(() -> host.endSession(() -> closed.set(true)));

        assertTrue(closed.get());
        assertEquals(0, scripted.calls());
    }

    @Test
    void logOutFromTheHomeScreenReturnsToTheLoginScreen() throws Exception {
        ScreenHost host = onEdt(() -> new ScreenHost(StubService.forHome(ALICE).service()));
        onEdt(() -> host.showHome(EMPLOYEE));
        HomePanel home = (HomePanel) shown(host);

        onEdt(() -> home.logOutButton().doClick());

        awaitTrue("login screen", () -> host.currentScreen() instanceof LoginPanel);
    }
}
