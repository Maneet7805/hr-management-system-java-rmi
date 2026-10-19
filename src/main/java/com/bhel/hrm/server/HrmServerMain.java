package com.bhel.hrm.server;

import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;

/**
 * Starts the HR server. Usage: {@code HrmServerMain [path-to-server.properties]}.
 * The JVM stays alive on its own because the exported RMI service keeps a non-daemon thread running.
 */
public final class HrmServerMain {

    private HrmServerMain() {
    }

    public static void main(String[] args) {
        Path configFile = Path.of(args.length > 0 ? args[0] : "config/server.properties");
        try {
            ServerSettings settings = ServerSettings.load(configFile, System.getenv());
            RmiServer server = RmiServer.start(settings, new HRMServiceImpl());
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            System.out.println("HR server running over TLS: registry port " + settings.registryPort()
                    + ", service port " + settings.objectPort());
        } catch (RemoteException e) {
            // Listed first: RemoteException is a kind of IOException, so it must be caught before it.
            System.out.println("Cannot start the server: a port may already be in use (" + e.getMessage() + ")");
            System.exit(1);
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            System.out.println("Cannot start the server: " + e.getMessage());
            System.exit(1);
        }
    }
}
