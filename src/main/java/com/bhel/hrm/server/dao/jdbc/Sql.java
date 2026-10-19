package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.DuplicateValueException;
import java.sql.SQLException;

/** Turns JDBC errors into the server's own exceptions, without putting SQL text in the message. */
final class Sql {

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
