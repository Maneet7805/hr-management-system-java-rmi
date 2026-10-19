package com.bhel.hrm.client.ui;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Reads a date a person typed. The form is yyyy-MM-dd and the screens show it next to the field, because Swing
 * has no standard calendar widget (decision #66). A problem is reported as a sentence naming the field.
 */
final class DateInput {

    private DateInput() {
    }

    /** @throws IllegalArgumentException with a message fit to show the user */
    static LocalDate required(String text, String fieldName) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Enter the " + fieldName.toLowerCase() + ".");
        }
        return parse(trimmed, fieldName);
    }

    /** Empty means "no date"; anything else must still be a real date. */
    static LocalDate optional(String text, String fieldName) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? null : parse(trimmed, fieldName);
    }

    private static LocalDate parse(String trimmed, String fieldName) {
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    fieldName + " must be a real date in the form yyyy-MM-dd, for example 2026-03-15.");
        }
    }
}
