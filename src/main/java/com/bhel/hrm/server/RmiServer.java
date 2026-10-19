package com.bhel.hrm.server;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.net.RmiNames;
import com.bhel.hrm.net.TlsClientSocketFactory;
import com.bhel.hrm.net.TlsContexts;
import com.bhel.hrm.server.security.RequestFilters;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import javax.net.ssl.SSLContext;
import javax.rmi.ssl.SslRMIServerSocketFactory;

/**
 * A running RMI server: a registry and the service, both reachable only over TLS.
 * Call {@link #close()} to stop it.
 */
public final class RmiServer implements AutoCloseable {

    private final Registry registry;
    private final HRMService service;

    private RmiServer(Registry registry, HRMService service) {
        this.registry = registry;
        this.service = service;
    }

    public static RmiServer start(ServerSettings settings, HRMService service) throws RemoteException {
        if (settings.hostname() != null) {
            // Stubs carry this name to clients, and clients check it against the certificate.
            System.setProperty("java.rmi.server.hostname", settings.hostname());
        }
        SSLContext tls = TlsContexts.forServer(settings.keyStore(), settings.keyStorePassword());
        RMIClientSocketFactory clientFactory = new TlsClientSocketFactory();
        RMIServerSocketFactory serverFactory = new SslRMIServerSocketFactory(tls, null, TlsContexts.PROTOCOLS, false);

        Registry registry = LocateRegistry.createRegistry(settings.registryPort(), clientFactory, serverFactory);
        HRMService stub = (HRMService) UnicastRemoteObject.exportObject(service, settings.objectPort(),
                clientFactory, serverFactory, RequestFilters.contractFilter());
        registry.rebind(RmiNames.SERVICE_NAME, stub);
        return new RmiServer(registry, service);
    }

    @Override
    public void close() {
        unexport(service);
        unexport(registry);
    }

    private static void unexport(java.rmi.Remote object) {
        try {
            UnicastRemoteObject.unexportObject(object, true);
        } catch (NoSuchObjectException e) {
            // Already stopped, which is the state we want, so there is nothing to report.
        }
    }
}
