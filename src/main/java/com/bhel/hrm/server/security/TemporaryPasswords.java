package com.bhel.hrm.server.security;

import java.security.SecureRandom;

/**
 * One-time passwords for newly registered employees. They are read aloud or copied by hand, so the
 * alphabet leaves out characters that look alike (0 and O, 1 and l and I). Twelve characters from
 * 57 give about 70 bits, far more than anyone can guess in the time before it must be changed.
 */
public final class TemporaryPasswords {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    public static String generate() {
        StringBuilder password = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            // nextInt(bound) picks each character with equal chance, so no character is favoured.
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
