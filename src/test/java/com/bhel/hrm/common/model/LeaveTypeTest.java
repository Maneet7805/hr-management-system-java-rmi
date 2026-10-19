package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import org.junit.jupiter.api.Test;

class LeaveTypeTest {

    @Test
    void validLeaveTypeSurvivesSerialization() {
        LeaveType original = new LeaveType(1L, "Annual", 14);

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LeaveType(1L, " ", 14));
    }

    @Test
    void negativeAllowanceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LeaveType(1L, "Annual", -1));
    }

    @Test
    void nullAllowanceMeansUncappedAndIsAccepted() {
        assertDoesNotThrow(() -> new LeaveType(4L, "Unpaid", null));
    }

    @Test
    void zeroAllowanceIsAccepted() {
        assertDoesNotThrow(() -> new LeaveType(5L, "Study", 0));
    }
}
