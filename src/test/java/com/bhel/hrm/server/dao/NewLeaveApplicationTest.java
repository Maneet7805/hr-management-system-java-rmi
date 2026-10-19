package com.bhel.hrm.server.dao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NewLeaveApplicationTest {

    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 3, 1, 9, 0);

    private static NewLeaveApplication application(LocalDate start, LocalDate end, String reason,
            LocalDateTime appliedAt) {
        return new NewLeaveApplication(1L, 1L, start, end, reason, appliedAt);
    }

    @Test
    void daysCountBothTheFirstAndTheLastDate() {
        NewLeaveApplication three = application(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null, APPLIED_AT);
        NewLeaveApplication one = application(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 10), null, APPLIED_AT);

        assertEquals(3, three.days());
        assertEquals(1, one.days());
    }

    @Test
    void anEndDateBeforeTheStartDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 10), null, APPLIED_AT));
    }

    @Test
    void leaveCrossingNewYearIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), null, APPLIED_AT));
    }

    @Test
    void aReasonLongerThan200CharactersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> application(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "x".repeat(201), APPLIED_AT));
    }

    @Test
    void aReasonOf200CharactersAndNoReasonAreBothAccepted() {
        assertDoesNotThrow(() -> application(
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "x".repeat(200), APPLIED_AT));
        assertDoesNotThrow(() -> application(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null, APPLIED_AT));
    }

    @Test
    void missingDatesOrAppliedTimeAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> application(null, LocalDate.of(2026, 3, 12), null, APPLIED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 3, 10), null, null, APPLIED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> application(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null, null));
    }
}
