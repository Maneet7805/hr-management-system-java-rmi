package com.bhel.hrm;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ClientSettings;
import com.bhel.hrm.client.ServerConnection;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.net.RmiNames;
import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.net.TlsTestFiles.Identity;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * The address rules that decide whether a client on another computer can connect. The loopback address 127.0.0.1
 * stands in for the server's network address, so these run on one machine. They cannot show firewall or Wi-Fi
 * problems: the real two-computer check is manual (docs/two-computer-setup.md).
 */
@Timeout(30)
class ServerAddressTlsTest {

    private static final String PASSWORD = TlsTestFiles.PASSWORD;

    /** What the key scripts put in every certificate, with 127.0.0.1 playing the server's network address. */
    private static final List<String> SERVER_NAMES = List.of("localhost", "127.0.0.1");

    @TempDir
    static Path dir;

    private static ClientSettings client(String host, TestServer server, Identity trusted) {
        return new ClientSettings(host, server.registryPort(), trusted.trustStore(), PASSWORD);
    }

    @Test
    void aClientConnectingByIpAddressIsAccepted() throws Exception {
        Identity identity = TlsTestFiles.createIdentity(dir, "by-ip", SERVER_NAMES);
        try (TestServer server = TestServer.start(identity.keyStore(), PASSWORD, "127.0.0.1")) {
            HRMService service = ServerConnection.connect(client("127.0.0.1", server, identity));

            assertDoesNotThrow(service::ping);
        }
    }

    /** One computer must keep working: the default settings advertise localhost and the client uses localhost. */
    @Test
    void theSingleComputerSetupStillWorks() throws Exception {
        Identity identity = TlsTestFiles.createIdentity(dir, "single", SERVER_NAMES);
        try (TestServer server = TestServer.start(identity.keyStore(), PASSWORD, "localhost")) {
            HRMService service = ServerConnection.connect(client("localhost", server, identity));

            assertDoesNotThrow(service::ping);
        }
    }

    /**
     * The client on the server computer may keep server.host=localhost after rmi.hostname is changed to the
     * network address: it finds the registry through localhost, then follows the stub to the advertised address.
     */
    @Test
    void aClientUsingLocalhostFollowsTheAdvertisedAddress() throws Exception {
        Identity identity = TlsTestFiles.createIdentity(dir, "follows", SERVER_NAMES);
        try (TestServer server = TestServer.start(identity.keyStore(), PASSWORD, "127.0.0.1")) {
            HRMService service = ServerConnection.connect(client("localhost", server, identity));

            assertDoesNotThrow(service::ping);
        }
    }

    /** For example, the server computer got a new IP address after its keys were made. */
    @Test
    void anAddressMissingFromTheCertificateIsRefused() throws Exception {
        Identity identity = TlsTestFiles.createIdentity(dir, "old-address", List.of("localhost", "192.0.2.10"));
        try (TestServer server = TestServer.start(identity.keyStore(), PASSWORD, "127.0.0.1")) {
            ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                    () -> ServerConnection.connect(client("127.0.0.1", server, identity)));

            assertTrue(error.getMessage().contains("certificate"), error.getMessage());
        }
    }

    /**
     * rmi.hostname names an address the certificate does not cover. The registry lookup still works, because the
     * client chose that address itself; the first real call goes to the advertised address and is refused.
     */
    @Test
    void aServerAdvertisingAnAddressOutsideItsCertificateIsRefused() throws Exception {
        Identity identity = TlsTestFiles.createIdentity(dir, "wrong-hostname", "localhost");
        try (TestServer server = TestServer.start(identity.keyStore(), PASSWORD, "127.0.0.1")) {
            ClientSettings settings = client("localhost", server, identity);
            HRMService service = ServerConnection.connect(settings);

            RemoteException error = assertThrows(RemoteException.class, service::ping);
            assertTrue(ServerConnection.explain(error, settings).contains("certificate"));
        }
    }

    /** Another server on the right address, with its own certificate for that address, is still not trusted. */
    @Test
    void aCertificateForTheRightAddressFromAnotherServerIsRefused() throws Exception {
        Identity real = TlsTestFiles.createIdentity(dir, "real", SERVER_NAMES);
        Identity impostor = TlsTestFiles.createIdentity(dir, "impostor", SERVER_NAMES);
        try (TestServer server = TestServer.start(impostor.keyStore(), PASSWORD, "127.0.0.1")) {
            ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class,
                    () -> ServerConnection.connect(client("127.0.0.1", server, real)));

            assertTrue(error.getMessage().contains("certificate"), error.getMessage());
        }
    }

    @Test
    void aPlainRmiClientCannotUseTheServerByIpAddress() throws Exception {
        Identity identity = TlsTestFiles.createIdentity(dir, "plain", SERVER_NAMES);
        try (TestServer server = TestServer.start(identity.keyStore(), PASSWORD, "127.0.0.1")) {
            Registry plain = LocateRegistry.getRegistry("127.0.0.1", server.registryPort());

            assertThrows(RemoteException.class, () -> plain.lookup(RmiNames.SERVICE_NAME));
        }
    }
}
