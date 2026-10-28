package com.bhel.hrm.net;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
        return createIdentity(dir, name, List.of(dnsName));
    }

    /**
     * A self-signed certificate valid for each of {@code addresses}. Host names become DNS entries and
     * IP addresses become IP entries, as in the key scripts: Java never accepts an IP address through a DNS entry.
     */
    public static Identity createIdentity(Path dir, String name, List<String> addresses) throws IOException {
        String san = addresses.stream()
                .map(address -> (isIpAddress(address) ? "ip:" : "dns:") + address)
                .collect(Collectors.joining(","));
        return create(dir, name, addresses.get(0), "-ext", "san=" + san);
    }

    /** A certificate like the key scripts made before they listed addresses: only a CN, no alternative names. */
    public static Identity createOldStyleIdentity(Path dir, String name, String commonName) throws IOException {
        return create(dir, name, commonName);
    }

    private static Identity create(Path dir, String name, String commonName, String... extraOptions)
            throws IOException {
        Path keyStore = dir.resolve(name + ".keystore");
        Path certificate = dir.resolve(name + ".cer");
        Path trustStore = dir.resolve(name + ".truststore");

        List<String> generate = new ArrayList<>(List.of("-genkeypair", "-alias", "hrm", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=" + commonName,
                "-keystore", keyStore.toString(), "-storetype", "PKCS12",
                "-storepass", PASSWORD, "-keypass", PASSWORD));
        generate.addAll(List.of(extraOptions));
        keytool(generate.toArray(String[]::new));
        keytool("-exportcert", "-alias", "hrm", "-keystore", keyStore.toString(),
                "-storepass", PASSWORD, "-file", certificate.toString());
        keytool("-importcert", "-noprompt", "-alias", "hrm", "-file", certificate.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
        return new Identity(keyStore, trustStore);
    }

    private static boolean isIpAddress(String address) {
        return address.matches("[0-9.]+") || address.contains(":");
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
