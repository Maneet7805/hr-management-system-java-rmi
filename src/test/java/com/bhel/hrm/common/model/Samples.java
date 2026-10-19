package com.bhel.hrm.common.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Test fixtures: small valid objects the tests can build on. */
final class Samples {

    private Samples() {
    }

    static Employee employee() {
        return new Employee(1L, "Alice", "Tan", "900101-14-5678", Role.EMPLOYEE,
                "012-3456789", "alice@bhel.example", "1 Jalan Satu", false);
    }

    static LeaveType annual() {
        return new LeaveType(1L, "Annual", 14);
    }

    static LeaveType unpaid() {
        return new LeaveType(4L, "Unpaid", null);
    }

    static FamilyMember spouse() {
        return new FamilyMember(7L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));
    }

    /** A valid 3-day application, 10 to 12 March 2026. */
    static LeaveApplication application() {
        return new LeaveApplication(11L, 1L, "Alice Tan", annual(),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3, "Family trip",
                LeaveStatus.PENDING, LocalDateTime.of(2026, 3, 1, 9, 0), null);
    }
}
