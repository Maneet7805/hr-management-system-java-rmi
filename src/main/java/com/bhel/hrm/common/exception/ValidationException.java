package com.bhel.hrm.common.exception;

/** The request broke a rule, for example an end date before the start date or a duplicate IC number. */
public class ValidationException extends HrmException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
