package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import com.bhel.hrm.server.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** No MySQL needed: the database stand-in is told to fail, to check what the client would see. */
class EmployeeServiceUnavailableTest {

    private static final String SAFE_MESSAGE = "The service is temporarily unavailable. Try again later.";

    private final FakeDatabase database = new FakeDatabase();
    private final CountingHasher hasher = new CountingHasher();
    private EmployeeService employees;
    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        AuthService auth = new AuthService(database, hasher, new SessionManager(new MutableClock()));
        employees = new EmployeeService(database, hasher, auth);
        database.addEmployee(hasher, "Hana", "Lim", "HR900001", Role.HR, "An HR passphrase", false);
        hrToken = auth.login("HR900001", "An HR passphrase").token();
        database.goDown();
    }

    @Test
    void registeringGivesTheSafeMessageWhenTheDatabaseIsDown() {
        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> employees.registerEmployee(hrToken, "Alice", "Tan", "AB123456"));

        assertEquals(SAFE_MESSAGE, error.getMessage());
    }

    @Test
    void listingProfileAndContactUpdateGiveTheSafeMessageToo() {
        assertEquals(SAFE_MESSAGE,
                assertThrows(ServiceUnavailableException.class, () -> employees.listEmployees(hrToken)).getMessage());
        assertEquals(SAFE_MESSAGE,
                assertThrows(ServiceUnavailableException.class, () -> employees.getMyProfile(hrToken)).getMessage());
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> employees.updateMyContact(hrToken, "1", null, null)).getMessage());
    }
}
