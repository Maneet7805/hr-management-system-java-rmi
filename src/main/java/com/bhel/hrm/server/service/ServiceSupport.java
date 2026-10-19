package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.DuplicateValueException;
import com.bhel.hrm.server.dao.Work;

/** Small helpers the services share, so each service stays about its own rules. */
final class ServiceSupport {

    private ServiceSupport() {
    }

    /**
     * Runs the work in a transaction. A database failure becomes the safe "temporarily unavailable"
     * message. A {@link DuplicateValueException} is passed through unchanged, because the caller knows
     * which value was duplicated and can say so.
     */
    static <T, E extends Exception> T transaction(Database database, Work<T, E> work)
            throws E, ServiceUnavailableException {
        try {
            return database.transaction(work);
        } catch (DuplicateValueException e) {
            throw e;
        } catch (DataAccessException e) {
            throw ServiceErrors.unavailable(e);
        }
    }

    /** As {@link #transaction}, for work that only reads. */
    static <T, E extends Exception> T read(Database database, Work<T, E> work)
            throws E, ServiceUnavailableException {
        try {
            return database.read(work);
        } catch (DataAccessException e) {
            throw ServiceErrors.unavailable(e);
        }
    }

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** Optional text: trimmed, blank becomes {@code null}, and too long is refused with the field's name. */
    static String optionalText(String value, String field, int maxLength) throws ValidationException {
        String trimmed = trim(value);
        if (trimmed == null || trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new ValidationException(field + " must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }
}
