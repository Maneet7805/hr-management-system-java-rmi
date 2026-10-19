package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import com.bhel.hrm.server.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

class HrSeederTest {

    private final FakeDatabase database = new FakeDatabase();
    private final CountingHasher hasher = new CountingHasher();
    private final HrSeeder seeder = new HrSeeder(database, hasher);

    @Test
    void createsAnHrAccountThatCanLogInWithoutHavingToChangeThePassword() throws Exception {
        Employee hr = seeder.createHr("Hana", "Lim", "HR900001", "An HR passphrase");

        AuthService auth = new AuthService(database, hasher, new SessionManager(new MutableClock()));
        Session session = auth.login("HR900001", "An HR passphrase");

        assertEquals(Role.HR, hr.role());
        assertEquals(Role.HR, session.role());
        assertFalse(session.mustChangePassword());
        assertEquals("Hana Lim", session.displayName());
    }

    @Test
    void aSecondAccountWithTheSameIcNumberIsRefusedWithAClearMessage() throws Exception {
        seeder.createHr("Hana", "Lim", "HR900001", "An HR passphrase");

        ValidationException error = assertThrows(ValidationException.class,
                () -> seeder.createHr("Other", "Person", "hr900001", "Another passphrase"));

        assertTrue(error.getMessage().contains("already exists"), error.getMessage());
    }

    @Test
    void aWeakPasswordIsRefused() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> seeder.createHr("Hana", "Lim", "HR900001", "short"));

        assertTrue(error.getMessage().contains("at least 8"), error.getMessage());
    }

    @Test
    void invalidNamesOrIcNumbersAreRefusedWithTheReason() {
        assertThrows(ValidationException.class, () -> seeder.createHr(" ", "Lim", "HR900001", "An HR passphrase"));
        assertThrows(ValidationException.class, () -> seeder.createHr("Hana", "Lim", "x", "An HR passphrase"));
    }

    @Test
    void whenTheDatabaseIsDownTheMessageIsSafe() {
        database.goDown();

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> seeder.createHr("Hana", "Lim", "HR900001", "An HR passphrase"));

        assertEquals("The service is temporarily unavailable. Try again later.", error.getMessage());
    }
}
