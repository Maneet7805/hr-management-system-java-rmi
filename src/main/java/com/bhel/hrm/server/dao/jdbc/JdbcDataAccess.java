package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication.LeaveStatus;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Session.Role;
import com.bhel.hrm.server.dao.DataAccess.DataAccessException;
import com.bhel.hrm.server.dao.DataAccess.DuplicateValueException;
import com.bhel.hrm.server.dao.DataAccess.EmployeeDao;
import com.bhel.hrm.server.dao.DataAccess.FamilyMemberDao;
import com.bhel.hrm.server.dao.DataAccess.LeaveApplicationDao;
import com.bhel.hrm.server.dao.DataAccess.LeaveTypeDao;
import com.bhel.hrm.server.dao.DataAccess.LoginRecord;
import com.bhel.hrm.server.dao.DataAccess.NewEmployee;
import com.bhel.hrm.server.dao.DataAccess.NewLeaveApplication;
import com.bhel.hrm.server.dao.DataAccess.StoredCredentials;
import com.bhel.hrm.server.dao.DataAccess;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The MySQL DAOs, all sharing one connection and therefore one transaction. */
public final class JdbcDataAccess implements DataAccess {

    private final EmployeeDao employees;
    private final FamilyMemberDao family;
    private final LeaveTypeDao leaveTypes;
    private final LeaveApplicationDao leave;

    public JdbcDataAccess(Connection connection) {
        this.employees = new JdbcEmployeeDao(connection);
        this.family = new JdbcFamilyMemberDao(connection);
        this.leaveTypes = new JdbcLeaveTypeDao(connection);
        this.leave = new JdbcLeaveApplicationDao(connection);
    }

    @Override
    public EmployeeDao employees() {
        return employees;
    }

    @Override
    public FamilyMemberDao family() {
        return family;
    }

    @Override
    public LeaveTypeDao leaveTypes() {
        return leaveTypes;
    }

    @Override
    public LeaveApplicationDao leave() {
        return leave;
    }

    /** MySQL version of {@link EmployeeDao}. Every query is a PreparedStatement; values are never glued into SQL. */
    static final class JdbcEmployeeDao implements EmployeeDao {

        private static final String EMPLOYEE_COLUMNS =
                "id, first_name, last_name, ic_passport, role, phone, email, address, must_change_password";
        private static final String SELECT_EMPLOYEE = "SELECT " + EMPLOYEE_COLUMNS + " FROM employee";

        private final Connection connection;

        JdbcEmployeeDao(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Employee insert(NewEmployee employee, StoredCredentials credentials) {
            String sql = "INSERT INTO employee (first_name, last_name, ic_passport, role, must_change_password,"
                    + " password_hash, password_salt, hash_iterations) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            long id;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, employee.firstName());
                statement.setString(2, employee.lastName());
                statement.setString(3, employee.icPassport());
                statement.setString(4, employee.role().name());
                statement.setBoolean(5, employee.mustChangePassword());
                statement.setBytes(6, credentials.hash());
                statement.setBytes(7, credentials.salt());
                statement.setInt(8, credentials.iterations());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not save the employee", e);
            }
            // Read it back so the caller sees exactly what the database stored.
            return findById(id).orElseThrow(() -> new DataAccessException("The new employee could not be read back"));
        }

        @Override
        public Optional<Employee> findById(long id) {
            return findOne(SELECT_EMPLOYEE + " WHERE id = ?", id);
        }

        @Override
        public Optional<Employee> lockById(long id) {
            return findOne(SELECT_EMPLOYEE + " WHERE id = ? FOR UPDATE", id);
        }

