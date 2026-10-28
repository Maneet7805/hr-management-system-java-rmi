package com.bhel.hrm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.net.TlsTestFiles.Identity;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the Windows setup scripts with PowerShell 7 ({@code pwsh}) in a throwaway copy of the project. Only the
 * Windows-only steps are replaced: {@code cmd} (which runs the .bat files) and {@code mvn}. Everything else, including
 * the order of the checks and the keytool calls, runs for real. Skipped where pwsh is not installed; Windows itself
 * (saved user settings, the .bat files, the firewall) is still checked by hand.
 */
@EnabledIf("pwshIsInstalled")
@Timeout(120)
class PowerShellScriptsTest {

    private static final Path PWSH = findOnPath("pwsh");

    /**
     * Stand-ins for the Windows-only commands. Each call to cmd is written to the log. The import of a certificate
     * succeeds, by writing a new truststore like import-server-cert.bat does, only when TEST_ANSWER is yes.
     */
    private static final String STAND_INS = """
            function global:cmd {
                Add-Content -Path $env:TEST_LOG -Value ($args -join ' ')
                $global:LASTEXITCODE = 0
                if ("$args" -like '*import-server-cert*') {
                    if ($env:TEST_ANSWER -eq 'yes') { Set-Content -Path 'config/client.truststore' -Value 'new truststore' }
                    else { $global:LASTEXITCODE = 1 }
                }
            }
            function global:mvn {
                if ("$args" -like '*copy-dependencies*') { New-Item -ItemType Directory -Force 'target/lib' | Out-Null }
                $global:LASTEXITCODE = 0
            }
            """;

    @TempDir
    Path dir;

    private Path project;
    private Path log;

    private record Result(int exitCode, String output) {
    }

    static boolean pwshIsInstalled() {
        return PWSH != null;
    }

    private static Path findOnPath(String program) {
        String[] names = {program, program + ".exe"};
        return Arrays.stream(System.getenv("PATH").split(File.pathSeparator))
                .flatMap(folder -> Arrays.stream(names).map(name -> Path.of(folder, name)))
                .filter(Files::isExecutable)
                .findFirst().orElse(null);
    }

    @BeforeEach
    void copyTheScripts() throws IOException {
        project = dir.resolve("project");
        Files.createDirectories(project.resolve("scripts"));
        Files.createDirectories(project.resolve("config"));
        for (String script : List.of("common.ps1", "setup.ps1", "setup-client.ps1")) {
            Files.copy(Path.of("scripts", script), project.resolve("scripts").resolve(script));
        }
        for (String template : List.of("client.example.properties", "server.example.properties")) {
            Files.copy(Path.of("config", template), project.resolve("config").resolve(template));
        }
        log = dir.resolve("calls.log");
        Files.writeString(log, "");
    }

    /** Runs PowerShell commands from the project folder with only the given HRM_* variables set. */
    private Result powershell(Map<String, String> variables, String commands) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(PWSH.toString(), "-NoProfile", "-NonInteractive", "-Command",
                STAND_INS + commands + "\nexit $LASTEXITCODE")
                .directory(project.toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(name -> name.startsWith("HRM_"));
        environment.putAll(variables);
        environment.put("TEST_LOG", log.toString());
        // keytool from the JDK that runs the tests.
        environment.put("PATH", Path.of(System.getProperty("java.home"), "bin") + File.pathSeparator
                + environment.get("PATH"));
        Process process = builder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        return new Result(process.waitFor(), output);
    }

    private Path config(String name) {
        return project.resolve("config").resolve(name);
    }

    private void installKeys(Path keyStore, Path certificate, Path trustStore) throws IOException {
        Files.copy(keyStore, config("server.keystore"));
        Files.copy(certificate, config("server.cer"));
        Files.copy(trustStore, config("client.truststore"));
    }

    private static Path certificateOf(Identity identity) {
        String keyStore = identity.keyStore().toString();
        return Path.of(keyStore.substring(0, keyStore.length() - ".keystore".length()) + ".cer");
    }

    private String keysProblem(String password, String... addresses) throws Exception {
        String list = addresses.length == 0 ? "@()" : "@('" + String.join("','", addresses) + "')";
        Result result = powershell(Map.of("HRM_KEYSTORE_PASSWORD", TlsTestFiles.PASSWORD,
                "HRM_TRUSTSTORE_PASSWORD", password),
                ". ./scripts/common.ps1\n$p = Get-KeysProblem " + list
                        + "\nif ($p) { 'PROBLEM: ' + $p } else { 'REUSABLE' }"
                        // A refused keytool call leaves its exit code behind; the answer is in the output.
                        + "\n$global:LASTEXITCODE = 0");
        assertEquals(0, result.exitCode(), result.output());
        return result.output().trim();
    }

    // ---- Sourcery finding 7: files from different key generations must not be reused ----

    @Test
    void keysThatBelongTogetherAreReused() throws Exception {
        Identity keys = TlsTestFiles.createIdentity(dir, "a", List.of("localhost", "127.0.0.1", "192.0.2.10"));
        installKeys(keys.keyStore(), certificateOf(keys), keys.trustStore());

        assertEquals("REUSABLE", keysProblem(TlsTestFiles.PASSWORD, "192.0.2.10"));
    }

