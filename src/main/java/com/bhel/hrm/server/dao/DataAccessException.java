package com.bhel.hrm.server.dao;

/**
 * The database could not do what was asked. Unchecked and used only inside the server: the service
 * layer turns it into an exception the client can show. The message never contains SQL or passwords.
 */
public class DataAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
