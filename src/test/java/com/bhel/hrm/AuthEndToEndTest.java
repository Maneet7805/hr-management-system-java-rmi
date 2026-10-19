package com.bhel.hrm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ClientSettings;
import com.bhel.hrm.client.ServerConnection;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.PermissionDeniedException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Nothing is faked here: real MySQL, the real secure server, the real 600,000-round password hashing,
 * and a client that connects the way the GUI will.
 */
@Timeout(60)
class AuthEndToEndTest extends DatabaseTestBase {

    private static final String FIRST_PASSWORD = "First HR passphrase";
    private static final String SECOND_PASSWORD = "Second HR passphrase";

    @TempDir
    static Path dir;

    private static final PasswordHasher HASHER = new Pbkdf2PasswordHasher();
    private static Identity identity;
    private static int registryPort;
    private static RmiServer server;

    /** Runs after the base class has connected to MySQL and installed the tables. */
    @BeforeAll
    static void startServer() throws IOException {
        identity = TlsTestFiles.createIdentity(dir, "server", "localhost");
        registryPort = TlsTestFiles.freePort();
        int objectPort = TlsTestFiles.freePort();
        AuthService auth = new AuthService(database, HASHER, new SessionManager(Clock.systemUTC()));
        server = RmiServer.start(new ServerSettings(registryPort, objectPort, identity.keyStore(),
                TlsTestFiles.PASSWORD, "localhost"),
                new HRMServiceImpl(database, auth, new EmployeeService(database, HASHER, auth)));
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    private static HRMService client() throws Exception {
        return ServerConnection.connect(
                new ClientSettings("localhost", registryPort, identity.trustStore(), TlsTestFiles.PASSWORD));
    }

    private static void seedHr() throws Exception {
        new HrSeeder(database, HASHER).createHr("Hana", "Lim", "HR900001", FIRST_PASSWORD);
    }

    @Test
    void pingWorksThroughTheWholeStack() throws Exception {
        assertDoesNotThrow(client()::ping);
    }

    @Test
    void anHrAccountCanLogInChangeItsPasswordAndLogInAgain() throws Exception {
        seedHr();
        HRMService service = client();

        Session session = service.login("HR900001", FIRST_PASSWORD);
        service.changePassword(session.token(), FIRST_PASSWORD, SECOND_PASSWORD);

        assertEquals(Role.HR, session.role());
        assertFalse(session.mustChangePassword());
        InvalidCredentialsException oldPassword = assertThrows(InvalidCredentialsException.class,
                () -> service.login("HR900001", FIRST_PASSWORD));
        assertEquals("Wrong ID or password.", oldPassword.getMessage());
        assertEquals("Hana Lim", service.login("HR900001", SECOND_PASSWORD).displayName());
    }

    @Test
    void aWrongPasswordAndAnUnknownIdLookTheSameToTheClient() throws Exception {
        seedHr();
        HRMService service = client();

        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> service.login("HR900001", "not the password"));
        InvalidCredentialsException unknownId = assertThrows(InvalidCredentialsException.class,
                () -> service.login("NOBODY99", "not the password"));

        assertEquals(wrongPassword.getMessage(), unknownId.getMessage());
    }

    @Test
    void hrRegistersAnEmployeeWhoThenChangesThePasswordAndKeepsTheirOwnDetails() throws Exception {
        seedHr();
        HRMService service = client();
        String hr = service.login("HR900001", FIRST_PASSWORD).token();

        RegistrationResult registered = service.registerEmployee(hr, "Alice", "Tan", "AB123456");
        Session temporary = service.login("AB123456", registered.temporaryPassword());

        assertTrue(temporary.mustChangePassword());
        assertThrows(PermissionDeniedException.class, () -> service.getMyProfile(temporary.token()));

        service.changePassword(temporary.token(), registered.temporaryPassword(), SECOND_PASSWORD);
        Employee updated = service.updateMyContact(temporary.token(), "012-3456789", "alice@bhel.example", "1 Jalan Satu");
        FamilyMember spouse = service.addFamilyMember(temporary.token(), "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));

        assertEquals("012-3456789", updated.phone());
        assertEquals(List.of(spouse), service.getMyFamily(temporary.token()));
        assertThrows(PermissionDeniedException.class, () -> service.listEmployees(temporary.token()));
        assertEquals(2, service.listEmployees(hr).size());
    }

    @Test
    void theDatabaseHoldsAHashAndNeverThePassword() throws Exception {
        seedHr();
        byte[] plain = FIRST_PASSWORD.getBytes(StandardCharsets.UTF_8);
        String sql = "SELECT password_hash, password_salt, hash_iterations FROM employee WHERE ic_passport = ?";

        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "HR900001");
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                byte[] hash = rows.getBytes("password_hash");
                byte[] salt = rows.getBytes("password_salt");

                assertEquals(32, hash.length);
                assertEquals(16, salt.length);
                assertEquals(600_000, rows.getInt("hash_iterations"));
                assertFalse(Arrays.equals(plain, hash));
                assertNotEquals(FIRST_PASSWORD, new String(hash, StandardCharsets.ISO_8859_1));
            }
        } catch (SQLException e) {
            throw new AssertionError("could not read the employee row", e);
        }
    }
}
