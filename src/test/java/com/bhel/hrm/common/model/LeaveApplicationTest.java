package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LeaveApplicationTest {

    private static LeaveApplication application(LocalDate start, LocalDate end, int days) {
        return new LeaveApplication(11L, 1L, "Alice Tan", Samples.annual(), start, end, days, "Family trip",
                LeaveStatus.PENDING, LocalDateTime.of(2026, 3, 1, 9, 0), null);
    }

    @Test
    void validApplicationSurvivesSerialization() {
        LeaveApplication original = Samples.application();

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void endDateBeforeStartDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 10), 3));
    }

    @Test
    void leaveCrossingNewYearIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), 4));
    }

    @Test
    void daysThatDoNotMatchTheDatesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 2));
    }

    @Test
    void singleDayLeaveCountsAsOneDay() {
        assertDoesNotThrow(() -> application(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), 1));
    }

    @Test
    void missingStatusIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LeaveApplication(11L, 1L, "Alice Tan", Samples.annual(),
                        LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3, null,
                        null, LocalDateTime.of(2026, 3, 1, 9, 0), null));
    }
}
