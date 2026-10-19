package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.DuplicateValueException;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.security.PasswordHasher;
import com.bhel.hrm.server.security.PasswordPolicy;

/**
 * Creates an HR account without anyone being logged in. Registering employees needs an HR user
 * to log in first, so the very first HR account has to come from somewhere else (decision #29).
 */
public final class HrSeeder {

    private final Database database;
    private final PasswordHasher hasher;

    public HrSeeder(Database database, PasswordHasher hasher) {
        this.database = database;
        this.hasher = hasher;
    }

    /** The new HR person chose their own password, so it does not have to be changed at first login. */
    public Employee createHr(String firstName, String lastName, String icPassport, String password)
            throws HrmException {
        PasswordPolicy.check(password, icPassport);
        NewEmployee newEmployee;
        try {
            newEmployee = new NewEmployee(firstName, lastName, icPassport, Role.HR, false);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        try {
            return database.transaction(da -> da.employees().insert(newEmployee, hasher.hash(password)));
        } catch (DuplicateValueException e) {
            throw new ValidationException("An employee with that IC/passport number already exists.");
        } catch (DataAccessException e) {
            throw ServiceErrors.unavailable(e);
        }
    }
}
