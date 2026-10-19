package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FamilyMemberTest {

    @Test
    void validFamilyMemberSurvivesSerialization() {
        FamilyMember original = new FamilyMember(7L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void blankNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FamilyMember(1L, " ", "Spouse", null));
    }

    @Test
    void blankRelationshipIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FamilyMember(1L, "Bob Tan", "", null));
    }

    @Test
    void dateOfBirthIsOptional() {
        assertDoesNotThrow(() -> new FamilyMember(1L, "Bob Tan", "Spouse", null));
    }

    @Test
    void dateOfBirthInTheFutureIsRejected() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        assertThrows(IllegalArgumentException.class, () -> new FamilyMember(1L, "Bob Tan", "Child", tomorrow));
    }

    @Test
    void dateOfBirthTodayIsAccepted() {
        assertDoesNotThrow(() -> new FamilyMember(1L, "Baby Tan", "Child", LocalDate.now()));
    }
}
