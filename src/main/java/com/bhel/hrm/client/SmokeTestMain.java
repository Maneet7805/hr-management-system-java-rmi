package com.bhel.hrm.client;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.HrmException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.rmi.RemoteException;

/**
 * Command-line check that a client can reach the server over TLS. Run it after starting the
 * server; it is also a quick demo for the test manual. Exit code 0 means success.
 */
public final class SmokeTestMain {

    private SmokeTestMain() {
    }

    public static void main(String[] args) {
        Path configFile = Path.of(args.length > 0 ? args[0] : "config/client.properties");
        try {
            ClientSettings settings = ClientSettings.load(configFile, System.getenv());
            System.exit(run(settings, System.out));
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("Cannot start: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Connects, pings, prints one line, and returns the exit code. */
    public static int run(ClientSettings settings, PrintStream out) {
        try {
            HRMService service = ServerConnection.connect(settings);
            service.ping();
            out.println("Server reachable over TLS at " + settings.host() + ":" + settings.port());
            return 0;
        } catch (RemoteException e) {
            out.println(ServerConnection.explain(e, settings));
            return 1;
        } catch (HrmException e) {
            out.println(e.getMessage());
            return 1;
        }
    }
}
