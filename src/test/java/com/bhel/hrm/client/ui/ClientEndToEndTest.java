package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ClientSettings;
import com.bhel.hrm.client.ResilientService;
import com.bhel.hrm.client.ServerConnection;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.net.TlsTestFiles.Identity;
import com.bhel.hrm.server.HRMServiceImpl;
import com.bhel.hrm.server.RmiServer;
import com.bhel.hrm.server.ServerSettings;
import com.bhel.hrm.server.db.DatabaseTestBase;
import com.bhel.hrm.server.security.PasswordHasher;
import com.bhel.hrm.server.security.Pbkdf2PasswordHasher;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.service.AuthService;
import com.bhel.hrm.server.service.EmployeeService;
import com.bhel.hrm.server.service.HrSeeder;
import com.bhel.hrm.server.service.LeaveService;
import com.bhel.hrm.server.service.ReportService;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The screens, the retrying wrapper, the secure connection, the server and MySQL, all real. The screens are
 * driven by code (typing into fields and clicking buttons) instead of by a person.
 */
@Timeout(90)
class ClientEndToEndTest extends DatabaseTestBase {

    private static final PasswordHasher HASHER = new Pbkdf2PasswordHasher(1000);

    @TempDir
    static Path dir;

    private static Identity identity;
    private static int registryPort;
    private static RmiServer server;

