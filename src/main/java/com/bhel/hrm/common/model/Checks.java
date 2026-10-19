package com.bhel.hrm.common.model;

/**
 * Small validation helpers shared by the data classes.
 * Records run their constructor again when they are deserialized, so these checks also
 * stop a forged byte stream from creating an invalid object on the server.
 */
final class Checks {

    private Checks() {
    }

    /** Text that must be present, not blank and at most {@code maxLength} characters. */
    static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
