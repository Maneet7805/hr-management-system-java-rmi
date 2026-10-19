package com.bhel.hrm.server.dao;

import java.util.Arrays;

/**
 * A password as it is stored: the hash, the random salt and how many rounds were used.
 * Server-only, so it is not Serializable and never part of a class sent to a client (decision #36).
 */
public record StoredCredentials(byte[] hash, byte[] salt, int iterations) {

    public StoredCredentials {
        if (hash == null || hash.length == 0 || salt == null || salt.length == 0) {
            throw new IllegalArgumentException("Hash and salt are required");
        }
        if (iterations < 1) {
            throw new IllegalArgumentException("Iterations must be at least 1");
        }
        hash = hash.clone();
        salt = salt.clone();
    }

    /** A copy, so the stored value cannot be changed from outside. */
    @Override
    public byte[] hash() {
        return hash.clone();
    }

    @Override
    public byte[] salt() {
        return salt.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StoredCredentials that
                && iterations == that.iterations
                && Arrays.equals(hash, that.hash)
                && Arrays.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(hash) + Arrays.hashCode(salt)) + iterations;
    }

    /** Leaves the values out so they cannot end up in a log. */
    @Override
    public String toString() {
        return "StoredCredentials[iterations=" + iterations + "]";
    }
}
