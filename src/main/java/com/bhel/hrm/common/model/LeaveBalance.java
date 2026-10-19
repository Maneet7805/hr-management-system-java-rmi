package com.bhel.hrm.common.model;

import java.io.Serializable;

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
