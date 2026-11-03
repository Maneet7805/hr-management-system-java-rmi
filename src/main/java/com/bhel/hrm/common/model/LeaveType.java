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

    /**
     * Days used for one leave type in one year. The server computes it from the leave history,
     * nothing is stored (DECISION.md #19).
     */
    public record LeaveBalance(LeaveType leaveType, int year, int approvedDays, int pendingDays)
            implements Serializable {

        private static final long serialVersionUID = 1L;

        public LeaveBalance {
            Checks.requireNonNull(leaveType, "Leave type");
            if (approvedDays < 0 || pendingDays < 0) {
                throw new IllegalArgumentException("Days must not be negative");
            }
        }

        /** Days still available, or {@code null} when the leave type has no cap. */
        public Integer remaining() {
            if (leaveType.annualAllowance() == null) {
                return null;
            }
            return leaveType.annualAllowance() - approvedDays - pendingDays;
        }
    }
}
