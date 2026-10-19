package com.bhel.hrm.server.dao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * What is needed to record a new leave request. It checks the rules that depend only on the request
 * itself; whether the employee has enough days, or already has leave on those dates, is for the service.
 *
 * @param reason optional, at most 200 characters
 */
public record NewLeaveApplication(long employeeId, long leaveTypeId, LocalDate startDate, LocalDate endDate,
        String reason, LocalDateTime appliedAt) {

    private static final int MAX_REASON_LENGTH = 200;

    public NewLeaveApplication {
        if (startDate == null || endDate == null || appliedAt == null) {
            throw new IllegalArgumentException("Start date, end date and applied time are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must not be before the start date");
        }
        if (startDate.getYear() != endDate.getYear()) {
            throw new IllegalArgumentException("Leave must stay within one calendar year");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Reason must be at most " + MAX_REASON_LENGTH + " characters");
        }
    }

    /** Calendar days from start to end, counting both (decision #21). */
    public int days() {
        return (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
    }
}
