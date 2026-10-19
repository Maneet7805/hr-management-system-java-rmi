package com.bhel.hrm.common.exception;

/** The server could not finish the request, for example because the database is down. */
public class ServiceUnavailableException extends HrmException {

    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
