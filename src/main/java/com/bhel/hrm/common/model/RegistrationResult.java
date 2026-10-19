package com.bhel.hrm.common.model;

import java.io.Serializable;

/**
 * Returned to HR when an employee is registered. The temporary password is shown once and
 * the employee must change it at first login. {@code toString} leaves it out so it cannot end up in a log.
 */
public record RegistrationResult(Employee employee, String temporaryPassword) implements Serializable {

    private static final long serialVersionUID = 1L;

    public RegistrationResult {
        Checks.requireNonNull(employee, "Employee");
        Checks.requireText(temporaryPassword, "Temporary password", 100);
    }

    @Override
    public String toString() {
        return "RegistrationResult[employee=" + employee.icPassport() + "]";
    }
}
