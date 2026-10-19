package com.bhel.hrm.server.security;

import com.bhel.hrm.common.exception.ValidationException;

/**
 * Rules for a new password. Length matters more than mixing character types, so there are no
 * "needs a digit and a symbol" rules. The upper limit stops someone sending a huge password to waste
 * server time on hashing.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /** @throws ValidationException with a message the user can act on */
    public static void check(String password, String loginId) throws ValidationException {
        if (password == null || password.isBlank()) {
            throw new ValidationException("Password must not be blank.");
        }
        if (password.length() < MIN_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_LENGTH + " characters.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new ValidationException("Password must be at most " + MAX_LENGTH + " characters.");
        }
        if (loginId != null && password.equalsIgnoreCase(loginId.trim())) {
            throw new ValidationException("Password must not be the same as your login ID.");
        }
    }
}
