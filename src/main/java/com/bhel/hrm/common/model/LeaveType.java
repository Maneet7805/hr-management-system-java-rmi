package com.bhel.hrm.common.model;

import java.io.Serializable;

/**
 * A kind of leave, for example Annual or Medical.
 *
 * @param annualAllowance days per year; {@code null} means uncapped (for example Unpaid)
 */
public record LeaveType(long id, String name, Integer annualAllowance) implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MAX_NAME_LENGTH = 50;

    public LeaveType {
        Checks.requireText(name, "Leave type name", MAX_NAME_LENGTH);
        if (annualAllowance != null && annualAllowance < 0) {
            throw new IllegalArgumentException("Annual allowance must not be negative");
        }
    }
}
