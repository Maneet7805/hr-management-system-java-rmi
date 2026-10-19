package com.bhel.hrm.net;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Builds the TLS settings for the server (its keystore) and the client (its truststore).
 * Only TLS 1.3 and 1.2 are allowed; older versions have known weaknesses.
 */
public final class TlsContexts {

    public static final String[] PROTOCOLS = {"TLSv1.3", "TLSv1.2"};

    private TlsContexts() {
    }

    /** The server proves who it is with the private key in its keystore. */
    public static SSLContext forServer(Path keyStorePath, String password) {
        KeyStore keyStore = load(keyStorePath, password, "keystore");
        try {
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, password.toCharArray());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot use keystore '" + keyStorePath + "': " + e.getMessage(), e);
        }
    }

    /** The client trusts only the certificates in its truststore. */
    public static SSLContext forClient(Path trustStorePath, String password) {
        KeyStore trustStore = load(trustStorePath, password, "truststore");
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot use truststore '" + trustStorePath + "': " + e.getMessage(), e);
        }
    }

    /**
     * The JDK's message for a wrong password never contains the password itself.
     * A file that does not exist is reported as IllegalArgumentException, not IOException.
     */
    private static KeyStore load(Path path, String password, String kind) {
        try {
            return KeyStore.getInstance(path.toFile(), password.toCharArray());
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot read " + kind + " '" + path + "': " + e.getMessage(), e);
        }
    }
}
