package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import org.junit.jupiter.api.Test;

class LeaveBalanceTest {

    @Test
    void validBalanceSurvivesSerialization() {
        LeaveBalance original = new LeaveBalance(Samples.annual(), 2026, 3, 2);

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void remainingIsAllowanceMinusApprovedMinusPending() {
        assertEquals(9, new LeaveBalance(Samples.annual(), 2026, 3, 2).remaining());
    }

    @Test
    void remainingIsNullForAnUncappedLeaveType() {
        assertNull(new LeaveBalance(Samples.unpaid(), 2026, 5, 0).remaining());
    }

    @Test
    void negativeApprovedDaysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LeaveBalance(Samples.annual(), 2026, -1, 0));
    }

    @Test
    void negativePendingDaysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LeaveBalance(Samples.annual(), 2026, 0, -1));
    }
}
