package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ScriptedService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class LoginPanelTest {

    private static final Session EMPLOYEE = new Session("token-1", 7L, "Alice Tan", Role.EMPLOYEE, false);
    private static final Session TEMPORARY = new Session("token-2", 8L, "Bob Lee", Role.EMPLOYEE, true);

    private final RecordingNavigator navigator = new RecordingNavigator();

    private LoginPanel panelFor(HRMService service) throws Exception {
        return onEdt(() -> new LoginPanel(service, navigator));
    }

    private static void type(LoginPanel panel, String id, String password) throws Exception {
        onEdt(() -> {
            panel.idField().setText(id);
            panel.passwordField().setText(password);
        });
    }

    private static void clickLogin(LoginPanel panel) throws Exception {
        onEdt(() -> panel.loginButton().doClick());
    }

    @Test
    void emptyFieldsAreRefusedWithoutCallingTheServer() throws Exception {
        ScriptedService scripted = new ScriptedService();
        LoginPanel panel = panelFor(scripted.service());

        clickLogin(panel);

        assertEquals("Enter your ID and password.", onEdt(() -> panel.statusText().getText()));
        assertEquals(0, scripted.calls());
    }

    @Test
    void successGoesToTheHomeScreenAndClearsThePassword() throws Exception {
        ScriptedService scripted = new ScriptedService(EMPLOYEE);
        LoginPanel panel = panelFor(scripted.service());
        type(panel, "  AB123456  ", "a password");

        clickLogin(panel);
        awaitTrue("home screen", () -> navigator.homeFor != null);

        assertEquals(EMPLOYEE, navigator.homeFor);
        assertEquals("", onEdt(() -> new String(panel.passwordField().getPassword())));
        assertEquals("AB123456", scripted.argumentsOfCall(0).get(0), "the ID is trimmed before it is sent");
        assertEquals("a password", scripted.argumentsOfCall(0).get(1));
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        ScriptedService scripted = new ScriptedService(EMPLOYEE);
        LoginPanel panel = panelFor(scripted.service());
        type(panel, "AB123456", "a password");

        clickLogin(panel);
        awaitTrue("home screen", () -> navigator.homeFor != null);

        assertEquals(1, scripted.calls());
        assertEquals(0, scripted.callsOnEventThread(), "a server call on the event thread freezes the window");
    }

    @Test
    void aTemporaryPasswordGoesToTheChangePasswordScreenNotHome() throws Exception {
        ScriptedService scripted = new ScriptedService(TEMPORARY);
        LoginPanel panel = panelFor(scripted.service());
        type(panel, "CD123456", "Temp-pass-9");

        clickLogin(panel);
        awaitTrue("change password screen", () -> navigator.changePasswordFor != null);

        assertEquals(TEMPORARY, navigator.changePasswordFor);
        assertNull(navigator.homeFor);
    }

    @Test
    void aWrongPasswordShowsTheMessageClearsThePasswordAndAllowsAnotherTry() throws Exception {
        ScriptedService scripted = new ScriptedService(new InvalidCredentialsException("Wrong ID or password."));
        LoginPanel panel = panelFor(scripted.service());
        type(panel, "AB123456", "not the password");

        clickLogin(panel);
        awaitTrue("the message", () -> panel.statusText().getText().equals("Wrong ID or password."));

        assertEquals("", onEdt(() -> new String(panel.passwordField().getPassword())));
        assertTrue(onEdt(() -> panel.loginButton().isEnabled()));
        assertTrue(onEdt(() -> panel.idField().isEnabled()));
        assertNull(navigator.homeFor);
        assertNull(navigator.changePasswordFor);
    }

    @Test
    void anUnreachableServerShowsTheServersOwnReadableSentence() throws Exception {
        String sentence = "Cannot reach the HR server at localhost:1099. Check that it is running and try again.";
        ScriptedService scripted = new ScriptedService(new ServiceUnavailableException(sentence));
        LoginPanel panel = panelFor(scripted.service());
        type(panel, "AB123456", "a password");

        clickLogin(panel);
        awaitTrue("the message", () -> panel.statusText().getText().equals(sentence));
    }

    @Test
    void theFormIsDisabledWhileTheCallRunsAndEnabledAgainAfterwards() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        HRMService slow = (HRMService) Proxy.newProxyInstance(HRMService.class.getClassLoader(),
                new Class<?>[] {HRMService.class}, (proxy, method, args) -> {
                    release.await(10, TimeUnit.SECONDS);
                    return EMPLOYEE;
                });
        LoginPanel panel = panelFor(slow);
        type(panel, "AB123456", "a password");

        clickLogin(panel);

        assertFalse(onEdt(() -> panel.loginButton().isEnabled()), "no double submit while waiting");
        assertFalse(onEdt(() -> panel.idField().isEnabled()));
        release.countDown();
        awaitTrue("home screen", () -> navigator.homeFor != null);
        assertTrue(onEdt(() -> panel.loginButton().isEnabled()));
    }

    @Test
    void aMessageFromElsewhereCanBeShownOnTheLoginScreen() throws Exception {
        LoginPanel panel = panelFor(new ScriptedService().service());

        onEdt(() -> panel.showMessage("Your session has expired. Please log in again."));

        assertEquals("Your session has expired. Please log in again.", onEdt(() -> panel.statusText().getText()));
    }
}
