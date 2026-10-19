package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.server.dao.LeaveTypeDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL version of {@link LeaveTypeDao}. */
final class JdbcLeaveTypeDao implements LeaveTypeDao {

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
