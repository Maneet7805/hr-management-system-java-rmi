package com.bhel.hrm.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.net.TlsTestFiles;
import com.bhel.hrm.net.TlsTestFiles.Identity;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CertificateNamesTest {

    @Test
    void readsEveryHostNameAndIpAddress(@TempDir Path dir) throws IOException {
        List<String> names = List.of("localhost", "127.0.0.1", "hr-server.local", "192.0.2.10");
        Identity identity = TlsTestFiles.createIdentity(dir, "server", names);

        assertEquals(names, CertificateNames.read(identity.keyStore(), TlsTestFiles.PASSWORD));
    }

    @Test
    void anOldCertificateWithoutAlternativeNamesIsMatchedOnItsCommonName(@TempDir Path dir) throws IOException {
        Identity identity = TlsTestFiles.createOldStyleIdentity(dir, "old", "localhost");

        assertEquals(List.of("localhost"), CertificateNames.read(identity.keyStore(), TlsTestFiles.PASSWORD));
    }

    @Test
    void wrongPasswordIsReportedWithoutRevealingIt(@TempDir Path dir) throws IOException {
        Identity identity = TlsTestFiles.createIdentity(dir, "server", "localhost");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> CertificateNames.read(identity.keyStore(), "not-the-password"));

        assertFalse(error.getMessage().contains("not-the-password"));
        assertTrue(error.getMessage().contains("keystore"));
    }

    @Test
    void coversIgnoresCaseButNeedsTheExactName() {
        List<String> names = List.of("HR-Server", "127.0.0.1");

        assertTrue(CertificateNames.covers(names, "hr-server"));
        assertTrue(CertificateNames.covers(names, "127.0.0.1"));
        assertFalse(CertificateNames.covers(names, "hr-server.local"));
        assertFalse(CertificateNames.covers(names, "192.0.2.10"));
    }

    /** Java reads an IPv6 entry back as 0:0:0:0:0:0:0:1, so ::1 in the settings must still match it. */
    @Test
    void coversMatchesAnIpv6AddressWrittenEitherWay(@TempDir Path dir) throws IOException {
        Identity identity = TlsTestFiles.createIdentity(dir, "v6", List.of("localhost", "::1"));
        List<String> names = CertificateNames.read(identity.keyStore(), TlsTestFiles.PASSWORD);

        assertTrue(CertificateNames.covers(names, "::1"), names.toString());
        assertTrue(CertificateNames.covers(names, "0:0:0:0:0:0:0:1"), names.toString());
    }

    @Test
    void onlyTextThatIsAnIpAddressIsTreatedAsOne() {
        assertNotNull(CertificateNames.ipAddress("192.168.1.20"));
        assertNotNull(CertificateNames.ipAddress("::1"));
        assertNull(CertificateNames.ipAddress("127.example.com"));
        assertNull(CertificateNames.ipAddress("hr-server"));
        assertNull(CertificateNames.ipAddress("999.1.1.1"));
    }
}
