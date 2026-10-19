package com.bhel.hrm.client;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.net.TlsTestFiles.Identity;
import com.bhel.hrm.server.HRMServiceImpl;
import com.bhel.hrm.server.RmiServer;
import com.bhel.hrm.server.ServerSettings;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.service.AuthService;
import com.bhel.hrm.server.service.EmployeeService;
import com.bhel.hrm.server.service.LeaveService;
import com.bhel.hrm.server.service.ReportService;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** The fault tolerance the brief asks for, with a real secure server that is really stopped and started. */
@Timeout(60)
class ResilientServiceServerRestartTest {

    private static final long[] PAUSES = {50, 100};

    @TempDir
    Path dir;

    private static HRMServiceImpl newService() {
        FakeDatabase database = new FakeDatabase();
        CountingHasher hasher = new CountingHasher();
        AuthService auth = new AuthService(database, hasher, new SessionManager(Clock.systemUTC()));
        return new HRMServiceImpl(database, auth, new EmployeeService(database, hasher, auth),
                new LeaveService(database, auth, Clock.systemDefaultZone()), new ReportService(database, auth));
    }

    @Test
    void aRunningClientRecoversWhenTheServerIsRestartedOnTheSamePorts() throws IOException {
        Identity identity = TlsTestFiles.createIdentity(dir, "server", "localhost");
        int registryPort = TlsTestFiles.freePort();
        int objectPort = TlsTestFiles.freePort();
        ServerSettings server = new ServerSettings(registryPort, objectPort, identity.keyStore(),
                TlsTestFiles.PASSWORD, "localhost");
        ClientSettings client = new ClientSettings("localhost", registryPort, identity.trustStore(),
                TlsTestFiles.PASSWORD);
        HRMService service = ResilientService.create(() -> ServerConnection.connect(client), PAUSES, Thread::sleep);

        RmiServer first = RmiServer.start(server, newService());
        assertDoesNotThrow(service::ping);
        first.close();
        RmiServer second = RmiServer.start(server, newService());
        try {
            // The old connection is dead; the wrapper notices, looks the server up again and carries on.
            assertDoesNotThrow(service::ping);
        } finally {
            second.close();
        }
    }

    @Test
    void whenTheServerStaysDownTheUserGetsAReadableMessageAfterTheRetries() throws IOException {
        Identity identity = TlsTestFiles.createIdentity(dir, "server", "localhost");
        int registryPort = TlsTestFiles.freePort();
        int objectPort = TlsTestFiles.freePort();
        ServerSettings server = new ServerSettings(registryPort, objectPort, identity.keyStore(),
                TlsTestFiles.PASSWORD, "localhost");
        ClientSettings client = new ClientSettings("localhost", registryPort, identity.trustStore(),
                TlsTestFiles.PASSWORD);
        HRMService service = ResilientService.create(() -> ServerConnection.connect(client), PAUSES, Thread::sleep);
        RmiServer running = RmiServer.start(server, newService());
        assertDoesNotThrow(service::ping);
        running.close();

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class, service::ping);

        assertTrue(error.getMessage().startsWith("Cannot reach the HR server at localhost:" + registryPort),
                error.getMessage());
    }
}