    @Test
    void aTruststoreFromOtherKeysIsNotReused() throws Exception {
        Identity keys = TlsTestFiles.createIdentity(dir, "a", "localhost");
        Identity older = TlsTestFiles.createIdentity(dir, "b", "localhost");
        installKeys(keys.keyStore(), certificateOf(keys), older.trustStore());

        assertTrue(keysProblem(TlsTestFiles.PASSWORD).contains("do not hold the same certificate"));
    }

    @Test
    void aServerCerFromOtherKeysIsNotReused() throws Exception {
        Identity keys = TlsTestFiles.createIdentity(dir, "a", "localhost");
        Identity older = TlsTestFiles.createIdentity(dir, "b", "localhost");
        installKeys(keys.keyStore(), certificateOf(older), keys.trustStore());

        assertTrue(keysProblem(TlsTestFiles.PASSWORD).contains("do not hold the same certificate"));
    }

    @Test
    void keysAreNotReusedForAnAddressTheyDoNotList() throws Exception {
        Identity keys = TlsTestFiles.createIdentity(dir, "a", "localhost");
        installKeys(keys.keyStore(), certificateOf(keys), keys.trustStore());

        assertTrue(keysProblem(TlsTestFiles.PASSWORD, "192.0.2.10").contains("does not include 192.0.2.10"));
        assertTrue(keysProblem("wrong-password").contains("does not open config\\client.truststore"));
    }

    // ---- Sourcery finding 8: never replace the keys under a running server ----

    @Test
    void setupRefusesToMakeNewKeysWhileTheServerIsRunning() throws Exception {
        for (String file : List.of("server.keystore", "server.cer", "client.truststore")) {
            Files.writeString(config(file), "old " + file);
        }
        try (ServerSocket runningServer = new ServerSocket(0)) {
            Files.writeString(config("server.properties"), "rmi.port=" + runningServer.getLocalPort() + "\n");

            Result result = powershell(Map.of("HRM_DB_PASSWORD", "unused", "HRM_KEYSTORE_PASSWORD", "key-password",
                    "HRM_TRUSTSTORE_PASSWORD", "trust-password"), "& ./scripts/setup.ps1 -NoSaveSettings -SkipSeed");

            assertNotEquals(0, result.exitCode());
            assertTrue(result.output().contains("listening on port " + runningServer.getLocalPort()), result.output());
        }
        assertEquals("old server.keystore", Files.readString(config("server.keystore")));
        assertFalse(Files.readString(log).contains("generate-keys"), "no new keys were made");
    }

    // ---- Sourcery finding 6: -NoSaveSettings must not invent a password that is then lost ----

    @Test
    void setupWithoutSavingNeedsBothPasswordsFirst() throws Exception {
        Result result = powershell(Map.of("HRM_DB_PASSWORD", "unused"), "& ./scripts/setup.ps1 -NoSaveSettings -SkipSeed");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("With -NoSaveSettings"), result.output());
        assertFalse(Files.readString(log).contains("generate-keys"));
    }

    @Test
    void clientSetupWithoutSavingNeedsThePasswordFirst() throws Exception {
        Files.writeString(config("server.cer"), "certificate");

        Result result = powershell(Map.of(),
                "& ./scripts/setup-client.ps1 -ServerAddress 192.0.2.10 -NoSaveSettings");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("With -NoSaveSettings"), result.output());
        assertEquals("", Files.readString(log), "nothing was imported");
    }

    // ---- Sourcery findings 4 and 5: a mistake or a "no" leaves a working client setup as it was ----

    private Map<String, String> existingClient(String answer) throws IOException {
        Files.writeString(config("server.cer"), "certificate");
        Files.writeString(config("client.truststore"), "old truststore");
        Files.writeString(config("client.properties"), "server.host=old-server\n");
        Map<String, String> variables = new HashMap<>();
        variables.put("HRM_TRUSTSTORE_PASSWORD", "client-password");
        variables.put("TEST_ANSWER", answer);
        return variables;
    }

    @Test
    void anInvalidAddressIsRefusedBeforeTheTruststoreIsTouched() throws Exception {
        Map<String, String> variables = existingClient("yes");

        Result result = powershell(variables, "& ./scripts/setup-client.ps1 -ServerAddress 'bad;name' -NoSaveSettings");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("is not a host name or IP address"), result.output());
        assertEquals("", Files.readString(log), "the certificate was not imported");
        assertEquals("old truststore", Files.readString(config("client.truststore")).trim());
        assertEquals("server.host=old-server", Files.readString(config("client.properties")).trim());
    }

    @Test
    void aRefusedCertificateChangesNothing() throws Exception {
        Map<String, String> variables = existingClient("no");

        Result result = powershell(variables, "& ./scripts/setup-client.ps1 -ServerAddress 192.0.2.10 -NoSaveSettings");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.output().contains("Nothing on this computer was changed"), result.output());
        assertEquals("old truststore", Files.readString(config("client.truststore")).trim());
        assertEquals("server.host=old-server", Files.readString(config("client.properties")).trim());
    }

    @Test
    void anAcceptedCertificateThenPointsTheClientAtTheServer() throws Exception {
        Map<String, String> variables = existingClient("yes");

        Result result = powershell(variables, "& ./scripts/setup-client.ps1 -ServerAddress 192.0.2.10 -NoSaveSettings");

        assertEquals(0, result.exitCode(), result.output());
        assertEquals("new truststore", Files.readString(config("client.truststore")).trim());
        assertEquals("server.host=192.0.2.10", Files.readString(config("client.properties")).trim());
    }
}
