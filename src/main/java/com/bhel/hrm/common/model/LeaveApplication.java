package com.bhel.hrm.common.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * One leave request. Days are counted as calendar days, inclusive of both dates, and a request
 * cannot cross a year boundary (see DECISION.md #21 and #22).
 *
 * @param employeeName display name, so HR can read a list without extra calls
 * @param days         must equal the number of calendar days from start to end, inclusive
 * @param reason       optional
 * @param decidedAt    {@code null} while the application is still pending
 */
public record LeaveApplication(
        long id,
        long employeeId,
        String employeeName,
        LeaveType leaveType,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        String reason,
        LeaveStatus status,
        LocalDateTime appliedAt,
        LocalDateTime decidedAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MAX_REASON_LENGTH = 200;

    public LeaveApplication {
        Checks.requireText(employeeName, "Employee name", 101);
        Checks.requireNonNull(leaveType, "Leave type");
        Checks.requireNonNull(startDate, "Start date");
        Checks.requireNonNull(endDate, "End date");
        Checks.requireNonNull(status, "Status");
        Checks.requireNonNull(appliedAt, "Applied time");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before the start date");
        }
        if (startDate.getYear() != endDate.getYear()) {
            throw new IllegalArgumentException("Leave must stay within one calendar year");
        }
        if (days != ChronoUnit.DAYS.between(startDate, endDate) + 1) {
            throw new IllegalArgumentException("Days must equal the number of calendar days from start to end");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
    }
}
