package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.Employee;
import java.util.List;
import java.util.Optional;

/** Stores and finds employees. Finds return {@link Optional} or {@code false} when nothing matches. */
public interface EmployeeDao {

    /** @throws DuplicateValueException if the IC/passport number is already used (ignoring upper/lower case) */
    Employee insert(NewEmployee employee, StoredCredentials credentials);

    Optional<Employee> findById(long id);

    /**
     * Finds the employee and locks the row until the surrounding transaction ends, so another
     * transaction asking for the same lock has to wait. Only meaningful inside {@link Database#transaction}.
     */
    Optional<Employee> lockById(long id);

    /** For login: the employee and the stored password, found by IC/passport number. */
    Optional<LoginRecord> findLogin(String icPassport);

    /** All employees ordered by last name, then first name. */
    List<Employee> findAll();

    /** Changes only phone, email and address. Empty if the employee does not exist. */
    Optional<Employee> updateContact(long id, String phone, String email, String address);

    /** Replaces the password and the must-change flag. {@code false} if the employee does not exist. */
    boolean setCredentials(long id, StoredCredentials credentials, boolean mustChangePassword);
}
