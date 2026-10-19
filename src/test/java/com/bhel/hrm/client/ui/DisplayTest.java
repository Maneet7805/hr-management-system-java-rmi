package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DisplayTest {

    @Test
    void datesAndTimesUseTheSameFormatTheUserTypes() {
        assertEquals("2026-03-15", Display.date(LocalDate.of(2026, 3, 15)));
        assertEquals("2026-03-01 09:05", Display.dateTime(LocalDateTime.of(2026, 3, 1, 9, 5)));
    }

    @Test
    void missingDatesAndTimesShowAsEmptyText() {
        assertEquals("", Display.date(null));
        assertEquals("", Display.dateTime(null));
        assertEquals("", Display.text(null));
        assertEquals("Hello", Display.text("Hello"));
    }

    @Test
    void statusesReadLikeWords() {
        assertEquals("Pending", Display.status(LeaveStatus.PENDING));
        assertEquals("Approved", Display.status(LeaveStatus.APPROVED));
        assertEquals("Rejected", Display.status(LeaveStatus.REJECTED));
    }

    @Test
    void rolesReadLikeJobDescriptions() {
        assertEquals("HR staff", Display.role(Role.HR));
        assertEquals("Employee", Display.role(Role.EMPLOYEE));
    }

    @Test
    void anAllowanceOfNothingMeansUnlimited() {
        assertEquals("14", Display.allowance(new LeaveType(1L, "Annual", 14)));
        assertEquals("Unlimited", Display.allowance(new LeaveType(4L, "Unpaid", null)));
    }

    @Test
    void theRemainingDaysOfAnUnlimitedTypeAreUnlimited() {
        assertEquals("11", Display.remaining(new LeaveBalance(new LeaveType(1L, "Annual", 14), 2026, 3, 0)));
        assertEquals("Unlimited", Display.remaining(new LeaveBalance(new LeaveType(4L, "Unpaid", null), 2026, 9, 0)));
    }

    @Test
    void dayCountsUseTheRightWord() {
        assertEquals("1 day", Display.days(1));
        assertEquals("3 days", Display.days(3));
        assertEquals("0 days", Display.days(0));
    }
}
