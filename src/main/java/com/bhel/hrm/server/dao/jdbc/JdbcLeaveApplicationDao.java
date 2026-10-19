package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.LeaveApplicationDao;
import com.bhel.hrm.server.dao.NewLeaveApplication;
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

/** MySQL version of {@link LeaveApplicationDao}. Every query is a PreparedStatement. */
final class JdbcLeaveApplicationDao implements LeaveApplicationDao {

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
