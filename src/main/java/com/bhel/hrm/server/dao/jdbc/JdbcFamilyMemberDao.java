package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.FamilyMemberDao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL version of {@link FamilyMemberDao}. */
final class JdbcFamilyMemberDao implements FamilyMemberDao {

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
