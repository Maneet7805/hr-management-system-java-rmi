package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import com.bhel.hrm.server.testsupport.MutableClock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** No MySQL needed: the database stand-in is told to fail, to check what the client would see. */
class LeaveServiceUnavailableTest {

    private static final String SAFE_MESSAGE = "The service is temporarily unavailable. Try again later.";

    private final FakeDatabase database = new FakeDatabase();
    private final CountingHasher hasher = new CountingHasher();
    private final MutableClock clock = new MutableClock();
    private LeaveService leave;
    private ReportService reports;
    private String employeeToken;
    private String hrToken;

    @BeforeEach
    void setUp() throws Exception {
        AuthService auth = new AuthService(database, hasher, new SessionManager(clock));
        leave = new LeaveService(database, auth, clock);
        reports = new ReportService(database, auth);
        database.addEmployee(hasher, "Alice", "Tan", "AB123456", Role.EMPLOYEE, "Correct-horse-1", false);
        database.addEmployee(hasher, "Hana", "Lim", "HR900001", Role.HR, "An HR passphrase", false);
        employeeToken = auth.login("AB123456", "Correct-horse-1").token();
        hrToken = auth.login("HR900001", "An HR passphrase").token();
        database.goDown();
    }

    @Test
    void employeeLeaveMethodsGiveTheSafeMessage() {
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> leave.getLeaveTypes(employeeToken)).getMessage());
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> leave.getMyLeaveBalances(employeeToken, 2026)).getMessage());
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class, () -> leave.applyForLeave(
                employeeToken, 1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null)).getMessage());
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> leave.getMyLeaveApplications(employeeToken)).getMessage());
    }

    @Test
    void hrMethodsGiveTheSafeMessageToo() {
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> leave.listPendingLeave(hrToken)).getMessage());
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> leave.decideLeave(hrToken, 1L, true)).getMessage());
        assertEquals(SAFE_MESSAGE, assertThrows(ServiceUnavailableException.class,
                () -> reports.generateYearlyReport(hrToken, 1L, 2026)).getMessage());
    }
}
