package com.bhel.hrm.client;

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

class ClientSettingsTest {

    private static final Map<String, String> ENV = Map.of("HRM_TRUSTSTORE_PASSWORD", "test-only-password");

    @Test
    void emptyFileGivesTheDefaults() {
        ClientSettings settings = ClientSettings.from(new Properties(), ENV);

        assertEquals("localhost", settings.host());
        assertEquals(1099, settings.port());
        assertEquals(Path.of("config/client.truststore"), settings.trustStore());
        assertEquals("test-only-password", settings.trustStorePassword());
    }

    @Test
    void valuesInTheFileOverrideTheDefaults() {
        Properties props = new Properties();
        props.setProperty("server.host", "hr-server.example");
        props.setProperty("rmi.port", "2000");
        props.setProperty("tls.truststore", "keys/my.truststore");

        ClientSettings settings = ClientSettings.from(props, ENV);

        assertEquals("hr-server.example", settings.host());
        assertEquals(2000, settings.port());
        assertEquals(Path.of("keys/my.truststore"), settings.trustStore());
    }

    @Test
    void missingTruststorePasswordNamesTheEnvironmentVariable() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ClientSettings.from(new Properties(), Map.of()));

        assertTrue(error.getMessage().contains("HRM_TRUSTSTORE_PASSWORD"));
    }

    @Test
    void portThatIsNotANumberNamesTheSetting() {
        Properties props = new Properties();
        props.setProperty("rmi.port", "abc");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ClientSettings.from(props, ENV));

        assertTrue(error.getMessage().contains("rmi.port"));
    }

    @Test
    void blankHostIsRejected() {
        Properties props = new Properties();
        props.setProperty("server.host", " ");

        assertThrows(IllegalArgumentException.class, () -> ClientSettings.from(props, ENV));
    }

    @Test
    void toStringDoesNotRevealThePassword() {
        assertFalse(ClientSettings.from(new Properties(), ENV).toString().contains("test-only-password"));
    }

    @Test
    void loadReadsAPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("client.properties");
        Files.writeString(file, "server.host=hr.example\n");

        assertEquals("hr.example", ClientSettings.load(file, ENV).host());
    }
}
