package com.bhel.hrm.common.exception;

/** The user is logged in but their role is not allowed to do this. */
public class PermissionDeniedException extends HrmException {

    private static final long serialVersionUID = 1L;

    public PermissionDeniedException(String message) {
        super(message);
    }
}
