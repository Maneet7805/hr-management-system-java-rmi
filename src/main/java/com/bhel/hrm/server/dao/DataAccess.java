package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication.LeaveStatus;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Session.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All the DAOs, bound to the same database connection, so they share one transaction. */
public interface DataAccess {

    EmployeeDao employees();

    FamilyMemberDao family();

    LeaveTypeDao leaveTypes();

    LeaveApplicationDao leave();

    /**
     * The only way the service layer reaches the data. Each call opens one connection, runs the work
     * with DAOs bound to it, and closes it again.
     *
     * <p>It is an interface so the services depend on this contract, not on MySQL.
     */
    public interface Database {

        /** Runs work that only reads, or that needs no all-or-nothing guarantee. Each statement commits at once. */
        <T, E extends Exception> T read(Work<T, E> work) throws E;

        /**
         * Runs the work as one all-or-nothing unit: everything is committed if the work returns,
         * and everything is undone if it throws anything. Use it whenever two or more changes belong together,
         * or when a row lock is needed.
         */
        <T, E extends Exception> T transaction(Work<T, E> work) throws E;
    }

    /**
     * A piece of work to run against the database, given the DAOs to use.
     *
     * <p>{@code E} lets the work throw its own checked exception (a business rule being broken, for
     * example) and have it reach the caller unchanged, after the transaction has been rolled back.
     * Work that throws no checked exception needs no {@code try/catch} at the call site.
     */
    @FunctionalInterface
    public interface Work<T, E extends Exception> {

        T run(DataAccess dataAccess) throws E;
    }

    /**
     * The database could not do what was asked. Unchecked and used only inside the server: the service
     * layer turns it into an exception the client can show. The message never contains SQL or passwords.
     */
    public static class DataAccessException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }

        public DataAccessException(String message) {
            super(message);
        }
    }

    /** A value that must be unique already exists, for example a second employee with the same IC/passport. */
    public static class DuplicateValueException extends DataAccessException {

        private static final long serialVersionUID = 1L;

        public DuplicateValueException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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

    /**
     * Stores an employee's family members. Changing or removing a member always names the employee,
     * so an employee can never touch someone else's family.
     */
    public interface FamilyMemberDao {

        FamilyMember insert(long employeeId, String name, String relationship, LocalDate dateOfBirth);

        /** In the order they were added. */
        List<FamilyMember> findByEmployee(long employeeId);

        /** Replaces name, relationship and date of birth. Empty if that member does not belong to this employee. */
        Optional<FamilyMember> update(long employeeId, FamilyMember member);

        /** {@code false} if that member does not exist or belongs to another employee. */
        boolean delete(long employeeId, long familyMemberId);
    }

    /** Reads the leave types (Annual, Medical, ...). They are seed data and are not changed by the program. */
    public interface LeaveTypeDao {

        /** All types in id order. */
        List<LeaveType> findAll();

        Optional<LeaveType> findById(long id);
    }

    /**
     * Stores leave applications. It records and finds; the service decides whether a request is allowed
     * (enough days, no overlap), using {@link #overlaps} and {@link #daysUsed} inside one transaction.
     */
    public interface LeaveApplicationDao {

        /** Saves a new application as PENDING and returns it as stored. */
        LeaveApplication insert(NewLeaveApplication application);

        Optional<LeaveApplication> findById(long id);

        /** One employee's applications, newest start date first. */
        List<LeaveApplication> findByEmployee(long employeeId);

        /** All applications with this status, oldest application first. */
        List<LeaveApplication> findByStatus(LeaveStatus status);

        /** One employee's applications that start in {@code year}, earliest first. */
        List<LeaveApplication> findByEmployeeAndYear(long employeeId, int year);

        /**
         * Whether the employee already has PENDING or APPROVED leave sharing at least one day with
         * the given dates (both ends included). Rejected leave never counts.
         */
        boolean overlaps(long employeeId, LocalDate startDate, LocalDate endDate);

        /** Days taken per leave type id, for applications with this status that start in {@code year}. */
        Map<Long, Integer> daysUsed(long employeeId, int year, LeaveStatus status);

        /**
         * Approves or rejects an application, but only if it is still PENDING. The check and the change
         * are one statement, so when two people decide at once exactly one succeeds.
         *
         * @param decision must be APPROVED or REJECTED
         * @return the updated application, or empty if it does not exist or was already decided
         */
        Optional<LeaveApplication> decide(long applicationId, LeaveStatus decision, long decidedBy,
                LocalDateTime decidedAt);
    }

    /** An employee together with the stored password, found by IC/passport number when someone logs in. */
    public record LoginRecord(Employee employee, StoredCredentials credentials) {
    }

    /** What is needed to create an employee. The database gives it an id; contact details are added later. */
    public record NewEmployee(String firstName, String lastName, String icPassport, Role role,
            boolean mustChangePassword) {

        public NewEmployee {
            // Building an Employee applies the same rules (name length, IC format) the client-facing class has,
            // so they are written only once.
            new Employee(0, firstName, lastName, icPassport, role, null, null, null, mustChangePassword);
        }
    }

    /**
     * What is needed to record a new leave request. It checks the rules that depend only on the request
     * itself; whether the employee has enough days, or already has leave on those dates, is for the service.
     *
     * @param reason optional, at most 200 characters
     */
    public record NewLeaveApplication(long employeeId, long leaveTypeId, LocalDate startDate, LocalDate endDate,
            String reason, LocalDateTime appliedAt) {

        private static final int MAX_REASON_LENGTH = 200;

        public NewLeaveApplication {
            if (startDate == null || endDate == null || appliedAt == null) {
                throw new IllegalArgumentException("Start date, end date and applied time are required");
            }
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("End date must not be before the start date");
            }
            if (startDate.getYear() != endDate.getYear()) {
                throw new IllegalArgumentException("Leave must stay within one calendar year");
            }
            if (reason != null && reason.length() > MAX_REASON_LENGTH) {
                throw new IllegalArgumentException("Reason must be at most " + MAX_REASON_LENGTH + " characters");
            }
        }

        /** Calendar days from start to end, counting both (decision #21). */
        public int days() {
            return (int) (ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }
    }

    /**
     * A password as it is stored: the hash, the random salt and how many rounds were used.
     * Server-only, so it is not Serializable and never part of a class sent to a client (decision #36).
     */
    public record StoredCredentials(byte[] hash, byte[] salt, int iterations) {

        public StoredCredentials {
            if (hash == null || hash.length == 0 || salt == null || salt.length == 0) {
                throw new IllegalArgumentException("Hash and salt are required");
            }
            if (iterations < 1) {
                throw new IllegalArgumentException("Iterations must be at least 1");
            }
            hash = hash.clone();
            salt = salt.clone();
        }

        /** A copy, so the stored value cannot be changed from outside. */
        @Override
        public byte[] hash() {
            return hash.clone();
        }

        @Override
        public byte[] salt() {
            return salt.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof StoredCredentials that
                    && iterations == that.iterations
                    && Arrays.equals(hash, that.hash)
                    && Arrays.equals(salt, that.salt);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * Arrays.hashCode(hash) + Arrays.hashCode(salt)) + iterations;
        }

        /** Leaves the values out so they cannot end up in a log. */
        @Override
        public String toString() {
            return "StoredCredentials[iterations=" + iterations + "]";
        }
    }
}
