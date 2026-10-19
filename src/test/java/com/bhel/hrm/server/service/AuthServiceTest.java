package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.PermissionDeniedException;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.security.SessionManager.ActiveSession;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import com.bhel.hrm.server.testsupport.MutableClock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private static final String PASSWORD = "Correct-horse-1";

    private final MutableClock clock = new MutableClock();
    private final FakeDatabase database = new FakeDatabase();
    private final CountingHasher hasher = new CountingHasher();
    private final SessionManager sessions = new SessionManager(clock);
    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(database, hasher, sessions);
        database.addEmployee(hasher, "Alice", "Tan", "AB123456", Role.EMPLOYEE, PASSWORD, false);
        database.addEmployee(hasher, "Hana", "Lim", "HR900001", Role.HR, PASSWORD, false);
        database.addEmployee(hasher, "Newbie", "Ng", "NEW00001", Role.EMPLOYEE, "Temp-pass-9", true);
    }

    // ---- login --------------------------------------------------------------------------------

    @Test
    void loginReturnsTheSessionDetails() throws Exception {
        Session session = auth.login("AB123456", PASSWORD);

        assertEquals("Alice Tan", session.displayName());
        assertEquals(Role.EMPLOYEE, session.role());
        assertFalse(session.mustChangePassword());
        assertTrue(session.token().length() >= 22);
    }

    @Test
    void loginTrimsTheIdAndIgnoresItsCase() throws Exception {
        assertEquals("Alice Tan", auth.login("  ab123456  ", PASSWORD).displayName());
    }

    @Test
    void aTemporaryPasswordLoginReportsThatTheOwnerMustChangeIt() throws Exception {
        assertTrue(auth.login("NEW00001", "Temp-pass-9").mustChangePassword());
    }

    @Test
    void aWrongPasswordIsRefusedWithTheGenericMessage() {
        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> auth.login("AB123456", "wrong password"));

        assertEquals("Wrong ID or password.", error.getMessage());
    }

    @Test
    void anUnknownIdGivesTheSameMessageAndStillCostsOneHashCheck() {
        int before = hasher.verifications();

        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> auth.login("NOBODY99", "whatever password"));

        assertEquals("Wrong ID or password.", error.getMessage());
        assertEquals(before + 1, hasher.verifications(), "unknown IDs must take as long as wrong passwords");
    }

    @Test
    void blankOrOverlongInputIsRefusedWithoutAnyHashing() {
        int before = hasher.verifications();

        assertThrows(InvalidCredentialsException.class, () -> auth.login(null, PASSWORD));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("   ", PASSWORD));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("AB123456", null));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("AB123456", ""));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("A".repeat(21), PASSWORD));
        assertThrows(InvalidCredentialsException.class, () -> auth.login("AB123456", "x".repeat(129)));

        assertEquals(before, hasher.verifications());
    }

    @Test
    void whenTheDatabaseIsDownLoginSaysSoWithoutSqlDetails() {
        database.goDown();

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> auth.login("AB123456", PASSWORD));

        assertEquals("The service is temporarily unavailable. Try again later.", error.getMessage());
    }

    // ---- sessions and roles -------------------------------------------------------------------

    @Test
    void requireSessionTellsWhoIsCalling() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        ActiveSession caller = auth.requireSession(token);

        assertEquals("Alice Tan", caller.displayName());
        assertEquals(Role.EMPLOYEE, caller.role());
    }

    @Test
    void anUnknownOrMissingTokenMeansTheSessionHasExpired() {
        InvalidCredentialsException unknown = assertThrows(InvalidCredentialsException.class,
                () -> auth.requireSession("no-such-token"));
        assertThrows(InvalidCredentialsException.class, () -> auth.requireSession(null));

        assertEquals("Your session has expired. Please log in again.", unknown.getMessage());
    }

    @Test
    void aSessionExpiresAfter30MinutesWithoutUse() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        clock.advance(Duration.ofMinutes(31));

        assertThrows(InvalidCredentialsException.class, () -> auth.requireSession(token));
    }

    @Test
    void someoneOnATemporaryPasswordCannotDoAnythingExceptChangeIt() throws Exception {
        String token = auth.login("NEW00001", "Temp-pass-9").token();

        PermissionDeniedException error = assertThrows(PermissionDeniedException.class,
                () -> auth.requireSession(token));

        assertEquals("You must change your temporary password before doing anything else.", error.getMessage());
    }

    @Test
    void requireRoleAcceptsTheRightRoleAndRefusesTheOther() throws Exception {
        String employeeToken = auth.login("AB123456", PASSWORD).token();
        String hrToken = auth.login("HR900001", PASSWORD).token();

        assertEquals(Role.HR, auth.requireRole(hrToken, Role.HR).role());
        assertEquals(Role.EMPLOYEE, auth.requireRole(employeeToken, Role.EMPLOYEE).role());
        PermissionDeniedException notHr = assertThrows(PermissionDeniedException.class,
                () -> auth.requireRole(employeeToken, Role.HR));
        PermissionDeniedException notEmployee = assertThrows(PermissionDeniedException.class,
                () -> auth.requireRole(hrToken, Role.EMPLOYEE));
        assertEquals("Only HR staff can do this.", notHr.getMessage());
        assertEquals("Only employees can do this.", notEmployee.getMessage());
    }

    // ---- logout -------------------------------------------------------------------------------

    @Test
    void logoutEndsTheSession() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        auth.logout(token);

        assertThrows(InvalidCredentialsException.class, () -> auth.requireSession(token));
    }

    @Test
    void logoutWithAnUnknownTokenIsHarmless() {
        auth.logout("no-such-token");
        auth.logout(null);
    }

    // ---- change password ----------------------------------------------------------------------

    @Test
    void afterAChangeTheNewPasswordWorksAndTheOldOneDoesNot() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        auth.changePassword(token, PASSWORD, "A brand new passphrase");

        assertEquals("Alice Tan", auth.login("AB123456", "A brand new passphrase").displayName());
        assertThrows(InvalidCredentialsException.class, () -> auth.login("AB123456", PASSWORD));
    }

    @Test
    void changingATemporaryPasswordClearsTheFlagAndUnlocksTheSameSession() throws Exception {
        Session first = auth.login("NEW00001", "Temp-pass-9");

        auth.changePassword(first.token(), "Temp-pass-9", "My own new passphrase");

        assertEquals(Role.EMPLOYEE, auth.requireSession(first.token()).role());
        assertFalse(auth.login("NEW00001", "My own new passphrase").mustChangePassword());
    }

    @Test
    void changingThePasswordEndsTheOtherSessionsOfTheSamePerson() throws Exception {
        String current = auth.login("AB123456", PASSWORD).token();
        String other = auth.login("AB123456", PASSWORD).token();
        String someoneElse = auth.login("HR900001", PASSWORD).token();

        auth.changePassword(current, PASSWORD, "A brand new passphrase");

        assertEquals(Role.EMPLOYEE, auth.requireSession(current).role());
        assertThrows(InvalidCredentialsException.class, () -> auth.requireSession(other));
        assertEquals(Role.HR, auth.requireSession(someoneElse).role());
    }

    @Test
    void aWrongCurrentPasswordIsRefusedAndNothingChanges() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        InvalidCredentialsException error = assertThrows(InvalidCredentialsException.class,
                () -> auth.changePassword(token, "not my password", "A brand new passphrase"));

        assertEquals("Your current password is wrong.", error.getMessage());
        assertEquals("Alice Tan", auth.login("AB123456", PASSWORD).displayName());
    }

    @Test
    void aWeakNewPasswordIsRefusedWithTheReason() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        ValidationException error = assertThrows(ValidationException.class,
                () -> auth.changePassword(token, PASSWORD, "short"));

        assertTrue(error.getMessage().contains("at least 8"), error.getMessage());
        assertEquals("Alice Tan", auth.login("AB123456", PASSWORD).displayName());
    }

    @Test
    void theNewPasswordMayNotBeTheSameAsTheCurrentOne() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();

        ValidationException error = assertThrows(ValidationException.class,
                () -> auth.changePassword(token, PASSWORD, PASSWORD));

        assertEquals("The new password must be different from the current one.", error.getMessage());
    }

    @Test
    void changePasswordNeedsALiveSession() {
        assertThrows(InvalidCredentialsException.class,
                () -> auth.changePassword("no-such-token", PASSWORD, "A brand new passphrase"));
    }

    @Test
    void whenTheDatabaseIsDownChangePasswordSaysSoWithoutSqlDetails() throws Exception {
        String token = auth.login("AB123456", PASSWORD).token();
        database.goDown();

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> auth.changePassword(token, PASSWORD, "A brand new passphrase"));

        assertEquals("The service is temporarily unavailable. Try again later.", error.getMessage());
        assertNotEquals(null, error.getMessage());
    }
}
