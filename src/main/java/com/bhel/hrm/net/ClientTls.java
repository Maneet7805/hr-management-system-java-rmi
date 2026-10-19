package com.bhel.hrm.net;

import javax.net.ssl.SSLContext;

/**
 * Holds the TLS settings this client process uses. RMI creates the client's sockets itself,
 * so {@link TlsClientSocketFactory} reads the settings from here each time it opens one.
 * A client program has one server to trust, so one shared value is enough.
 */
public final class ClientTls {

    private static volatile SSLContext context;

    private ClientTls() {
    }

    public static void install(SSLContext newContext) {
        context = newContext;
    }

    static SSLContext current() {
        SSLContext current = context;
        if (current == null) {
            throw new IllegalStateException("TLS is not set up: call ClientTls.install first");
        }
        return current;
    }
}
