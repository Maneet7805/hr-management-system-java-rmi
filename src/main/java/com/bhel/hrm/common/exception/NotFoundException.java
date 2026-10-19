package com.bhel.hrm.common.exception;

/** The employee, family member or leave application asked for does not exist. */
public class NotFoundException extends HrmException {

    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
