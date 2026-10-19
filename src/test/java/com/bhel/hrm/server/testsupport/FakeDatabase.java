package com.bhel.hrm.server.testsupport;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.DataAccess;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.EmployeeDao;
import com.bhel.hrm.server.dao.FamilyMemberDao;
import com.bhel.hrm.server.dao.LeaveApplicationDao;
import com.bhel.hrm.server.dao.LeaveTypeDao;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.Work;
import com.bhel.hrm.server.security.PasswordHasher;
import java.sql.SQLException;

/**
 * A {@link Database} with no MySQL behind it, for fast tests of the service layer.
 * Only employees exist. There is no rollback: tests that need one use the real database.
 * {@link #goDown()} makes every call fail like a lost connection would.
 */
public final class FakeDatabase implements Database {

    private final InMemoryEmployees employees = new InMemoryEmployees();
    private boolean down;

    /** Adds an employee whose password is {@code password}, hashed with the given hasher. */
    public Employee addEmployee(PasswordHasher hasher, String first, String last, String icPassport, Role role,
            String password, boolean mustChangePassword) {
        return employees.insert(new NewEmployee(first, last, icPassport, role, mustChangePassword),
                hasher.hash(password));
    }

    public InMemoryEmployees employees() {
        return employees;
    }

    public void goDown() {
        down = true;
    }

    @Override
    public <T, E extends Exception> T read(Work<T, E> work) throws E {
        return run(work);
    }

    @Override
    public <T, E extends Exception> T transaction(Work<T, E> work) throws E {
        return run(work);
    }

    private <T, E extends Exception> T run(Work<T, E> work) throws E {
        if (down) {
            // The cause deliberately holds SQL-looking text, to prove it never reaches a client.
            throw new DataAccessException("Cannot connect to the database",
                    new SQLException("SELECT * FROM employee failed"));
        }
        return work.run(new DataAccess() {
            @Override
            public EmployeeDao employees() {
                return employees;
            }

            @Override
            public FamilyMemberDao family() {
                throw new UnsupportedOperationException("not in the fake yet");
            }

            @Override
            public LeaveTypeDao leaveTypes() {
                throw new UnsupportedOperationException("not in the fake yet");
            }

            @Override
            public LeaveApplicationDao leave() {
                throw new UnsupportedOperationException("not in the fake yet");
            }
        });
    }
}
