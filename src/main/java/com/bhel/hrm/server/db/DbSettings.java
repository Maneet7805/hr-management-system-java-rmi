package com.bhel.hrm.server.db;

import com.bhel.hrm.net.SettingsSupport;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * How the server reaches MySQL. The URL and user come from {@code config/server.properties};
 * the password comes only from the environment variable {@code HRM_DB_PASSWORD}.
 */
public record DbSettings(String url, String user, String password) {

    /**
     * Local development database. sslMode=REQUIRED encrypts the connection but does not check the
     * server's certificate, which is acceptable only for a database on the same machine.
     */
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/hrm?sslMode=REQUIRED"
            + "&enabledTLSProtocols=TLSv1.3,TLSv1.2&allowPublicKeyRetrieval=false"
            + "&allowLoadLocalInfile=false&connectTimeout=5000";
    private static final String DEFAULT_USER = "hrm_app";

    public static DbSettings from(Properties props, Map<String, String> env) {
        return new DbSettings(
                SettingsSupport.text(props, "db.url", DEFAULT_URL),
                SettingsSupport.text(props, "db.user", DEFAULT_USER),
                SettingsSupport.requireEnv(env, "HRM_DB_PASSWORD"));
    }

    public static DbSettings load(Path file, Map<String, String> env) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            props.load(reader);
        }
        return from(props, env);
    }

    /** Leaves the password out so it cannot end up in a log. */
    @Override
    public String toString() {
        return "DbSettings[url=" + url + ", user=" + user + "]";
    }
}
