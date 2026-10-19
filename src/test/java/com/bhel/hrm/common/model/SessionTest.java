package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import org.junit.jupiter.api.Test;

class SessionTest {

    private static final String TOKEN = "k3Jx9-secret-token-value";

    @Test
    void validSessionSurvivesSerialization() {
        Session original = new Session(TOKEN, 1L, "Alice Tan", Role.EMPLOYEE, true);

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void blankTokenIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Session(" ", 1L, "Alice Tan", Role.EMPLOYEE, false));
    }

    @Test
    void toStringDoesNotRevealTheToken() {
        Session session = new Session(TOKEN, 1L, "Alice Tan", Role.EMPLOYEE, false);

        assertFalse(session.toString().contains(TOKEN));
    }
}
