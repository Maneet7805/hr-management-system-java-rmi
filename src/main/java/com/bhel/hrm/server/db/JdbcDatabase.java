package com.bhel.hrm.server.db;

import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.Work;
import com.bhel.hrm.server.dao.jdbc.JdbcDataAccess;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * MySQL version of {@link Database}. It opens one connection per call and has no connection pool,
 * which is enough for this project's size and keeps the code easy to follow.
 */
public final class JdbcDatabase implements Database {

    private final DbSettings settings;

    public JdbcDatabase(DbSettings settings) {
        this.settings = settings;
    }

    /** A new connection. The caller must close it. */
    public Connection open() {
        try {
            return DriverManager.getConnection(settings.url(), settings.user(), settings.password());
        } catch (SQLException e) {
            // The cause is kept for the server log; the message is safe to show and holds no password.
            throw new DataAccessException(
                    "Cannot connect to the database. Check that MySQL is running and the settings are right.", e);
        }
    }

    @Override
    public <T, E extends Exception> T read(Work<T, E> work) throws E {
        try (Connection connection = open()) {
            return work.run(new JdbcDataAccess(connection));
        } catch (SQLException e) {
            throw new DataAccessException("Database error", e);
        }
    }

    @Override
    public <T, E extends Exception> T transaction(Work<T, E> work) throws E {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(new JdbcDataAccess(connection));
                connection.commit();
                return result;
            } catch (Exception | Error failure) {
                rollback(connection, failure);
                throw failure;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error", e);
        }
    }

    /** Undoes the work. If even that fails, the second error is attached to the first, not lost. */
    private static void rollback(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }
}
