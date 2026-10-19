package com.bhel.hrm.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final String LOGIN_ID = "AB123456";

    @Test
    void aLongPassphraseIsAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.check("correct horse battery staple", LOGIN_ID));
    }

    @Test
    void exactlyEightCharactersIsAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.check("abcdefgh", LOGIN_ID));
    }

    @Test
    void sevenCharactersIsTooShort() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> PasswordPolicy.check("abcdefg", LOGIN_ID));

        assertTrue(error.getMessage().contains("at least 8"), error.getMessage());
    }

    @Test
    void exactly128CharactersIsAcceptedAndMoreIsRefused() {
        assertDoesNotThrow(() -> PasswordPolicy.check("a".repeat(128), LOGIN_ID));

        ValidationException error = assertThrows(ValidationException.class,
                () -> PasswordPolicy.check("a".repeat(129), LOGIN_ID));
        assertTrue(error.getMessage().contains("at most 128"), error.getMessage());
    }

    @Test
    void nullAndBlankAndOnlySpacesAreRefused() {
        assertThrows(ValidationException.class, () -> PasswordPolicy.check(null, LOGIN_ID));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("", LOGIN_ID));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("          ", LOGIN_ID));
    }

    @Test
    void thePasswordMayNotBeTheLoginIdInAnyCase() {
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("AB123456", LOGIN_ID));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("ab123456", LOGIN_ID));
        assertThrows(ValidationException.class, () -> PasswordPolicy.check("ab123456", "  AB123456  "));
    }

    @Test
    void thereAreNoCompositionRulesSoAllLowercaseIsFine() {
        assertDoesNotThrow(() -> PasswordPolicy.check("onlylowercaseletters", LOGIN_ID));
    }
}
