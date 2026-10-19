package com.bhel.hrm.server.security;

import com.bhel.hrm.server.dao.StoredCredentials;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 with HMAC-SHA-256, built into the JDK. It is deliberately slow, so someone who steals the
 * database cannot try billions of guesses per second. Each password gets its own random salt, so equal
 * passwords do not look equal in the database.
 *
 * <p>The iteration count is stored with every hash. The default can be raised later and old hashes still verify.
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {

    /** OWASP's current figure for PBKDF2-HMAC-SHA256 (Password Storage Cheat Sheet). */
    public static final int DEFAULT_ITERATIONS = 600_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public Pbkdf2PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /** A lower count is for tests only, to keep them fast. */
    public Pbkdf2PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be at least 1");
        }
        this.iterations = iterations;
    }

    @Override
    public StoredCredentials hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new StoredCredentials(derive(password, salt, iterations, KEY_BITS), salt, iterations);
    }

    @Override
    public boolean verify(String password, StoredCredentials stored) {
        byte[] expected = stored.hash();
        byte[] actual = derive(password, stored.salt(), stored.iterations(), expected.length * 8);
        // Compares every byte even after a mismatch, so the time taken does not reveal how much matched.
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int keyBits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Password hashing is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
