package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.YearlyReport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportFormatterTest {

    private static final LeaveType ANNUAL = new LeaveType(1L, "Annual", 14);
    private static final LeaveType UNPAID = new LeaveType(4L, "Unpaid", null);
    private static final Employee ALICE = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
            "012-3456789", "alice@bhel.example", "1 Jalan Satu", false);

    /** The report with every run of spaces squeezed to one, so the tests do not depend on column widths. */
    private static List<String> compactLines(String text) {
        return Arrays.stream(text.split("\n")).map(line -> line.trim().replaceAll("\\s+", " ")).toList();
    }

    private static YearlyReport fullReport() {
        return new YearlyReport(ALICE, 2026,
                List.of(new FamilyMember(1L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17)),
                        new FamilyMember(2L, "Mei Lin Tan", "Parent", null)),
                List.of(new LeaveApplication(2L, 7L, "Alice Tan", ANNUAL, LocalDate.of(2026, 3, 10),
                                LocalDate.of(2026, 3, 12), 3, "Family trip", LeaveStatus.APPROVED,
                                LocalDateTime.of(2026, 3, 1, 9, 0), LocalDateTime.of(2026, 3, 2, 14, 30)),
                        new LeaveApplication(3L, 7L, "Alice Tan", UNPAID, LocalDate.of(2026, 6, 1),
                                LocalDate.of(2026, 6, 1), 1, null, LeaveStatus.PENDING,
                                LocalDateTime.of(2026, 5, 20, 9, 15), null)),
                List.of(new LeaveBalance(ANNUAL, 2026, 3, 0), new LeaveBalance(UNPAID, 2026, 1, 0)));
    }

    @Test
    void theReportHasATitleWithTheYearAndEveryHeading() {
        List<String> lines = compactLines(ReportFormatter.format(fullReport()));

        assertEquals("YEARLY REPORT 2026", lines.get(0));
        assertTrue(lines.contains("EMPLOYEE"));
        assertTrue(lines.contains("FAMILY"));
        assertTrue(lines.contains("LEAVE BALANCES 2026"));
        assertTrue(lines.contains("LEAVE APPLICATIONS STARTING IN 2026"));
    }

    @Test
    void theProfileShowsEveryDetailWithItsLabel() {
        List<String> lines = compactLines(ReportFormatter.format(fullReport()));

        assertTrue(lines.contains("Name: Alice Tan"));
        assertTrue(lines.contains("IC/passport number: AB123456"));
        assertTrue(lines.contains("Role: Employee"));
        assertTrue(lines.contains("Phone: 012-3456789"));
        assertTrue(lines.contains("Email: alice@bhel.example"));
        assertTrue(lines.contains("Address: 1 Jalan Satu"));
    }

    @Test
    void missingContactDetailsLeaveTheLabelWithNothingAfterIt() {
        Employee bare = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE, null, null, null, false);
        YearlyReport report = new YearlyReport(bare, 2026, List.of(), List.of(), List.of());

        List<String> lines = compactLines(ReportFormatter.format(report));

        assertTrue(lines.contains("Phone:"));
        assertTrue(lines.contains("Email:"));
        assertTrue(lines.contains("Address:"));
    }

    @Test
    void familyMembersAreListedWithRelationshipAndBirthDateWhenKnown() {
        List<String> lines = compactLines(ReportFormatter.format(fullReport()));

        assertTrue(lines.contains("Bob Tan Spouse born 1990-05-17"));
        assertTrue(lines.contains("Mei Lin Tan Parent"), "no birth date, nothing invented");
    }

    @Test
    void balancesShowAllowanceUsedAndRemainingWithUnlimitedForUncappedTypes() {
        List<String> lines = compactLines(ReportFormatter.format(fullReport()));

        assertTrue(lines.contains("Type Allowance Approved Pending Remaining"));
        assertTrue(lines.contains("Annual 14 3 0 11"));
        assertTrue(lines.contains("Unpaid Unlimited 1 0 Unlimited"));
    }

    @Test
    void applicationsShowDatesDaysTypeStatusAndReason() {
        List<String> lines = compactLines(ReportFormatter.format(fullReport()));

        assertTrue(lines.contains("2026-03-10 to 2026-03-12 3 days Annual Approved Family trip"));
        assertTrue(lines.contains("2026-06-01 to 2026-06-01 1 day Unpaid Pending"), "no reason, no trailing text");
    }

    @Test
    void emptySectionsSaySoInsteadOfLeavingAGap() {
        YearlyReport empty = new YearlyReport(ALICE, 2026, List.of(), List.of(), List.of());

        List<String> lines = compactLines(ReportFormatter.format(empty));

        assertTrue(lines.contains("(none recorded)"), "family");
        assertTrue(lines.contains("(none)"), "applications");
    }

    @Test
    void theTextEndsWithOneNewlineAndUsesNoWindowsLineEndings() {
        String text = ReportFormatter.format(fullReport());

        assertTrue(text.endsWith("\n"));
        assertFalse(text.endsWith("\n\n"));
        assertFalse(text.contains("\r"));
    }

    @Test
    void theSuggestedFileNameNamesTheEmployeeAndTheYear() {
        assertEquals("yearly-report-AB123456-2026.txt", ReportFormatter.fileName(fullReport()));
    }

    @Test
    void anIcNumberWithHyphensKeepsThemInTheFileName() {
        Employee hyphens = new Employee(7L, "Alice", "Tan", "900101-14-5678", Role.EMPLOYEE, null, null, null, false);

        String name = ReportFormatter.fileName(new YearlyReport(hyphens, 2026, List.of(), List.of(), List.of()));

        assertEquals("yearly-report-900101-14-5678-2026.txt", name);
    }
}
