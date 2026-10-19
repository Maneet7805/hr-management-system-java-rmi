package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.PermissionDeniedException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.YearlyReport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest extends ServiceTestBase {

    private String hr;
    private String alice;
    private long aliceId;

    @BeforeEach
    void people() throws Exception {
        hr = hrToken();
        alice = employeeToken(hr, "Alice", "Tan", "AB123456");
        aliceId = employees.getMyProfile(alice).id();
    }

    private static LocalDate day(int year, int month, int dayOfMonth) {
        return LocalDate.of(year, month, dayOfMonth);
    }

    @Test
    void theReportHoldsTheProfileTheFamilyTheYearsLeaveAndEveryBalance() throws Exception {
        employees.updateMyContact(alice, "012-3456789", "alice@bhel.example", "1 Jalan Satu");
        FamilyMember spouse = employees.addFamilyMember(alice, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));
        LeaveApplication march = leave.applyForLeave(alice, 1L, day(2026, 3, 10), day(2026, 3, 12), "Trip");
        LeaveApplication june = leave.applyForLeave(alice, 2L, day(2026, 6, 1), day(2026, 6, 2), null);
        leave.decideLeave(hr, march.id(), true);
        leave.decideLeave(hr, june.id(), false);

        YearlyReport report = reports.generateYearlyReport(hr, aliceId, 2026);

        assertEquals(2026, report.year());
        assertEquals("Alice", report.employee().firstName());
        assertEquals("012-3456789", report.employee().phone());
        assertEquals(List.of(spouse), report.familyMembers());
        assertEquals(List.of(march.id(), june.id()),
                report.leaveApplications().stream().map(LeaveApplication::id).toList());
        assertEquals(4, report.balances().size());
        LeaveBalance annual = report.balances().get(0);
        assertEquals("Annual", annual.leaveType().name());
        assertEquals(3, annual.approvedDays());
        assertEquals(11, annual.remaining());
        assertEquals(14, report.balances().get(1).remaining(), "the rejected medical leave gives its days back");
    }

    @Test
    void onlyApplicationsThatStartInThatYearAreIncluded() throws Exception {
        LeaveApplication thisYear = leave.applyForLeave(alice, 1L, day(2026, 3, 10), day(2026, 3, 12), null);
        leave.applyForLeave(alice, 1L, day(2027, 3, 10), day(2027, 3, 12), null);
        leave.applyForLeave(alice, 1L, day(2025, 3, 10), day(2025, 3, 12), null);

        YearlyReport report = reports.generateYearlyReport(hr, aliceId, 2026);

        assertEquals(List.of(thisYear.id()), report.leaveApplications().stream().map(LeaveApplication::id).toList());
        assertEquals(11, report.balances().get(0).remaining());
    }

    @Test
    void anEmployeeWithNothingRecordedGetsEmptyListsAndFullBalances() throws Exception {
        YearlyReport report = reports.generateYearlyReport(hr, aliceId, 2026);

        assertTrue(report.familyMembers().isEmpty());
        assertTrue(report.leaveApplications().isEmpty());
        assertEquals(14, report.balances().get(0).remaining());
        assertNull(report.balances().get(3).remaining(), "Unpaid has no limit");
    }

    @Test
    void theReportCanBeMadeForAnHrAccountToo() throws Exception {
        long hrId = employees.getMyProfile(hr).id();

        Employee employee = reports.generateYearlyReport(hr, hrId, 2026).employee();

        assertEquals("Hana", employee.firstName());
    }

    @Test
    void aReportForAnEmployeeThatDoesNotExistIsNotFound() {
        assertEquals("Employee not found.", assertThrows(NotFoundException.class,
                () -> reports.generateYearlyReport(hr, 999_999L, 2026)).getMessage());
    }

    @Test
    void onlyHrCanGenerateReportsEvenAnEmployeeForThemselves() {
        assertEquals("Only HR staff can do this.", assertThrows(PermissionDeniedException.class,
                () -> reports.generateYearlyReport(alice, aliceId, 2026)).getMessage());
    }

    @Test
    void generatingAReportNeedsALiveSession() {
        assertThrows(InvalidCredentialsException.class,
                () -> reports.generateYearlyReport("no-such-token", aliceId, 2026));
    }

    @Test
    void aYearOutsideTheSensibleRangeIsRefused() {
        assertEquals("Year must be between 2000 and 2100.", assertThrows(ValidationException.class,
                () -> reports.generateYearlyReport(hr, aliceId, 1999)).getMessage());
        assertThrows(ValidationException.class, () -> reports.generateYearlyReport(hr, aliceId, 2101));
    }
}
