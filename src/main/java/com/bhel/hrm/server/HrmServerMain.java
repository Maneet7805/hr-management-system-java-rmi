package com.bhel.hrm.server;

import com.bhel.hrm.net.SettingsSupport;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.db.DbSettings;
import com.bhel.hrm.server.db.JdbcDatabase;
import com.bhel.hrm.server.security.PasswordHasher;
import com.bhel.hrm.server.security.Pbkdf2PasswordHasher;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.service.AuthService;
import com.bhel.hrm.server.service.EmployeeService;
import com.bhel.hrm.server.service.LeaveService;
import com.bhel.hrm.server.service.ReportService;
import java.io.IOException;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.time.Clock;

/**
 * Starts the HR server. Usage: {@code HrmServerMain [path-to-server.properties]}.
 * Needs HRM_KEYSTORE_PASSWORD and HRM_DB_PASSWORD in the environment.
 * The JVM stays alive on its own because the exported RMI service keeps a non-daemon thread running.
 */
public final class HrmServerMain {

    private HrmServerMain() {
    }

    public static void main(String[] args) {
        Path configFile = Path.of(args.length > 0 ? args[0] : "config/server.properties");
        try {
            ServerSettings settings = ServerSettings.load(configFile, System.getenv());
            DbSettings dbSettings = DbSettings.load(configFile, System.getenv());

            // The one place where the pieces are put together.
            Database database = new JdbcDatabase(dbSettings);
            PasswordHasher hasher = new Pbkdf2PasswordHasher();
            AuthService auth = new AuthService(database, hasher, new SessionManager(Clock.systemUTC()));

            EmployeeService employees = new EmployeeService(database, hasher, auth);
            LeaveService leave = new LeaveService(database, auth, Clock.systemDefaultZone());
            ReportService reports = new ReportService(database, auth);

            RmiServer server = RmiServer.start(settings,
                    new HRMServiceImpl(database, auth, employees, leave, reports));
            Runtime.getRuntime().addShutdownHook(new Thread(server::close));
            System.out.println("HR server running over TLS: registry port " + settings.registryPort()
                    + ", service port " + settings.objectPort());
        } catch (RemoteException e) {
            // Listed first: RemoteException is a kind of IOException, so it must be caught before it.
            System.out.println("Cannot start the server: a port may already be in use (" + e.getMessage() + ")");
            System.exit(1);
        } catch (IOException e) {
            System.out.println("Cannot start the server. " + SettingsSupport.describeUnreadable(configFile, e));
            System.exit(1);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Cannot start the server: " + e.getMessage());
            System.exit(1);
        }
    }
}
