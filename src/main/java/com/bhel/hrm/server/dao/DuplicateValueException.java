package com.bhel.hrm.server.dao;

/** A value that must be unique already exists, for example a second employee with the same IC/passport. */
public class DuplicateValueException extends DataAccessException {

    private static final long serialVersionUID = 1L;

    public DuplicateValueException(String message, Throwable cause) {
        super(message, cause);
    }
}
