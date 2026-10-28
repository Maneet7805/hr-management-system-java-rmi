package com.bhel.hrm.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    private static ServerSettings advertising(String hostname) {
        return new ServerSettings(1099, 1100, Path.of("config/server.keystore"), "test-only-password", hostname);
    }

    @Test
    void addressNoteGivesTheAddressOtherComputersUse() {
        String note = advertising("192.168.1.20").addressNote(List.of("localhost", "127.0.0.1", "192.168.1.20"));

        assertTrue(note.contains("server.host=192.168.1.20"), note);
    }

    @Test
    void addressNoteSaysWhenOnlyThisComputerCanConnect() {
        String note = advertising("localhost").addressNote(List.of("localhost", "127.0.0.1"));

        assertTrue(note.startsWith("Only programs on this computer can connect"), note);
        assertTrue(note.contains("docs/two-computer-setup.md"), note);
    }

    /** Sourcery finding 9: the expanded spelling of ::1 was reported as reachable by other computers. */
    @Test
    void addressNoteRecognisesEverySpellingOfTheLoopbackAddress() {
        for (String loopback : new String[] {"::1", "0:0:0:0:0:0:0:1", "127.0.0.1", "127.1.2.3"}) {
            String note = advertising(loopback).addressNote(List.of(loopback));

            assertTrue(note.startsWith("Only programs on this computer can connect"), loopback + ": " + note);
        }
    }

    /** A host name that merely starts with "127." is a real network name, not this computer. */
    @Test
    void addressNoteDoesNotMistakeANameForTheLoopbackAddress() {
        String note = advertising("127.example.com").addressNote(List.of("127.example.com"));

        assertTrue(note.contains("server.host=127.example.com"), note);
    }

    @Test
    void addressNoteWarnsWhenTheCertificateDoesNotCoverTheAdvertisedAddress() {
        String note = advertising("192.168.1.20").addressNote(List.of("localhost", "127.0.0.1"));

        assertTrue(note.startsWith("WARNING"), note);
        assertTrue(note.contains("192.168.1.20"), note);
    }

    @Test
    void addressNoteSaysWhenNoAddressIsSet() {
        String note = advertising(null).addressNote(List.of("localhost"));

        assertTrue(note.startsWith("rmi.hostname is not set"), note);
    }

    @Test
    void loadReadsAPropertiesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, "rmi.port=2222\n");

        assertEquals(2222, ServerSettings.load(file, ENV).registryPort());
    }
}
