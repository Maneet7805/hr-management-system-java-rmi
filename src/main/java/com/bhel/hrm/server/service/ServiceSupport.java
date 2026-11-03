package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.HrmException.ServiceUnavailableException;
import com.bhel.hrm.common.exception.HrmException.ValidationException;
import com.bhel.hrm.server.dao.DataAccess.DataAccessException;
import com.bhel.hrm.server.dao.DataAccess.Database;
import com.bhel.hrm.server.dao.DataAccess.DuplicateValueException;
import com.bhel.hrm.server.dao.DataAccess.Work;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Small helpers the services share, so each service stays about its own rules. */
public final class ServiceSupport {

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

    /** Turns a database failure into the one message a client is allowed to see. */
    public static final class ServiceErrors {

        private static final Logger LOG = Logger.getLogger(ServiceErrors.class.getName());

        private ServiceErrors() {
        }

        /**
         * The real cause goes to the server log only. The client gets a fixed sentence, so SQL text,
         * table names or connection details never reach it.
         */
        public static ServiceUnavailableException unavailable(DataAccessException cause) {
            LOG.log(Level.SEVERE, "Database problem", cause);
            return new ServiceUnavailableException("The service is temporarily unavailable. Try again later.");
        }
    }
}
