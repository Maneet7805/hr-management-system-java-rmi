package com.bhel.hrm.server.testsupport;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.server.dao.DuplicateValueException;
import com.bhel.hrm.server.dao.EmployeeDao;
import com.bhel.hrm.server.dao.LoginRecord;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An {@link EmployeeDao} that keeps everything in memory, for fast tests of the service layer.
 * It follows the same rules as MySQL where the tests depend on them: the IC/passport number is
 * unique ignoring case, and login finds an employee ignoring case.
 */
public final class InMemoryEmployees implements EmployeeDao {

    private final Map<Long, Employee> employees = new LinkedHashMap<>();
    private final Map<Long, StoredCredentials> credentials = new HashMap<>();
    private long nextId = 1;

    @Override
    public synchronized Employee insert(NewEmployee employee, StoredCredentials stored) {
        boolean taken = employees.values().stream()
                .anyMatch(existing -> existing.icPassport().equalsIgnoreCase(employee.icPassport()));
        if (taken) {
            throw new DuplicateValueException("That value already exists", null);
        }
        Employee created = new Employee(nextId++, employee.firstName(), employee.lastName(), employee.icPassport(),
                employee.role(), null, null, null, employee.mustChangePassword());
        employees.put(created.id(), created);
        credentials.put(created.id(), stored);
        return created;
    }

    @Override
    public synchronized Optional<Employee> findById(long id) {
        return Optional.ofNullable(employees.get(id));
    }

    @Override
    public synchronized Optional<Employee> lockById(long id) {
        return findById(id);
    }

    @Override
    public synchronized Optional<LoginRecord> findLogin(String icPassport) {
        return employees.values().stream()
                .filter(employee -> employee.icPassport().equalsIgnoreCase(icPassport))
                .findFirst()
                .map(employee -> new LoginRecord(employee, credentials.get(employee.id())));
    }

    @Override
    public synchronized List<Employee> findAll() {
        List<Employee> all = new ArrayList<>(employees.values());
        all.sort(Comparator.comparing(Employee::lastName).thenComparing(Employee::firstName)
                .thenComparingLong(Employee::id));
        return all;
    }

    @Override
    public synchronized Optional<Employee> updateContact(long id, String phone, String email, String address) {
        Employee current = employees.get(id);
        if (current == null) {
            return Optional.empty();
        }
        Employee updated = new Employee(id, current.firstName(), current.lastName(), current.icPassport(),
                current.role(), phone, email, address, current.mustChangePassword());
        employees.put(id, updated);
        return Optional.of(updated);
    }

    @Override
    public synchronized boolean setCredentials(long id, StoredCredentials stored, boolean mustChangePassword) {
        Employee current = employees.get(id);
        if (current == null) {
            return false;
        }
        credentials.put(id, stored);
        employees.put(id, new Employee(id, current.firstName(), current.lastName(), current.icPassport(),
                current.role(), current.phone(), current.email(), current.address(), mustChangePassword));
        return true;
    }
}
