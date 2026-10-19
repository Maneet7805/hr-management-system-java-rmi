package com.bhel.hrm.common.model;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * An employee as the client sees them. There is deliberately no password or hash field:
 * credentials never leave the server.
 *
 * @param icPassport IC or passport number; also the login ID
 * @param phone      optional
 * @param email      optional; if given it must contain '@'
 * @param address    optional
 */
public record Employee(
        long id,
        String firstName,
        String lastName,
        String icPassport,
        Role role,
        String phone,
        String email,
        String address,
        boolean mustChangePassword) implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MAX_NAME_LENGTH = 50;
    private static final Pattern IC_PASSPORT = Pattern.compile("[A-Za-z0-9-]{6,20}");

    public Employee {
        Checks.requireText(firstName, "First name", MAX_NAME_LENGTH);
        Checks.requireText(lastName, "Last name", MAX_NAME_LENGTH);
        if (icPassport == null || !IC_PASSPORT.matcher(icPassport).matches()) {
            throw new IllegalArgumentException("IC/passport number must be 6-20 letters, digits or hyphens");
        }
        Checks.requireNonNull(role, "Role");
        if (email != null && !email.isEmpty() && !email.contains("@")) {
            throw new IllegalArgumentException("Email must contain '@'");
        }
    }
}
