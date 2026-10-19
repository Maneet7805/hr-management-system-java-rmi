package com.bhel.hrm.common.exception;

/** Wrong login ID or password, or an unknown or expired session token. */
public class InvalidCredentialsException extends HrmException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
