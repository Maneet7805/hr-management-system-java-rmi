package com.bhel.hrm.server.service;

import static com.bhel.hrm.server.service.ServiceSupport.optionalText;
import static com.bhel.hrm.server.service.ServiceSupport.trim;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.DuplicateValueException;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import com.bhel.hrm.server.security.PasswordHasher;
import com.bhel.hrm.server.security.SessionManager.ActiveSession;
import com.bhel.hrm.server.security.TemporaryPasswords;
import java.time.LocalDate;
import java.util.List;

/**
 * Employees: HR registers and lists them, and every logged-in person keeps their own contact and
 * family details. Each method first asks {@link AuthService} who is calling, so no rule here can be
 * skipped by a client that sends something unexpected.
 */
public final class EmployeeService {

    // Column sizes of the database (decision #41). The shared data classes do not limit these.
    private static final int MAX_PHONE = 30;
    private static final int MAX_EMAIL = 100;
    private static final int MAX_ADDRESS = 255;

    private final Database database;
    private final PasswordHasher hasher;
    private final AuthService auth;

    public EmployeeService(Database database, PasswordHasher hasher, AuthService auth) {
        this.database = database;
        this.hasher = hasher;
        this.auth = auth;
    }

    // ---- HR only ------------------------------------------------------------------------------

    /** Creates an employee with a one-time temporary password, returned here and nowhere else. */
    public RegistrationResult registerEmployee(String token, String firstName, String lastName, String icPassport)
            throws HrmException {
        auth.requireRole(token, Role.HR);
        NewEmployee newEmployee;
        try {
            newEmployee = new NewEmployee(trim(firstName), trim(lastName), trim(icPassport), Role.EMPLOYEE, true);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        String temporaryPassword = TemporaryPasswords.generate();
        // Hash before the transaction starts, so the slow work does not hold a database connection open.
        StoredCredentials credentials = hasher.hash(temporaryPassword);
        try {
            Employee saved = ServiceSupport.transaction(database, da -> da.employees().insert(newEmployee, credentials));
            return new RegistrationResult(saved, temporaryPassword);
        } catch (DuplicateValueException e) {
            throw new ValidationException("An employee with that IC/passport number already exists.");
        }
    }

    public List<Employee> listEmployees(String token) throws HrmException {
        auth.requireRole(token, Role.HR);
        return ServiceSupport.read(database, da -> da.employees().findAll());
    }

    // ---- any logged-in person -----------------------------------------------------------------

    public Employee getMyProfile(String token) throws HrmException {
        ActiveSession me = auth.requireSession(token);
        return ServiceSupport.read(database, da -> da.employees().findById(me.employeeId()))
                .orElseThrow(() -> new NotFoundException("Employee not found."));
    }

    /** Changes phone, email and address only. Names and the IC/passport number stay under HR's control (#30). */
    public Employee updateMyContact(String token, String phone, String email, String address) throws HrmException {
        ActiveSession me = auth.requireSession(token);
        String cleanPhone = optionalText(phone, "Phone", MAX_PHONE);
        String cleanEmail = optionalText(email, "Email", MAX_EMAIL);
        String cleanAddress = optionalText(address, "Address", MAX_ADDRESS);
        if (cleanEmail != null && !cleanEmail.contains("@")) {
            throw new ValidationException("Email must contain '@'.");
        }
        return ServiceSupport.transaction(database,
                da -> da.employees().updateContact(me.employeeId(), cleanPhone, cleanEmail, cleanAddress))
                .orElseThrow(() -> new NotFoundException("Employee not found."));
    }

    public List<FamilyMember> getMyFamily(String token) throws HrmException {
        ActiveSession me = auth.requireSession(token);
        return ServiceSupport.read(database, da -> da.family().findByEmployee(me.employeeId()));
    }

    public FamilyMember addFamilyMember(String token, String name, String relationship, LocalDate dateOfBirth)
            throws HrmException {
        ActiveSession me = auth.requireSession(token);
        try {
            return ServiceSupport.transaction(database,
                    da -> da.family().insert(me.employeeId(), trim(name), trim(relationship), dateOfBirth));
        } catch (IllegalArgumentException e) {
            // The data class refuses bad values before anything is saved; its message says what is wrong.
            throw new ValidationException(e.getMessage());
        }
    }

    /** Only the caller's own member can be changed; someone else's id is reported as not found. */
    public FamilyMember updateFamilyMember(String token, FamilyMember member) throws HrmException {
        ActiveSession me = auth.requireSession(token);
        if (member == null) {
            throw new ValidationException("Family member details are required.");
        }
        return ServiceSupport.transaction(database, da -> da.family().update(me.employeeId(), member))
                .orElseThrow(() -> new NotFoundException("Family member not found."));
    }

    public void removeFamilyMember(String token, long familyMemberId) throws HrmException {
        ActiveSession me = auth.requireSession(token);
        boolean removed = ServiceSupport.transaction(database,
                da -> da.family().delete(me.employeeId(), familyMemberId));
        if (!removed) {
            throw new NotFoundException("Family member not found.");
        }
    }
}
