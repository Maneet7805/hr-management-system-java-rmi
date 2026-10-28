package com.bhel.hrm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ClientSettings;
import com.bhel.hrm.client.ServerConnection;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.net.TlsContexts;
import com.bhel.hrm.server.CertificateNames;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the real key scripts (the macOS/Linux versions, as CI does) in a temporary folder and checks what they make.
 * The Windows .bat versions follow the same steps but are checked by hand (docs/two-computer-setup.md).
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "runs the .sh scripts; the .bat versions are checked by hand")
@Timeout(60)
class KeyScriptsTest {

    private static final Path SCRIPTS = Path.of("scripts").toAbsolutePath();
    private static final String KEYSTORE_PASSWORD = "server-only-password";
    private static final String TRUSTSTORE_PASSWORD = "server-side-client-password";
    private static final Map<String, String> SERVER_PASSWORDS = Map.of(
            "HRM_KEYSTORE_PASSWORD", KEYSTORE_PASSWORD, "HRM_TRUSTSTORE_PASSWORD", TRUSTSTORE_PASSWORD);

    @TempDir
    Path dir;

    /** Stands in for the project folder on the server computer. */
    private Path serverComputer;

    private record Result(int exitCode, String output) {
    }

    @BeforeEach
    void makeServerComputer() throws IOException {
        serverComputer = Files.createDirectories(dir.resolve("server-computer/config")).getParent();
    }

    /** Runs a script from {@code projectFolder}, as the instructions say, with only the given passwords set. */
    private static Result run(Path projectFolder, Map<String, String> passwords, String typed, String script,
            String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("sh", SCRIPTS.resolve(script).toString()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command).directory(projectFolder.toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.remove("HRM_KEYSTORE_PASSWORD");
        environment.remove("HRM_TRUSTSTORE_PASSWORD");
        environment.putAll(passwords);
        // The scripts call keytool from the PATH; use the one belonging to the JDK that runs the tests.
        environment.put("PATH", Path.of(System.getProperty("java.home"), "bin") + File.pathSeparator
                + environment.get("PATH"));

        Process process = builder.start();
        try (OutputStream keyboard = process.getOutputStream()) {
            keyboard.write(typed.getBytes(UTF_8));
        }
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        return new Result(process.waitFor(), output);
    }

    private Result generateKeys(String... addresses) throws IOException, InterruptedException {
        return run(serverComputer, SERVER_PASSWORDS, "", "generate-keys.sh", addresses);
    }

    private Path serverFile(String name) {
        return serverComputer.resolve("config").resolve(name);
    }

    @Test
    void theCertificateIsValidForLocalhostAndEveryGivenAddress() throws Exception {
        Result result = generateKeys("192.0.2.10", "hr-server.local");

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(List.of("localhost", "127.0.0.1", "192.0.2.10", "hr-server.local"),
                CertificateNames.read(serverFile("server.keystore"), KEYSTORE_PASSWORD));
        assertTrue(Files.exists(serverFile("server.cer")));
        assertTrue(result.output().contains("SHA256"), "prints the fingerprint to compare: " + result.output());
    }

    @Test
    void theTruststoreHasItsOwnPassword() throws Exception {
        assertEquals(0, generateKeys().exitCode());

        assertDoesNotThrow(() -> TlsContexts.forClient(serverFile("client.truststore"), TRUSTSTORE_PASSWORD));
        assertThrows(IllegalStateException.class,
                () -> TlsContexts.forClient(serverFile("client.truststore"), KEYSTORE_PASSWORD));
    }

    @Test
    void oneSharedPasswordIsRefused() throws Exception {
        Result result = run(serverComputer, Map.of("HRM_KEYSTORE_PASSWORD", "same-password",
                "HRM_TRUSTSTORE_PASSWORD", "same-password"), "", "generate-keys.sh");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("must be different"), result.output());
        assertFalse(Files.exists(serverFile("server.keystore")));
    }

    @Test
    void aMissingTruststorePasswordIsRefused() throws Exception {
        Result result = run(serverComputer, Map.of("HRM_KEYSTORE_PASSWORD", KEYSTORE_PASSWORD), "",
                "generate-keys.sh");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("HRM_TRUSTSTORE_PASSWORD"), result.output());
    }

    /** A comma would otherwise smuggle an extra name into the certificate. */
    @Test
    void anAddressThatIsNotANameOrIpIsRefused() throws Exception {
        Result result = generateKeys("hr-server,dns:attacker.example");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("is not a host name or IP address"), result.output());
        assertFalse(Files.exists(serverFile("server.keystore")));
    }

    @Test
    void existingKeysAreNeverOverwritten() throws Exception {
        assertEquals(0, generateKeys().exitCode());
        byte[] firstKeys = Files.readAllBytes(serverFile("server.keystore"));

        Result second = generateKeys("192.0.2.10");

        assertNotEquals(0, second.exitCode());
        assertTrue(second.output().contains("already exists"), second.output());
        assertArrayEquals(firstKeys, Files.readAllBytes(serverFile("server.keystore")));
    }

    /**
     * The whole two-computer flow on one machine: the client computer gets only server.cer, makes its own truststore
     * with its own password, and connects by IP address to a server using the keys the script made.
     */
    @Test
    void aClientComputerTrustsTheServerWithOnlyTheCertificateAndItsOwnPassword() throws Exception {
        assertEquals(0, generateKeys("192.0.2.10").exitCode());
        Path clientComputer = Files.createDirectories(dir.resolve("client-computer/config")).getParent();
        Files.copy(serverFile("server.cer"), clientComputer.resolve("config/server.cer"));
        String clientPassword = "client-computer-password";

        Result imported = run(clientComputer, Map.of("HRM_TRUSTSTORE_PASSWORD", clientPassword), "yes\n",
                "import-server-cert.sh");

        assertEquals(0, imported.exitCode(), imported.output());
        assertFalse(Files.exists(clientComputer.resolve("config/server.keystore")), "no server secret is needed");
        try (TestServer server = TestServer.start(serverFile("server.keystore"), KEYSTORE_PASSWORD, "127.0.0.1")) {
            HRMService service = ServerConnection.connect(new ClientSettings("127.0.0.1", server.registryPort(),
                    clientComputer.resolve("config/client.truststore"), clientPassword));

            assertDoesNotThrow(service::ping);
        }
    }

    @Test
    void answeringNoKeepsTheOldTruststore() throws Exception {
        assertEquals(0, generateKeys().exitCode());
        Path clientComputer = Files.createDirectories(dir.resolve("client-computer/config")).getParent();
        Files.copy(serverFile("server.cer"), clientComputer.resolve("config/server.cer"));
        Path truststore = clientComputer.resolve("config/client.truststore");
        Files.writeString(truststore, "the old truststore");

        Result refused = run(clientComputer, Map.of("HRM_TRUSTSTORE_PASSWORD", "client-computer-password"), "no\n",
                "import-server-cert.sh");

        assertNotEquals(0, refused.exitCode());
        assertTrue(refused.output().contains("was not trusted"), refused.output());
        assertEquals("the old truststore", Files.readString(truststore));
    }
}
