package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.EmployeeDao;
import com.bhel.hrm.server.dao.LoginRecord;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL version of {@link EmployeeDao}. Every query is a PreparedStatement; values are never glued into SQL. */
final class JdbcEmployeeDao implements EmployeeDao {

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
