package com.bhel.hrm.server.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbSettingsTest {

    private static final Map<String, String> ENV = Map.of("HRM_DB_PASSWORD", "test-only-db-password");

    @Test
    void emptyFileGivesTheLocalDevelopmentDefaults() {
        DbSettings settings = DbSettings.from(new Properties(), ENV);

        assertTrue(settings.url().startsWith("jdbc:mysql://localhost:3306/hrm?"), settings.url());
        assertTrue(settings.url().contains("sslMode=REQUIRED"));
        assertEquals("hrm_app", settings.user());
        assertEquals("test-only-db-password", settings.password());
    }

    @Test
    void valuesInTheFileOverrideTheDefaults() {
        Properties props = new Properties();
        props.setProperty("db.url", "jdbc:mysql://db.example:3306/other");
        props.setProperty("db.user", "someone");

        DbSettings settings = DbSettings.from(props, ENV);

        assertEquals("jdbc:mysql://db.example:3306/other", settings.url());
        assertEquals("someone", settings.user());
    }

    @Test
    void missingPasswordNamesTheEnvironmentVariable() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> DbSettings.from(new Properties(), Map.of()));

        assertTrue(error.getMessage().contains("HRM_DB_PASSWORD"));
    }

    @Test
    void blankUrlIsRejected() {
        Properties props = new Properties();
        props.setProperty("db.url", " ");

        assertThrows(IllegalArgumentException.class, () -> DbSettings.from(props, ENV));
    }

    @Test
    void toStringDoesNotRevealThePassword() {
        assertFalse(DbSettings.from(new Properties(), ENV).toString().contains("test-only-db-password"));
    }

    @Test
    void loadReadsAPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, "db.user=from_file\n");

        assertEquals("from_file", DbSettings.load(file, ENV).user());
    }
}
