package com.bhel.hrm.server;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.service.AuthService;
import com.bhel.hrm.server.service.EmployeeService;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import com.bhel.hrm.server.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HRMServiceImplTest {

    private static final String PASSWORD = "Correct-horse-1";

    private final FakeDatabase database = new FakeDatabase();
    private final CountingHasher hasher = new CountingHasher();
    private HRMServiceImpl service;

    @BeforeEach
    void setUp() {
        AuthService auth = new AuthService(database, hasher, new SessionManager(new MutableClock()));
        EmployeeService employees = new EmployeeService(database, hasher, auth);
        service = new HRMServiceImpl(database, auth, employees);
        database.addEmployee(hasher, "Alice", "Tan", "AB123456", Role.EMPLOYEE, PASSWORD, false);
    }

    @Test
    void pingSucceedsWhenTheDatabaseIsReachable() {
        assertDoesNotThrow(service::ping);
    }

    @Test
    void pingFailsClearlyWhenTheDatabaseIsDown() {
        database.goDown();

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class, service::ping);

        assertEquals("The service is temporarily unavailable. Try again later.", error.getMessage());
    }

    @Test
    void loginLogoutAndChangePasswordWorkThroughTheService() throws Exception {
        Session session = service.login("AB123456", PASSWORD);

        service.changePassword(session.token(), PASSWORD, "A brand new passphrase");
        service.logout(session.token());

        assertThrows(InvalidCredentialsException.class,
                () -> service.changePassword(session.token(), "A brand new passphrase", "Yet another passphrase"));
        assertEquals("Alice Tan", service.login("AB123456", "A brand new passphrase").displayName());
    }

    @Test
    void employeeFeaturesWorkThroughTheService() throws Exception {
        database.addEmployee(hasher, "Hana", "Lim", "HR900001", Role.HR, "An HR passphrase", false);
        String hr = service.login("HR900001", "An HR passphrase").token();

        RegistrationResult registered = service.registerEmployee(hr, "Bob", "Lee", "CD123456");

        assertEquals("Bob", registered.employee().firstName());
        assertEquals(3, service.listEmployees(hr).size());
        assertEquals("Hana", service.getMyProfile(hr).firstName());
    }

    @Test
    void leaveFeaturesAreStillNotImplementedYet() {
        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> service.getLeaveTypes("any-token"));

        assertEquals("Not implemented yet", error.getMessage());
    }
}
