package com.bhel.hrm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ClientSettings;
import com.bhel.hrm.client.ServerConnection;
import com.bhel.hrm.client.SmokeTestMain;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.net.RmiNames;
import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.net.TlsTestFiles.Identity;
import com.bhel.hrm.server.HRMServiceImpl;
import com.bhel.hrm.server.RmiServer;
import com.bhel.hrm.server.ServerSettings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Starts a real server on this machine and talks to it through the same code the GUI will use. */
@Timeout(30)
class RmiOverTlsTest {

    private static final String PASSWORD = TlsTestFiles.PASSWORD;

    @TempDir
    static Path dir;

    private static Identity serverIdentity;
    private static Identity otherIdentity;
    private static int registryPort;
    private static RmiServer server;

    @BeforeAll
    static void startServer() throws IOException {
        serverIdentity = TlsTestFiles.createIdentity(dir, "server", "localhost");
        otherIdentity = TlsTestFiles.createIdentity(dir, "other", "localhost");
        registryPort = TlsTestFiles.freePort();
        int objectPort = TlsTestFiles.freePort();
        ServerSettings settings = new ServerSettings(registryPort, objectPort, serverIdentity.keyStore(),
                PASSWORD, "localhost");
        server = RmiServer.start(settings, new HRMServiceImpl());
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    private static ClientSettings client(String host, int port, Path trustStore) {
        return new ClientSettings(host, port, trustStore, PASSWORD);
    }

    @Test
    void pingSucceedsWithTheRightTruststore() throws Exception {
        HRMService service = ServerConnection.connect(client("localhost", registryPort, serverIdentity.trustStore()));

        assertDoesNotThrow(service::ping);
    }

    @Test
    void aClientThatDoesNotTrustTheServerCertificateIsRefused() {
        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> ServerConnection.connect(client("localhost", registryPort, otherIdentity.trustStore())));

        assertTrue(error.getMessage().contains("certificate"), error.getMessage());
    }

    @Test
    void theServerNameMustMatchTheCertificate() {
        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> ServerConnection.connect(client("127.0.0.1", registryPort, serverIdentity.trustStore())));

        assertTrue(error.getMessage().contains("certificate"), error.getMessage());
    }

    @Test
    void aPlainRmiClientCannotUseTheServer() throws Exception {
        Registry plain = LocateRegistry.getRegistry("localhost", registryPort);

        assertThrows(RemoteException.class, () -> plain.lookup(RmiNames.SERVICE_NAME));
    }

    @Test
    void aRawSocketGetsNoRmiHandshakeReply() throws IOException {
        int protocolAck = 0x4E;
        try (Socket socket = new Socket("localhost", registryPort)) {
            socket.setSoTimeout(3000);
            OutputStream out = socket.getOutputStream();
            out.write(new byte[] {'J', 'R', 'M', 'I', 0, 2, 0x4B});
            out.flush();
            int reply;
            try {
                reply = socket.getInputStream().read();
            } catch (IOException e) {
                reply = -1;
            }

            assertNotEquals(protocolAck, reply);
        }
    }

    @Test
    void whenNothingListensTheMessageIsOneReadableLine() throws IOException {
        int closedPort = TlsTestFiles.freePort();

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> ServerConnection.connect(client("localhost", closedPort, serverIdentity.trustStore())));

        assertTrue(error.getMessage().startsWith("Cannot reach the HR server at localhost:" + closedPort),
                error.getMessage());
        assertFalse(error.getMessage().contains("Exception"));
    }

    @Test
    void aMissingTruststoreFileIsReportedByName() {
        Path missing = dir.resolve("missing.truststore");

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> ServerConnection.connect(client("localhost", registryPort, missing)));

        assertTrue(error.getMessage().contains("missing.truststore"), error.getMessage());
    }

    @Test
    void aContractDataClassPassesTheFilterAndReachesTheService() throws Exception {
        HRMService service = ServerConnection.connect(client("localhost", registryPort, serverIdentity.trustStore()));
        FamilyMember member = new FamilyMember(1L, "Bob Tan", "Spouse", null);

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                () -> service.updateFamilyMember("any-token", member));

        assertEquals("Not implemented yet", error.getMessage());
    }

    @Test
    void smokeToolReportsSuccessAndFailureWithExitCodes() throws IOException {
        ByteArrayOutputStream good = new ByteArrayOutputStream();
        int okCode = SmokeTestMain.run(client("localhost", registryPort, serverIdentity.trustStore()),
                new PrintStream(good, true, StandardCharsets.UTF_8));

        ByteArrayOutputStream bad = new ByteArrayOutputStream();
        int failCode = SmokeTestMain.run(client("localhost", TlsTestFiles.freePort(), serverIdentity.trustStore()),
                new PrintStream(bad, true, StandardCharsets.UTF_8));

        assertEquals(0, okCode);
        assertTrue(good.toString(StandardCharsets.UTF_8).contains("Server reachable over TLS"));
        assertEquals(1, failCode);
        assertTrue(bad.toString(StandardCharsets.UTF_8).contains("Cannot reach"));
    }
}
