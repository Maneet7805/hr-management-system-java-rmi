package com.bhel.hrm.server.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.server.dao.DataAccessException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Needs no MySQL: it points at a port where nothing listens. */
class JdbcDatabaseConnectionTest {

    private static final String SECRET = "super-secret-database-password";

    @Test
    void anUnreachableDatabaseGivesAReadableMessageWithoutThePassword() throws IOException {
        int closedPort = TlsTestFiles.freePort();
        JdbcDatabase database = new JdbcDatabase(new DbSettings(
                "jdbc:mysql://localhost:" + closedPort + "/hrm_test?connectTimeout=2000", "hrm_app", SECRET));

        DataAccessException error = assertThrows(DataAccessException.class, () -> database.read(da -> null));

        assertTrue(error.getMessage().startsWith("Cannot connect to the database"), error.getMessage());
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            assertFalse(String.valueOf(cause.getMessage()).contains(SECRET), "password leaked in: " + cause);
        }
    }
}
