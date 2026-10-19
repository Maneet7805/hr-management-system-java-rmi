package com.bhel.hrm.net;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TlsContextsTest {

    @Test
    void buildsAServerAndAClientContextFromValidStores(@TempDir Path dir) throws IOException {
        TlsTestFiles.Identity identity = TlsTestFiles.createIdentity(dir, "server", "localhost");

        assertDoesNotThrow(() -> TlsContexts.forServer(identity.keyStore(), TlsTestFiles.PASSWORD));
        assertDoesNotThrow(() -> TlsContexts.forClient(identity.trustStore(), TlsTestFiles.PASSWORD));
    }

    @Test
    void wrongPasswordIsReportedWithoutRevealingIt(@TempDir Path dir) throws IOException {
        TlsTestFiles.Identity identity = TlsTestFiles.createIdentity(dir, "server", "localhost");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TlsContexts.forServer(identity.keyStore(), "not-the-password"));

        assertFalse(error.getMessage().contains("not-the-password"));
        assertTrue(error.getMessage().contains("keystore"));
    }

    @Test
    void missingFileNamesTheFile(@TempDir Path dir) {
        Path missing = dir.resolve("nothing-here.truststore");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TlsContexts.forClient(missing, "anything"));

        assertTrue(error.getMessage().contains("nothing-here.truststore"));
    }
}
