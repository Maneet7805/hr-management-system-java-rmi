package com.bhel.hrm;

import com.bhel.hrm.net.TlsTestFiles;
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

/**
 * Test helper: a real RMI server over TLS on this machine, with a stand-in database so connection tests do not
 * need MySQL. Close it to stop the server.
 */
record TestServer(RmiServer server, int registryPort, String hostnameBefore) implements AutoCloseable {

    private static final String HOSTNAME_PROPERTY = "java.rmi.server.hostname";

    /** @param advertisedAddress the server's {@code rmi.hostname}: the address put into the stubs it hands out */
    static TestServer start(Path keyStore, String keyStorePassword, String advertisedAddress) throws IOException {
        String hostnameBefore = System.getProperty(HOSTNAME_PROPERTY);
        int registryPort = TlsTestFiles.freePort();
        ServerSettings settings = new ServerSettings(registryPort, TlsTestFiles.freePort(), keyStore,
                keyStorePassword, advertisedAddress);
        FakeDatabase database = new FakeDatabase();
        CountingHasher hasher = new CountingHasher();
        AuthService auth = new AuthService(database, hasher, new SessionManager(Clock.systemUTC()));
        HRMServiceImpl service = new HRMServiceImpl(database, auth, new EmployeeService(database, hasher, auth),
                new LeaveService(database, auth, Clock.systemDefaultZone()), new ReportService(database, auth));
        return new TestServer(RmiServer.start(settings, service), registryPort, hostnameBefore);
    }

    /** Also puts back the JVM-wide hostname property RmiServer set, so later tests start from the same state. */
    @Override
    public void close() {
        server.close();
        if (hostnameBefore == null) {
            System.clearProperty(HOSTNAME_PROPERTY);
        } else {
            System.setProperty(HOSTNAME_PROPERTY, hostnameBefore);
        }
    }
}
