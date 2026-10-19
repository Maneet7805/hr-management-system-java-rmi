package com.bhel.hrm.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Test helper: makes a throwaway server identity (keystore for the server, truststore for the client)
 * in a temporary folder using the JDK's own keytool. Nothing here is a real secret, and nothing is
 * written inside the project.
 */
public final class TlsTestFiles {

    /** Password for every throwaway store. Not a secret: the stores are deleted after the test run. */
    public static final String PASSWORD = "test-only-password";

    public record Identity(Path keyStore, Path trustStore) {
    }

    private TlsTestFiles() {
    }

    /** A self-signed certificate valid only for {@code dnsName}. */
    public static Identity createIdentity(Path dir, String name, String dnsName) throws IOException {
        Path keyStore = dir.resolve(name + ".keystore");
        Path certificate = dir.resolve(name + ".cer");
        Path trustStore = dir.resolve(name + ".truststore");

        keytool("-genkeypair", "-alias", "hrm", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=" + dnsName, "-ext", "san=dns:" + dnsName,
                "-keystore", keyStore.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD);
        keytool("-exportcert", "-alias", "hrm", "-keystore", keyStore.toString(),
                "-storepass", PASSWORD, "-file", certificate.toString());
        keytool("-importcert", "-noprompt", "-alias", "hrm", "-file", certificate.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
        return new Identity(keyStore, trustStore);
    }

    /** A port nothing is listening on right now. */
    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void keytool(String... arguments) throws IOException {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool";
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.addAll(List.of(arguments));

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (process.waitFor() != 0) {
                throw new IllegalStateException("keytool failed: " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running keytool", e);
        }
    }
}
