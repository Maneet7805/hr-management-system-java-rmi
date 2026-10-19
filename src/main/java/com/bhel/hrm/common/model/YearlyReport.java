package com.bhel.hrm.common.model;

import java.io.Serializable;
import java.util.List;

/**
 * Everything HR needs for one employee in one year, sent in a single call so the client
 * does not make several round trips. The lists are copied and cannot be changed.
 *
 * @param leaveApplications applications whose start date falls in {@code year}
 */
public record YearlyReport(
        Employee employee,
        int year,
        List<FamilyMember> familyMembers,
        List<LeaveApplication> leaveApplications,
        List<LeaveBalance> balances) implements Serializable {

    private static final long serialVersionUID = 1L;

    public YearlyReport {
        Checks.requireNonNull(employee, "Employee");
        familyMembers = List.copyOf(Checks.requireNonNull(familyMembers, "Family members"));
        leaveApplications = List.copyOf(Checks.requireNonNull(leaveApplications, "Leave applications"));
        balances = List.copyOf(Checks.requireNonNull(balances, "Balances"));
    }
}
