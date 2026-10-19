package com.bhel.hrm.server.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.server.dao.StoredCredentials;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Pbkdf2PasswordHasherTest {

    /** Few rounds, so the tests stay fast. The real default is checked in one test below. */
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1000);

    @Test
    void verifyAcceptsTheRightPassword() {
        StoredCredentials stored = hasher.hash("correct horse battery staple");

        assertTrue(hasher.verify("correct horse battery staple", stored));
    }

    @Test
    void verifyRejectsAWrongPasswordIncludingACloseOne() {
        StoredCredentials stored = hasher.hash("Correct-Horse-1");

        assertFalse(hasher.verify("Correct-Horse-2", stored));
        assertFalse(hasher.verify("correct-horse-1", stored));
        assertFalse(hasher.verify("", stored));
    }

    @Test
    void theSamePasswordHashedTwiceGetsDifferentSaltsAndHashes() {
        StoredCredentials first = hasher.hash("same password");
        StoredCredentials second = hasher.hash("same password");

        assertFalse(Arrays.equals(first.salt(), second.salt()));
        assertFalse(Arrays.equals(first.hash(), second.hash()));
    }

    @Test
    void saltIs16BytesAndHashIs32Bytes() {
        StoredCredentials stored = hasher.hash("some password");

        assertEquals(16, stored.salt().length);
        assertEquals(32, stored.hash().length);
        assertEquals(1000, stored.iterations());
    }

    @Test
    void verifyUsesTheIterationCountStoredWithTheHashNotTheCurrentSetting() {
        StoredCredentials oldHash = new Pbkdf2PasswordHasher(1000).hash("old password");

        assertTrue(new Pbkdf2PasswordHasher(2000).verify("old password", oldHash));
    }

    @Test
    void thePlainPasswordIsNotInTheStoredValues() {
        String password = "plain-text-password";
        StoredCredentials stored = hasher.hash(password);

        assertNotEquals(password, new String(stored.hash(), StandardCharsets.ISO_8859_1));
        assertFalse(stored.toString().contains(password));
        assertFalse(Arrays.equals(password.getBytes(StandardCharsets.UTF_8), stored.hash()));
    }

    @Test
    void theDefaultIs600000Iterations() {
        assertEquals(600_000, Pbkdf2PasswordHasher.DEFAULT_ITERATIONS);
        assertEquals(600_000, new Pbkdf2PasswordHasher().hash("a default password").iterations());
    }

    @Test
    void aHashIsRepeatableFromTheStoredSaltAndIterations() {
        StoredCredentials stored = hasher.hash("repeat me");
        StoredCredentials again = new StoredCredentials(stored.hash(), stored.salt(), stored.iterations());

        assertArrayEquals(stored.hash(), again.hash());
        assertTrue(hasher.verify("repeat me", again));
    }

    @Test
    void fewerThanOneIterationIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Pbkdf2PasswordHasher(0));
    }
}
