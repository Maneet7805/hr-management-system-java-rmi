package com.bhel.hrm.client;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.net.ClientTls;
import com.bhel.hrm.net.RmiNames;
import com.bhel.hrm.net.TlsClientSocketFactory;
import com.bhel.hrm.net.TlsContexts;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

/**
 * Finds the server and returns the remote service. Every failure becomes a
 * {@link ServiceUnavailableException} with a message a user can act on.
 */
public final class ServerConnection {

    private ServerConnection() {
    }

    public static HRMService connect(ClientSettings settings) throws ServiceUnavailableException {
        try {
            ClientTls.install(TlsContexts.forClient(settings.trustStore(), settings.trustStorePassword()));
        } catch (IllegalStateException e) {
            throw new ServiceUnavailableException("The secure-connection files could not be read: " + e.getMessage());
        }
        try {
            Registry registry = LocateRegistry.getRegistry(settings.host(), settings.port(),
                    new TlsClientSocketFactory());
            return (HRMService) registry.lookup(RmiNames.SERVICE_NAME);
        } catch (NotBoundException e) {
            throw new ServiceUnavailableException("The server is running but the HR service is not available.");
        } catch (RemoteException e) {
            throw new ServiceUnavailableException(explain(e, settings));
        }
    }

    /** Turns a network failure into a sentence for the user. Use it for later calls too, not just connect. */
    public static String explain(RemoteException failure, ClientSettings settings) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SSLHandshakeException || cause instanceof CertificateException) {
                return "Secure connection failed: the server's certificate is not trusted "
                        + "or does not match the server name.";
            }
            if (cause instanceof SSLException) {
                return "Secure connection failed. The server may not be using TLS.";
            }
            if (cause instanceof UnknownHostException) {
                return "Cannot find the HR server '" + settings.host() + "'. Check the server name.";
            }
            if (cause instanceof ConnectException) {
                return "Cannot reach the HR server at " + settings.host() + ":" + settings.port()
                        + ". Check that it is running and try again.";
            }
        }
        return "The connection to the HR server failed. Try again later.";
    }
}
