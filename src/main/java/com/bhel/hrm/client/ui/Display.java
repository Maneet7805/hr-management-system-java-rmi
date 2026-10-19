package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** How values are written on screen. One place, so every screen writes the same thing the same way. */
final class Display {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String UNLIMITED = "Unlimited";

    private Display() {
    }

    /** Written the way the user types dates: yyyy-MM-dd. */
    static String date(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    static String dateTime(LocalDateTime time) {
        return time == null ? "" : DATE_TIME.format(time);
    }

    static String text(String value) {
        return value == null ? "" : value;
    }

    static String status(LeaveStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }

    static String role(Role role) {
        return role == Role.HR ? "HR staff" : "Employee";
    }

    /** A leave type with no yearly limit shows as "Unlimited". */
    static String allowance(LeaveType type) {
        return type.annualAllowance() == null ? UNLIMITED : String.valueOf(type.annualAllowance());
    }

    static String remaining(LeaveBalance balance) {
        Integer remaining = balance.remaining();
        return remaining == null ? UNLIMITED : String.valueOf(remaining);
    }

    static String days(int count) {
        return count == 1 ? "1 day" : count + " days";
    }
}
