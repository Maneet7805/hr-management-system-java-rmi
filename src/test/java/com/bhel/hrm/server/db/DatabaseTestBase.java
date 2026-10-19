package com.bhel.hrm.server.db;

import static org.junit.jupiter.api.Assertions.fail;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base for tests that use the real MySQL database {@code hrm_test} (never the development one).
 *
 * <p>These tests FAIL when MySQL or {@code HRM_DB_PASSWORD} is missing, so a green run always means
 * the database was really tested. To skip them on purpose, run {@code mvn test -Dhrm.skipDbTests=true}.
 */
public abstract class DatabaseTestBase {

    private static final String TEST_URL = "jdbc:mysql://localhost:3306/hrm_test?sslMode=REQUIRED"
            + "&enabledTLSProtocols=TLSv1.3,TLSv1.2&allowPublicKeyRetrieval=false"
            + "&allowLoadLocalInfile=false&connectTimeout=5000";

    protected static JdbcDatabase database;

    @BeforeAll
    static void connectAndInstallSchema() {
        Assumptions.assumeFalse(Boolean.getBoolean("hrm.skipDbTests"),
                "database tests skipped because -Dhrm.skipDbTests=true");
        String password = System.getenv("HRM_DB_PASSWORD");
        if (password == null || password.isBlank()) {
            fail("HRM_DB_PASSWORD is not set, so the database tests cannot run. "
                    + "Set it, or skip these tests on purpose with -Dhrm.skipDbTests=true");
        }
        database = new JdbcDatabase(new DbSettings(TEST_URL, "hrm_app", password));
        try (Connection connection = database.open()) {
            SchemaInstaller.install(connection);
        } catch (SQLException | RuntimeException e) {
            fail("Cannot use the hrm_test database (is MySQL running and is hrm_app allowed to use it?): "
                    + e.getMessage());
        }
    }

    /** Every test starts with no employees, family members or leave. Leave types stay: they are seed data. */
    @BeforeEach
    void emptyTheTables() throws SQLException {
        try (Connection connection = database.open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM leave_application");
            statement.executeUpdate("DELETE FROM family_member");
            statement.executeUpdate("DELETE FROM employee");
        }
    }
}
