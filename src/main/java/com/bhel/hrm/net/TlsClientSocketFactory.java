package com.bhel.hrm.net;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

/**
 * Opens the client's RMI connections over TLS. The server sends one of these inside the stub,
 * so it must be serializable and hold no state of its own.
 *
 * <p>Unlike the JDK's {@code SslRMIClientSocketFactory}, this checks that the server's certificate
 * was issued for the host name the client connected to. Without that check, anyone holding any
 * certificate the client trusts could pretend to be the server.
 */
public class TlsClientSocketFactory implements RMIClientSocketFactory, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        SSLSocket socket = (SSLSocket) ClientTls.current().getSocketFactory().createSocket(host, port);
        try {
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setProtocols(TlsContexts.PROTOCOLS);
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            // Handshake now so a bad certificate fails here, with a clear cause, not on the first call.
            socket.startHandshake();
            return socket;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    /** RMI compares factories to decide whether two connections can be shared. */
    @Override
    public boolean equals(Object other) {
        return other != null && other.getClass() == getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
