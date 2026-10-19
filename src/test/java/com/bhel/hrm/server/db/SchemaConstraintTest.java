package com.bhel.hrm.server.db;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Checks that MySQL itself refuses bad data, even if the Java code had a bug. */
class SchemaConstraintTest extends DatabaseTestBase {

    private static final StoredCredentials CREDENTIALS =
            new StoredCredentials(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1000);

    private static long anEmployee() {
        return database.transaction(da -> da.employees()
                .insert(new NewEmployee("Alice", "Tan", "SC000001", Role.EMPLOYEE, false), CREDENTIALS).id());
    }

    private static void insertLeave(long employeeId, LocalDate start, LocalDate end, int days) throws SQLException {
        String sql = "INSERT INTO leave_application (employee_id, leave_type_id, start_date, end_date, days, applied_at)"
                + " VALUES (?, 1, ?, ?, ?, ?)";
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, employeeId);
            statement.setDate(2, Date.valueOf(start));
            statement.setDate(3, Date.valueOf(end));
            statement.setInt(4, days);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 9, 0)));
            statement.executeUpdate();
        }
    }

    @Test
    void aCorrectLeaveRowIsAccepted() {
        long employee = anEmployee();

        assertDoesNotThrow(() -> insertLeave(employee, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3));
    }

    @Test
    void anEndDateBeforeTheStartDateIsRefused() {
        long employee = anEmployee();

        assertThrows(SQLException.class,
                () -> insertLeave(employee, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 10), 1));
    }

    @Test
    void aDaysValueThatDoesNotMatchTheDatesIsRefused() {
        long employee = anEmployee();

        assertThrows(SQLException.class,
                () -> insertLeave(employee, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 2));
    }

    @Test
    void leaveCrossingNewYearIsRefused() {
        long employee = anEmployee();

        assertThrows(SQLException.class,
                () -> insertLeave(employee, LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2), 4));
    }

    @Test
    void leaveForAnEmployeeThatDoesNotExistIsRefused() {
        assertThrows(SQLException.class,
                () -> insertLeave(999_999L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3));
    }

    @Test
    void aFamilyMemberWithoutAnEmployeeIsRefused() {
        String sql = "INSERT INTO family_member (employee_id, name, relationship) VALUES (999999, 'Bob', 'Spouse')";

        assertThrows(SQLException.class, () -> {
            try (Connection connection = database.open();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        });
    }

    @Test
    void installingTheSchemaAgainChangesNothing() throws SQLException {
        try (Connection connection = database.open()) {
            SchemaInstaller.install(connection);
            SchemaInstaller.install(connection);

            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM leave_type");
                    ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals(4, rows.getInt(1));
            }
        }
    }
}
