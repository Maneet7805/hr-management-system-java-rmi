package com.bhel.hrm.common.model;

import java.io.Serializable;

/**
 * Returned by login. The client sends {@code token} with every later call.
 * {@code toString} leaves the token out so it cannot end up in a log by accident.
 */
public record Session(String token, long employeeId, String displayName, Role role, boolean mustChangePassword)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    public Session {
        Checks.requireText(token, "Token", 200);
        Checks.requireText(displayName, "Display name", 101);
        Checks.requireNonNull(role, "Role");
    }

    @Override
    public String toString() {
        return "Session[employeeId=" + employeeId + ", role=" + role + "]";
    }
}
