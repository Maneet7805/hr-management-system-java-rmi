package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.YearlyReport;

/**
 * Writes a yearly report as plain text: the same text is shown on screen and saved to a file. Columns are
 * aligned with spaces, so it reads well in a fixed-width font and in any text editor. Lines end with "\n".
 */
final class ReportFormatter {

    private ReportFormatter() {
    }

    static String format(YearlyReport report) {
        StringBuilder out = new StringBuilder();
        int year = report.year();
        line(out, "YEARLY REPORT " + year);
        line(out, "=".repeat(("YEARLY REPORT " + year).length()));

        line(out, "");
        line(out, "EMPLOYEE");
        Employee employee = report.employee();
        detail(out, "Name:", employee.firstName() + " " + employee.lastName());
        detail(out, "IC/passport number:", employee.icPassport());
        detail(out, "Role:", Display.role(employee.role()));
        detail(out, "Phone:", Display.text(employee.phone()));
        detail(out, "Email:", Display.text(employee.email()));
        detail(out, "Address:", Display.text(employee.address()));

        line(out, "");
        line(out, "FAMILY");
        if (report.familyMembers().isEmpty()) {
            line(out, "  (none recorded)");
        }
        for (FamilyMember member : report.familyMembers()) {
            String born = member.dateOfBirth() == null ? "" : "born " + Display.date(member.dateOfBirth());
            line(out, String.format("  %-24s%-14s%s", member.name(), member.relationship(), born));
        }

        line(out, "");
        line(out, "LEAVE BALANCES " + year);
        line(out, balanceRow("Type", "Allowance", "Approved", "Pending", "Remaining"));
        for (LeaveBalance balance : report.balances()) {
            line(out, balanceRow(balance.leaveType().name(), Display.allowance(balance.leaveType()),
                    String.valueOf(balance.approvedDays()), String.valueOf(balance.pendingDays()),
                    Display.remaining(balance)));
        }

        line(out, "");
        line(out, "LEAVE APPLICATIONS STARTING IN " + year);
        if (report.leaveApplications().isEmpty()) {
            line(out, "  (none)");
        }
        for (LeaveApplication application : report.leaveApplications()) {
            line(out, String.format("  %s to %s  %-8s %-10s %-9s %s",
                    Display.date(application.startDate()), Display.date(application.endDate()),
                    Display.days(application.days()), application.leaveType().name(),
                    Display.status(application.status()), Display.text(application.reason())));
        }
        return out.toString();
    }

    /**
     * A suggested name for the saved file. The IC/passport number is already limited to letters, digits and
     * hyphens by the shared Employee class, so it is safe to use in a file name as it is.
     */
    static String fileName(YearlyReport report) {
        return "yearly-report-" + report.employee().icPassport() + "-" + report.year() + ".txt";
    }

    private static String balanceRow(String type, String allowance, String approved, String pending,
            String remaining) {
        return String.format("  %-12s%10s%10s%9s%11s", type, allowance, approved, pending, remaining);
    }

    private static void detail(StringBuilder out, String label, String value) {
        line(out, String.format("  %-21s%s", label, value));
    }

    /** Trailing spaces are dropped, so an empty value does not leave invisible padding behind. */
    private static void line(StringBuilder out, String text) {
        out.append(text.stripTrailing()).append('\n');
    }
}
