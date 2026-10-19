package com.bhel.hrm.server.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemporaryPasswordsTest {

    @Test
    void aTemporaryPasswordHas12Characters() {
        assertEquals(12, TemporaryPasswords.generate().length());
    }

    @Test
    void itUsesOnlyLettersAndDigitsWithoutLookAlikes() {
        for (int i = 0; i < 500; i++) {
            String password = TemporaryPasswords.generate();

            assertTrue(password.matches("[A-HJ-NP-Za-km-z2-9]{12}"), password);
        }
    }

    @Test
    void thousandPasswordsAreAllDifferent() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(TemporaryPasswords.generate());
        }

        assertEquals(1000, seen.size());
    }

    @Test
    void everyGeneratedPasswordSatisfiesThePasswordPolicy() {
        for (int i = 0; i < 200; i++) {
            String password = TemporaryPasswords.generate();

            assertDoesNotThrow(() -> PasswordPolicy.check(password, "AB123456"));
        }
    }

    @Test
    void theAlphabetHasNoAmbiguousCharacters() {
        for (char ambiguous : "0O1lI".toCharArray()) {
            assertFalse(TemporaryPasswords.ALPHABET.indexOf(ambiguous) >= 0, "contains " + ambiguous);
        }
        assertEquals(57, TemporaryPasswords.ALPHABET.length());
    }
}
