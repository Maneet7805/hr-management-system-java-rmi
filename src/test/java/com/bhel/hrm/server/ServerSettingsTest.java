package com.bhel.hrm.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerSettingsTest {

    private static final Map<String, String> ENV = Map.of("HRM_KEYSTORE_PASSWORD", "test-only-password");

    @Test
    void emptyFileGivesTheDefaults() {
        ServerSettings settings = ServerSettings.from(new Properties(), ENV);

        assertEquals(1099, settings.registryPort());
        assertEquals(1100, settings.objectPort());
        assertEquals(Path.of("config/server.keystore"), settings.keyStore());
        assertNull(settings.hostname());
        assertEquals("test-only-password", settings.keyStorePassword());
    }

    @Test
    void valuesInTheFileOverrideTheDefaults() {
        Properties props = new Properties();
        props.setProperty("rmi.port", "2000");
        props.setProperty("rmi.objectPort", "2001");
        props.setProperty("rmi.hostname", "hr-server.example");
        props.setProperty("tls.keystore", "keys/my.keystore");

        ServerSettings settings = ServerSettings.from(props, ENV);

        assertEquals(2000, settings.registryPort());
        assertEquals(2001, settings.objectPort());
        assertEquals("hr-server.example", settings.hostname());
        assertEquals(Path.of("keys/my.keystore"), settings.keyStore());
    }

    @Test
    void missingKeystorePasswordNamesTheEnvironmentVariable() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ServerSettings.from(new Properties(), Map.of()));

        assertTrue(error.getMessage().contains("HRM_KEYSTORE_PASSWORD"));
    }

    @Test
    void blankKeystorePasswordIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ServerSettings.from(new Properties(), Map.of("HRM_KEYSTORE_PASSWORD", "  ")));
    }

    @Test
    void portThatIsNotANumberNamesTheSetting() {
        Properties props = new Properties();
        props.setProperty("rmi.port", "abc");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ServerSettings.from(props, ENV));

        assertTrue(error.getMessage().contains("rmi.port"));
    }

    @Test
    void portsOutsideTheValidRangeAreRejected() {
        for (String bad : new String[] {"0", "65536", "-5"}) {
            Properties props = new Properties();
            props.setProperty("rmi.objectPort", bad);

            assertThrows(IllegalArgumentException.class, () -> ServerSettings.from(props, ENV), bad);
        }
    }

    @Test
    void toStringDoesNotRevealThePassword() {
        assertFalse(ServerSettings.from(new Properties(), ENV).toString().contains("test-only-password"));
    }

    @Test
    void loadReadsAPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, "rmi.port=2222\n");

        assertEquals(2222, ServerSettings.load(file, ENV).registryPort());
    }
}
