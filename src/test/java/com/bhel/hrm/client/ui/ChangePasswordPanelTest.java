package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.client.ScriptedService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class ChangePasswordPanelTest {

    private static final Session TEMPORARY = new Session("token-2", 8L, "Bob Lee", Role.EMPLOYEE, true);

    private final RecordingNavigator navigator = new RecordingNavigator();

    private ChangePasswordPanel panelFor(ScriptedService scripted) throws Exception {
        return onEdt(() -> new ChangePasswordPanel(scripted.service(), navigator, TEMPORARY));
    }

    private static void fill(ChangePasswordPanel panel, String current, String next, String confirm) throws Exception {
        onEdt(() -> {
            panel.currentField().setText(current);
            panel.newField().setText(next);
            panel.confirmField().setText(confirm);
        });
    }

    private static void clickChange(ChangePasswordPanel panel) throws Exception {
        onEdt(() -> panel.changeButton().doClick());
    }

    private static String status(ChangePasswordPanel panel) throws Exception {
        return onEdt(() -> panel.statusText().getText());
    }

    @Test
    void aTemporaryPasswordIsExplainedOnScreen() throws Exception {
        ChangePasswordPanel panel = panelFor(new ScriptedService());

        assertTrue(onEdt(() -> panel.noticeLabel().getText()).contains("temporary password"));
    }

    @Test
    void emptyFieldsAreRefusedWithoutCallingTheServer() throws Exception {
        ScriptedService scripted = new ScriptedService();
        ChangePasswordPanel panel = panelFor(scripted);
        fill(panel, "Temp-pass-9", "", "");

        clickChange(panel);

        assertEquals("Fill in all three fields.", status(panel));
        assertEquals(0, scripted.calls());
    }

    @Test
    void aConfirmationThatDoesNotMatchIsRefusedWithoutCallingTheServer() throws Exception {
        ScriptedService scripted = new ScriptedService();
        ChangePasswordPanel panel = panelFor(scripted);
        fill(panel, "Temp-pass-9", "My own passphrase", "My own passphrasf");

        clickChange(panel);

        assertEquals("The new password and its confirmation are not the same.", status(panel));
        assertEquals(0, scripted.calls());
    }

    @Test
    void successSendsTheRightValuesAndGoesHomeWithTheTemporaryFlagCleared() throws Exception {
        ScriptedService scripted = new ScriptedService((Object) null);
        ChangePasswordPanel panel = panelFor(scripted);
        fill(panel, "Temp-pass-9", "My own passphrase", "My own passphrase");

        clickChange(panel);
        awaitTrue("home screen", () -> navigator.homeFor != null);

        assertEquals(java.util.List.of("token-2", "Temp-pass-9", "My own passphrase"), scripted.argumentsOfCall(0));
        assertEquals("Bob Lee", navigator.homeFor.displayName());
        assertFalse(navigator.homeFor.mustChangePassword());
        assertEquals("token-2", navigator.homeFor.token(), "the same login continues");
    }

    @Test
    void theServerIsNeverCalledOnTheEventThreadWhenChangingOrLoggingOut() throws Exception {
        ScriptedService change = new ScriptedService((Object) null);
        ChangePasswordPanel panel = panelFor(change);
        fill(panel, "Temp-pass-9", "My own passphrase", "My own passphrase");
        clickChange(panel);
        awaitTrue("home screen", () -> navigator.homeFor != null);

        ScriptedService logout = new ScriptedService((Object) null);
        ChangePasswordPanel other = panelFor(logout);
        onEdt(() -> other.logOutButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(0, change.callsOnEventThread());
        assertEquals(0, logout.callsOnEventThread());
    }

    @Test
    void aWeakPasswordRefusedByTheServerStaysOnTheScreenWithTheReasonAndClearsTheFields() throws Exception {
        ScriptedService scripted = new ScriptedService(new ValidationException("Password must be at least 8 characters."));
        ChangePasswordPanel panel = panelFor(scripted);
        fill(panel, "Temp-pass-9", "short", "short");

        clickChange(panel);
        awaitTrue("the reason", () -> panel.statusText().getText().equals("Password must be at least 8 characters."));

        assertEquals("", onEdt(() -> new String(panel.newField().getPassword())));
        assertTrue(onEdt(() -> panel.changeButton().isEnabled()));
        assertNull(navigator.homeFor);
    }

    @Test
    void aWrongCurrentPasswordIsShownAndDoesNotSendTheUserToTheLoginScreen() throws Exception {
        ScriptedService scripted = new ScriptedService(new InvalidCredentialsException("Your current password is wrong."));
        ChangePasswordPanel panel = panelFor(scripted);
        fill(panel, "not it", "My own passphrase", "My own passphrase");

        clickChange(panel);
        awaitTrue("the message", () -> panel.statusText().getText().equals("Your current password is wrong."));

        assertEquals(0, navigator.loginShown);
    }

    @Test
    void anExpiredSessionGoesBackToTheLoginScreenWithAnExplanation() throws Exception {
        ScriptedService scripted = new ScriptedService(new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        ChangePasswordPanel panel = panelFor(scripted);
        fill(panel, "Temp-pass-9", "My own passphrase", "My own passphrase");

        clickChange(panel);
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void logOutEndsTheSessionAndShowsTheLoginScreen() throws Exception {
        ScriptedService scripted = new ScriptedService((Object) null);
        ChangePasswordPanel panel = panelFor(scripted);

        onEdt(() -> panel.logOutButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(java.util.List.of("logout"), scripted.methodsCalled());
        assertNull(navigator.loginMessage);
    }

    // ---- a person who is not on a temporary password may change the password and walk away ----

    private static final Session NORMAL = new Session("token-3", 7L, "Alice Tan", Role.EMPLOYEE, false);

    @Test
    void whenChangingByChoiceThereIsACancelButtonAndNoTemporaryPasswordNotice() throws Exception {
        ChangePasswordPanel panel = onEdt(() -> new ChangePasswordPanel(new ScriptedService().service(), navigator, NORMAL));

        assertFalse(onEdt(() -> panel.noticeLabel().getText()).contains("temporary"));
        assertTrue(onEdt(() -> panel.cancelButton().isVisible()));
        assertFalse(onEdt(() -> panel.logOutButton().isVisible()), "log out is only offered when a change is forced");
    }

    @Test
    void cancelReturnsToTheHomeScreenWithoutCallingTheServer() throws Exception {
        ScriptedService scripted = new ScriptedService();
        ChangePasswordPanel panel = onEdt(() -> new ChangePasswordPanel(scripted.service(), navigator, NORMAL));

        onEdt(() -> panel.cancelButton().doClick());

        assertEquals(NORMAL, navigator.homeFor);
        assertEquals(0, scripted.calls());
    }

    @Test
    void whenTheChangeIsForcedThereIsNoCancelButton() throws Exception {
        ChangePasswordPanel panel = panelFor(new ScriptedService());

        assertFalse(onEdt(() -> panel.cancelButton().isVisible()), "a temporary password must be replaced");
        assertTrue(onEdt(() -> panel.logOutButton().isVisible()));
    }
}