        @Override
        public Optional<LoginRecord> findLogin(String icPassport) {
            String sql = "SELECT " + EMPLOYEE_COLUMNS + ", password_hash, password_salt, hash_iterations"
                    + " FROM employee WHERE ic_passport = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, icPassport);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        return Optional.empty();
                    }
                    StoredCredentials credentials = new StoredCredentials(
                            rows.getBytes("password_hash"), rows.getBytes("password_salt"), rows.getInt("hash_iterations"));
                    return Optional.of(new LoginRecord(employeeFrom(rows), credentials));
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not look up the login", e);
            }
        }

        @Override
        public List<Employee> findAll() {
            String sql = SELECT_EMPLOYEE + " ORDER BY last_name, first_name, id";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                    ResultSet rows = statement.executeQuery()) {
                List<Employee> employees = new ArrayList<>();
                while (rows.next()) {
                    employees.add(employeeFrom(rows));
                }
                return employees;
            } catch (SQLException e) {
                throw Sql.translate("Could not list the employees", e);
            }
        }

        @Override
        public Optional<Employee> updateContact(long id, String phone, String email, String address) {
            String sql = "UPDATE employee SET phone = ?, email = ?, address = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, phone);
                statement.setString(2, email);
                statement.setString(3, address);
                statement.setLong(4, id);
                if (statement.executeUpdate() == 0) {
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not update the contact details", e);
            }
            return findById(id);
        }

        @Override
        public boolean setCredentials(long id, StoredCredentials credentials, boolean mustChangePassword) {
            String sql = "UPDATE employee SET password_hash = ?, password_salt = ?, hash_iterations = ?,"
                    + " must_change_password = ? WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, credentials.hash());
                statement.setBytes(2, credentials.salt());
                statement.setInt(3, credentials.iterations());
                statement.setBoolean(4, mustChangePassword);
                statement.setLong(5, id);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw Sql.translate("Could not change the password", e);
            }
        }

        private Optional<Employee> findOne(String sql, long id) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(employeeFrom(rows)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not read the employee", e);
            }
        }

        private static Employee employeeFrom(ResultSet rows) throws SQLException {
            return new Employee(
                    rows.getLong("id"),
                    rows.getString("first_name"),
                    rows.getString("last_name"),
                    rows.getString("ic_passport"),
                    Role.valueOf(rows.getString("role")),
                    rows.getString("phone"),
                    rows.getString("email"),
                    rows.getString("address"),
                    rows.getBoolean("must_change_password"));
        }
    }

    /** MySQL version of {@link FamilyMemberDao}. */
    static final class JdbcFamilyMemberDao implements FamilyMemberDao {

        private static final String SELECT_MEMBER = "SELECT id, name, relationship, date_of_birth FROM family_member";

        private final Connection connection;

        JdbcFamilyMemberDao(Connection connection) {
            this.connection = connection;
        }

        @Override
        public FamilyMember insert(long employeeId, String name, String relationship, LocalDate dateOfBirth) {
            // Building the object first applies the same rules the client-facing class has, before anything is saved.
            new FamilyMember(0, name, relationship, dateOfBirth);
            String sql = "INSERT INTO family_member (employee_id, name, relationship, date_of_birth) VALUES (?, ?, ?, ?)";
            long id;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, employeeId);
                statement.setString(2, name);
                statement.setString(3, relationship);
                statement.setObject(4, dateOfBirth);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not save the family member", e);
            }
            return find(employeeId, id).orElseThrow(() -> new DataAccessException("The new family member could not be read back"));
        }

        @Override
        public List<FamilyMember> findByEmployee(long employeeId) {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_MEMBER + " WHERE employee_id = ? ORDER BY id")) {
                statement.setLong(1, employeeId);
                try (ResultSet rows = statement.executeQuery()) {
                    List<FamilyMember> members = new ArrayList<>();
                    while (rows.next()) {
                        members.add(memberFrom(rows));
                    }
                    return members;
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not list the family members", e);
            }
        }

        @Override
        public Optional<FamilyMember> update(long employeeId, FamilyMember member) {
            String sql = "UPDATE family_member SET name = ?, relationship = ?, date_of_birth = ?"
                    + " WHERE id = ? AND employee_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, member.name());
                statement.setString(2, member.relationship());
                statement.setObject(3, member.dateOfBirth());
                statement.setLong(4, member.id());
                statement.setLong(5, employeeId);
                if (statement.executeUpdate() == 0) {
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not update the family member", e);
            }
            return find(employeeId, member.id());
        }

        @Override
        public boolean delete(long employeeId, long familyMemberId) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM family_member WHERE id = ? AND employee_id = ?")) {
                statement.setLong(1, familyMemberId);
                statement.setLong(2, employeeId);
                return statement.executeUpdate() > 0;
            } catch (SQLException e) {
                throw Sql.translate("Could not remove the family member", e);
            }
        }

        private Optional<FamilyMember> find(long employeeId, long id) {
            try (PreparedStatement statement = connection.prepareStatement(
                    SELECT_MEMBER + " WHERE id = ? AND employee_id = ?")) {
                statement.setLong(1, id);
                statement.setLong(2, employeeId);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(memberFrom(rows)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not read the family member", e);
            }
        }

        private static FamilyMember memberFrom(ResultSet rows) throws SQLException {
            return new FamilyMember(
                    rows.getLong("id"),
                    rows.getString("name"),
                    rows.getString("relationship"),
                    rows.getObject("date_of_birth", LocalDate.class));
        }
    }

    /** MySQL version of {@link LeaveTypeDao}. */
    static final class JdbcLeaveTypeDao implements LeaveTypeDao {

        private static final String SELECT_TYPE = "SELECT id, name, annual_allowance FROM leave_type";

        private final Connection connection;

        JdbcLeaveTypeDao(Connection connection) {
            this.connection = connection;
        }

        @Override
        public List<LeaveType> findAll() {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_TYPE + " ORDER BY id");
                    ResultSet rows = statement.executeQuery()) {
                List<LeaveType> types = new ArrayList<>();
                while (rows.next()) {
                    types.add(leaveTypeFrom(rows));
                }
                return types;
            } catch (SQLException e) {
                throw Sql.translate("Could not list the leave types", e);
            }
        }

        @Override
        public Optional<LeaveType> findById(long id) {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_TYPE + " WHERE id = ?")) {
                statement.setLong(1, id);
                try (ResultSet rows = statement.executeQuery()) {
                    return rows.next() ? Optional.of(leaveTypeFrom(rows)) : Optional.empty();
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not read the leave type", e);
            }
        }

        private static LeaveType leaveTypeFrom(ResultSet rows) throws SQLException {
            int allowance = rows.getInt("annual_allowance");
            // wasNull() describes the column read just before it, so ask now, before reading anything else.
            Integer annualAllowance = rows.wasNull() ? null : allowance;
            return new LeaveType(rows.getLong("id"), rows.getString("name"), annualAllowance);
        }
    }

    /** MySQL version of {@link LeaveApplicationDao}. Every query is a PreparedStatement. */
    static final class JdbcLeaveApplicationDao implements LeaveApplicationDao {

        /** One row with everything the client-facing {@link LeaveApplication} holds, joined from three tables. */
        private static final String SELECT_APPLICATION =
                "SELECT a.id, a.employee_id, CONCAT(e.first_name, ' ', e.last_name) AS employee_name,"
                        + " t.id AS type_id, t.name AS type_name, t.annual_allowance AS type_allowance,"
                        + " a.start_date, a.end_date, a.days, a.reason, a.status, a.applied_at, a.decided_at"
                        + " FROM leave_application a"
                        + " JOIN employee e ON e.id = a.employee_id"
                        + " JOIN leave_type t ON t.id = a.leave_type_id";

        private final Connection connection;

        JdbcLeaveApplicationDao(Connection connection) {
            this.connection = connection;
        }

        @Override
        public LeaveApplication insert(NewLeaveApplication application) {
            String sql = "INSERT INTO leave_application (employee_id, leave_type_id, start_date, end_date, days,"
                    + " reason, applied_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            long id;
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, application.employeeId());
                statement.setLong(2, application.leaveTypeId());
                statement.setObject(3, application.startDate());
                statement.setObject(4, application.endDate());
                statement.setInt(5, application.days());
                statement.setString(6, application.reason());
                statement.setObject(7, application.appliedAt());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    id = keys.getLong(1);
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not save the leave application", e);
            }
            return findById(id)
                    .orElseThrow(() -> new DataAccessException("The new leave application could not be read back"));
        }

        @Override
        public Optional<LeaveApplication> findById(long id) {
            List<LeaveApplication> found = query(SELECT_APPLICATION + " WHERE a.id = ?", id);
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        }

        @Override
        public List<LeaveApplication> findByEmployee(long employeeId) {
            return query(SELECT_APPLICATION + " WHERE a.employee_id = ? ORDER BY a.start_date DESC, a.id DESC",
                    employeeId);
        }

        @Override
        public List<LeaveApplication> findByStatus(LeaveStatus status) {
            return query(SELECT_APPLICATION + " WHERE a.status = ? ORDER BY a.applied_at, a.id", status.name());
        }

        @Override
        public List<LeaveApplication> findByEmployeeAndYear(long employeeId, int year) {
            // A range on the date, not YEAR(start_date), so the index on (employee_id, start_date) can be used.
            return query(SELECT_APPLICATION + " WHERE a.employee_id = ? AND a.start_date >= ? AND a.start_date < ?"
                    + " ORDER BY a.start_date, a.id", employeeId, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1));
        }

        @Override
        public boolean overlaps(long employeeId, LocalDate startDate, LocalDate endDate) {
            String sql = "SELECT COUNT(*) FROM leave_application WHERE employee_id = ?"
                    + " AND status IN ('PENDING', 'APPROVED') AND start_date <= ? AND end_date >= ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, employeeId);
                statement.setObject(2, endDate);
                statement.setObject(3, startDate);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getInt(1) > 0;
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not check for overlapping leave", e);
            }
        }

        @Override
        public Map<Long, Integer> daysUsed(long employeeId, int year, LeaveStatus status) {
            String sql = "SELECT leave_type_id, SUM(days) AS total FROM leave_application"
                    + " WHERE employee_id = ? AND status = ? AND start_date >= ? AND start_date < ?"
                    + " GROUP BY leave_type_id";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, employeeId);
                statement.setString(2, status.name());
                statement.setObject(3, LocalDate.of(year, 1, 1));
                statement.setObject(4, LocalDate.of(year + 1, 1, 1));
                try (ResultSet rows = statement.executeQuery()) {
                    Map<Long, Integer> used = new HashMap<>();
                    while (rows.next()) {
                        used.put(rows.getLong("leave_type_id"), rows.getInt("total"));
                    }
                    return used;
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not add up the leave days", e);
            }
        }

        @Override
        public Optional<LeaveApplication> decide(long applicationId, LeaveStatus decision, long decidedBy,
                LocalDateTime decidedAt) {
            if (decision != LeaveStatus.APPROVED && decision != LeaveStatus.REJECTED) {
                throw new IllegalArgumentException("A decision must be APPROVED or REJECTED");
            }
            // "AND status = 'PENDING'" makes check and change one step: a second person deciding at the
            // same moment finds nothing left to change.
            String sql = "UPDATE leave_application SET status = ?, decided_by = ?, decided_at = ?"
                    + " WHERE id = ? AND status = 'PENDING'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, decision.name());
                statement.setLong(2, decidedBy);
                statement.setObject(3, decidedAt);
                statement.setLong(4, applicationId);
                if (statement.executeUpdate() == 0) {
                    return Optional.empty();
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not save the decision", e);
            }
            return findById(applicationId);
        }

        private List<LeaveApplication> query(String sql, Object... parameters) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
                try (ResultSet rows = statement.executeQuery()) {
                    List<LeaveApplication> applications = new ArrayList<>();
                    while (rows.next()) {
                        applications.add(applicationFrom(rows));
                    }
                    return applications;
                }
            } catch (SQLException e) {
                throw Sql.translate("Could not read the leave applications", e);
            }
        }

        private static LeaveApplication applicationFrom(ResultSet rows) throws SQLException {
            int allowance = rows.getInt("type_allowance");
            // wasNull() describes the column read just before it, so ask now, before reading anything else.
            Integer annualAllowance = rows.wasNull() ? null : allowance;
            LeaveType type = new LeaveType(rows.getLong("type_id"), rows.getString("type_name"), annualAllowance);
            return new LeaveApplication(
                    rows.getLong("id"),
                    rows.getLong("employee_id"),
                    rows.getString("employee_name"),
                    type,
                    rows.getObject("start_date", LocalDate.class),
                    rows.getObject("end_date", LocalDate.class),
                    rows.getInt("days"),
                    rows.getString("reason"),
                    LeaveStatus.valueOf(rows.getString("status")),
                    rows.getObject("applied_at", LocalDateTime.class),
                    rows.getObject("decided_at", LocalDateTime.class));
        }
    }

    /** Turns JDBC errors into the server's own exceptions, without putting SQL text in the message. */
    static final class Sql {

        /** MySQL's error number for "duplicate entry for a unique key". */
        private static final int MYSQL_DUPLICATE_ENTRY = 1062;

        private Sql() {
        }

        static DataAccessException translate(String message, SQLException failure) {
            if (failure.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                return new DuplicateValueException("That value already exists", failure);
            }
            return new DataAccessException(message, failure);
        }
    }
}
