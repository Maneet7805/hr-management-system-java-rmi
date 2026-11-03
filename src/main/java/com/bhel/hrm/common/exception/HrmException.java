package com.bhel.hrm.common.exception;

/**
 * Base class for every error the server reports to a client.
 * It is a checked exception so the client must handle it. The message is written for the user,
 * and there is deliberately no "cause" constructor, so SQL or stack details never cross the network.
 */
public class HrmException extends Exception {

    private static final long serialVersionUID = 1L;

    public HrmException(String message) {
        super(message);
    }

    /** Wrong login ID or password, or an unknown or expired session token. */
    public static class InvalidCredentialsException extends HrmException {

        private static final long serialVersionUID = 1L;

        public InvalidCredentialsException(String message) {
            super(message);
        }
    }

    /** The employee, family member or leave application asked for does not exist. */
    public static class NotFoundException extends HrmException {

        private static final long serialVersionUID = 1L;

        public NotFoundException(String message) {
            super(message);
        }
    }

    /** The user is logged in but their role is not allowed to do this. */
    public static class PermissionDeniedException extends HrmException {

        private static final long serialVersionUID = 1L;

        public PermissionDeniedException(String message) {
            super(message);
        }
    }

    /** The server could not finish the request, for example because the database is down. */
    public static class ServiceUnavailableException extends HrmException {

        private static final long serialVersionUID = 1L;

        public ServiceUnavailableException(String message) {
            super(message);
        }
    }

    /** The request broke a rule, for example an end date before the start date or a duplicate IC number. */
    public static class ValidationException extends HrmException {

        private static final long serialVersionUID = 1L;

        public ValidationException(String message) {
            super(message);
        }
    }
}
