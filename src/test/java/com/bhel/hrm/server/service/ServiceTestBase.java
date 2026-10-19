package com.bhel.hrm.server.service;

import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.server.db.DatabaseTestBase;
import com.bhel.hrm.server.security.Pbkdf2PasswordHasher;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.testsupport.MutableClock;

/**
 * Base for tests of the service layer against the real MySQL test database. Each test gets a fresh
 * clock, hasher (few rounds, to stay fast) and services, on top of empty tables.
 */
public abstract class ServiceTestBase extends DatabaseTestBase {

    protected static final String HR_PASSWORD = "An HR passphrase";
    protected static final String CHOSEN_PASSWORD = "My chosen passphrase";

    protected final MutableClock clock = new MutableClock();
    protected final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1000);
    protected final SessionManager sessions = new SessionManager(clock);
    protected final AuthService auth = new AuthService(database, hasher, sessions);
    protected final EmployeeService employees = new EmployeeService(database, hasher, auth);

    /** Creates an HR account (as the seed tool does) and returns a logged-in token. */
    protected String hrToken() throws Exception {
        new HrSeeder(database, hasher).createHr("Hana", "Lim", "HR900001", HR_PASSWORD);
        return auth.login("HR900001", HR_PASSWORD).token();
    }

    /** HR registers an employee, who logs in with the temporary password and picks their own. Returns their token. */
    protected String employeeToken(String hrToken, String firstName, String lastName, String icPassport)
            throws Exception {
        RegistrationResult registered = employees.registerEmployee(hrToken, firstName, lastName, icPassport);
        Session temporary = auth.login(icPassport, registered.temporaryPassword());
        auth.changePassword(temporary.token(), registered.temporaryPassword(), CHOSEN_PASSWORD);
        return temporary.token();
    }
}
