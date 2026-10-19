package com.bhel.hrm.server.db;

import com.bhel.hrm.server.dao.DataAccessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the tables from {@code schema.sql}. Every statement is safe to run twice
 * ({@code IF NOT EXISTS}, {@code INSERT IGNORE}), so installing again changes nothing.
 */
public final class SchemaInstaller {

    private static final String SCRIPT = "/schema.sql";

    private SchemaInstaller() {
    }

    public static void install(Connection connection) {
        // Plain Statement is fine here: the script is our own fixed text, with no user input in it.
        try (Statement statement = connection.createStatement()) {
            for (String sql : bundledStatements()) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the database tables", e);
        }
    }

    /** The statements of the schema file that ships inside the program. */
    public static List<String> bundledStatements() {
        try (InputStream in = SchemaInstaller.class.getResourceAsStream(SCRIPT)) {
            if (in == null) {
                throw new DataAccessException("schema.sql is missing from the program");
            }
            return statements(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new DataAccessException("Could not read schema.sql", e);
        }
    }

    /**
     * Splits a script into statements at semicolons. Lines starting with {@code --} are comments.
     * This is enough for our own script; it does not understand semicolons inside quoted text.
     */
    static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(trimmed).append(' ');
            if (trimmed.endsWith(";")) {
                result.add(strip(current));
                current.setLength(0);
            }
        }
        if (current.length() > 0) {
            result.add(strip(current));
        }
        return result;
    }

    private static String strip(StringBuilder statement) {
        String text = statement.toString().trim();
        return text.endsWith(";") ? text.substring(0, text.length() - 1).trim() : text;
    }
}
