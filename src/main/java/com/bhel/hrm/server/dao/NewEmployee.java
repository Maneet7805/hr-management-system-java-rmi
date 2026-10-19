package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;

/** What is needed to create an employee. The database gives it an id; contact details are added later. */
public record NewEmployee(String firstName, String lastName, String icPassport, Role role,
        boolean mustChangePassword) {

    public NewEmployee {
        // Building an Employee applies the same rules (name length, IC format) the client-facing class has,
        // so they are written only once.
        new Employee(0, firstName, lastName, icPassport, role, null, null, null, mustChangePassword);
    }
}