    @BeforeAll
    static void startServer() throws IOException {
        identity = TlsTestFiles.createIdentity(dir, "server", "localhost");
        registryPort = TlsTestFiles.freePort();
        int objectPort = TlsTestFiles.freePort();
        AuthService auth = new AuthService(database, HASHER, new SessionManager(Clock.systemUTC()));
        server = RmiServer.start(new ServerSettings(registryPort, objectPort, identity.keyStore(),
                TlsTestFiles.PASSWORD, "localhost"),
                new HRMServiceImpl(database, auth, new EmployeeService(database, HASHER, auth),
                        new LeaveService(database, auth, Clock.systemDefaultZone()), new ReportService(database, auth)));
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    private static HRMService newClient() {
        ClientSettings settings = new ClientSettings("localhost", registryPort, identity.trustStore(),
                TlsTestFiles.PASSWORD);
        return ResilientService.create(() -> ServerConnection.connect(settings));
    }

    private static void logInThroughTheScreen(ScreenHost host, String id, String password) throws Exception {
        LoginPanel login = assertInstanceOf(LoginPanel.class, onEdt(host::currentScreen));
        onEdt(() -> {
            login.idField().setText(id);
            login.passwordField().setText(password);
            login.loginButton().doClick();
        });
    }

    @Test
    void anHrAccountLogsInThroughTheScreenSeesTheHomeScreenAndLogsOut() throws Exception {
        new HrSeeder(database, HASHER).createHr("Hana", "Lim", "HR900001", "An HR passphrase");
        ScreenHost host = onEdt(() -> new ScreenHost(newClient()));

        logInThroughTheScreen(host, "HR900001", "An HR passphrase");
        awaitTrue("home screen", () -> host.currentScreen() instanceof HomePanel);

        HomePanel home = (HomePanel) onEdt(host::currentScreen);
        assertTrue(onEdt(() -> home.welcomeLabel().getText()).contains("Hana Lim (HR staff)"));
        onEdt(() -> home.logOutButton().doClick());
        awaitTrue("login screen", () -> host.currentScreen() instanceof LoginPanel);
    }

    @Test
    void aWrongPasswordShowsTheServersMessageOnTheLoginScreen() throws Exception {
        new HrSeeder(database, HASHER).createHr("Hana", "Lim", "HR900001", "An HR passphrase");
        ScreenHost host = onEdt(() -> new ScreenHost(newClient()));

        logInThroughTheScreen(host, "HR900001", "not the password");
        LoginPanel login = (LoginPanel) onEdt(host::currentScreen);
        awaitTrue("the message", () -> login.statusText().getText().equals("Wrong ID or password."));
    }

    @Test
    void aNewEmployeeIsSentToChooseAPasswordAndThenReachesTheHomeScreen() throws Exception {
        new HrSeeder(database, HASHER).createHr("Hana", "Lim", "HR900001", "An HR passphrase");
        HRMService admin = newClient();
        String hrToken = admin.login("HR900001", "An HR passphrase").token();
        RegistrationResult registered = admin.registerEmployee(hrToken, "Alice", "Tan", "AB123456");
        ScreenHost host = onEdt(() -> new ScreenHost(newClient()));

        logInThroughTheScreen(host, "AB123456", registered.temporaryPassword());
        awaitTrue("change password screen", () -> host.currentScreen() instanceof ChangePasswordPanel);

        ChangePasswordPanel change = (ChangePasswordPanel) onEdt(host::currentScreen);
        onEdt(() -> {
            change.currentField().setText(registered.temporaryPassword());
            change.newField().setText("My own passphrase");
            change.confirmField().setText("My own passphrase");
            change.changeButton().doClick();
        });
        awaitTrue("home screen", () -> host.currentScreen() instanceof HomePanel);

        HomePanel home = (HomePanel) onEdt(host::currentScreen);
        assertTrue(onEdt(() -> home.welcomeLabel().getText()).contains("Alice Tan (Employee)"));
        // The new password really is the one in use now.
        assertEquals("Alice Tan", newClient().login("AB123456", "My own passphrase").displayName());
    }

    @Test
    void anEmployeeKeepsTheirDetailsAndAppliesForLeaveThroughTheScreens() throws Exception {
        new HrSeeder(database, HASHER).createHr("Hana", "Lim", "HR900001", "An HR passphrase");
        HRMService admin = newClient();
        String hrToken = admin.login("HR900001", "An HR passphrase").token();
        RegistrationResult registered = admin.registerEmployee(hrToken, "Alice", "Tan", "AB123456");
        int year = java.time.LocalDate.now().getYear();

        ScreenHost host = onEdt(() -> new ScreenHost(newClient()));
        logInThroughTheScreen(host, "AB123456", registered.temporaryPassword());
        awaitTrue("change password screen", () -> host.currentScreen() instanceof ChangePasswordPanel);
        ChangePasswordPanel change = (ChangePasswordPanel) onEdt(host::currentScreen);
        onEdt(() -> {
            change.currentField().setText(registered.temporaryPassword());
            change.newField().setText("My own passphrase");
            change.confirmField().setText("My own passphrase");
            change.changeButton().doClick();
        });
        awaitTrue("home screen", () -> host.currentScreen() instanceof HomePanel);
        HomePanel home = (HomePanel) onEdt(host::currentScreen);
        LeavePanel leave = (LeavePanel) onEdt(() -> home.tabs().getComponentAt(0));
        ProfilePanel profile = (ProfilePanel) onEdt(() -> home.tabs().getComponentAt(1));
        FamilyPanel family = (FamilyPanel) onEdt(() -> home.tabs().getComponentAt(2));

        // My details
        awaitTrue("details loaded", () -> profile.saveButton().isEnabled());
        assertEquals("Alice Tan", onEdt(() -> profile.nameLabel().getText()));
        onEdt(() -> {
            profile.phoneField().setText("012-3456789");
            profile.emailField().setText("alice@bhel.example");
            profile.addressField().setText("1 Jalan Satu");
            profile.saveButton().doClick();
        });
        awaitTrue("details saved", () -> profile.statusText().getText().equals("Saved."));

        // Family
        onEdt(() -> {
            family.nameField().setText("Bob Tan");
            family.relationshipField().setText("Spouse");
            family.dobField().setText("1990-05-17");
            family.addButton().doClick();
        });
        awaitTrue("family member listed", () -> family.table().getRowCount() == 1);

        // Leave: first too many days, then a good request
        awaitTrue("leave loaded", () -> leave.typeCombo().getItemCount() == 4 && leave.balancesTable().getRowCount() == 4);
        onEdt(() -> {
            leave.startField().setText(year + "-01-01");
            leave.endField().setText(year + "-01-20");
            leave.applyButton().doClick();
        });
        awaitTrue("the refusal", () -> leave.statusText().getText().startsWith("Not enough Annual leave left"));
        onEdt(() -> {
            leave.startField().setText(year + "-03-10");
            leave.endField().setText(year + "-03-12");
            leave.reasonField().setText("Family trip");
            leave.applyButton().doClick();
        });
        awaitTrue("application listed", () -> leave.applicationsTable().getRowCount() == 1);

        assertEquals("Application sent: 3 days of Annual leave, waiting for HR's decision.",
                onEdt(() -> leave.statusText().getText()));
        awaitTrue("balance updated", () -> "11".equals(leave.balancesTable().getValueAt(0, 4)));
        // The server really has all of it.
        assertEquals("012-3456789", admin.listEmployees(hrToken).stream()
                .filter(e -> e.icPassport().equals("AB123456")).findFirst().orElseThrow().phone());
        var pending = admin.listPendingLeave(hrToken);
        assertEquals(1, pending.size());
        assertEquals("Alice Tan", pending.get(0).employeeName());
        assertEquals("Family trip", pending.get(0).reason());
    }}
