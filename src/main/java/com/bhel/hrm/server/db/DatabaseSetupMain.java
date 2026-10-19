package com.bhel.hrm.server.db;

import com.bhel.hrm.server.dao.DataAccessException;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Creates the tables in the database named by {@code config/server.properties}. Safe to run again.
 * Usage: {@code DatabaseSetupMain [path-to-server.properties]}; needs HRM_DB_PASSWORD in the environment.
 */
public final class DatabaseSetupMain {

    private DatabaseSetupMain() {
    }

    public static void main(String[] args) {
        Path configFile = Path.of(args.length > 0 ? args[0] : "config/server.properties");
        try {
            DbSettings settings = DbSettings.load(configFile, System.getenv());
            try (Connection connection = new JdbcDatabase(settings).open()) {
                SchemaInstaller.install(connection);
            }
            System.out.println("Database tables are ready: " + settings);
        } catch (IOException | IllegalArgumentException | DataAccessException | SQLException e) {
            System.out.println("Cannot set up the database: " + e.getMessage());
            System.exit(1);
        }
    }
}
