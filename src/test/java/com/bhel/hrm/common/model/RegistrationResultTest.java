package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import org.junit.jupiter.api.Test;

class RegistrationResultTest {

    private static final String PASSWORD = "Temp-4829-xqzv";

    @Test
    void validResultSurvivesSerialization() {
        RegistrationResult original = new RegistrationResult(Samples.employee(), PASSWORD);

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void blankTemporaryPasswordIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RegistrationResult(Samples.employee(), ""));
    }

    @Test
    void toStringDoesNotRevealThePassword() {
        RegistrationResult result = new RegistrationResult(Samples.employee(), PASSWORD);

        assertFalse(result.toString().contains(PASSWORD));
    }
}
