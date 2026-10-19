package com.bhel.hrm.client;

import com.bhel.hrm.net.SettingsSupport;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * What a client needs to reach the server. Host, port and file name come from
 * {@code config/client.properties}; the truststore password comes only from the environment.
 *
 * @param host the server's name. It must match the name in the server's certificate.
 */
public record ClientSettings(String host, int port, Path trustStore, String trustStorePassword) {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1099;
    private static final String DEFAULT_TRUSTSTORE = "config/client.truststore";

    public static ClientSettings from(Properties props, Map<String, String> env) {
        return new ClientSettings(
                SettingsSupport.text(props, "server.host", DEFAULT_HOST),
                SettingsSupport.port(props, "rmi.port", DEFAULT_PORT),
                Path.of(SettingsSupport.text(props, "tls.truststore", DEFAULT_TRUSTSTORE)),
                SettingsSupport.requireEnv(env, "HRM_TRUSTSTORE_PASSWORD"));
    }

    public static ClientSettings load(Path file, Map<String, String> env) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            props.load(reader);
        }
        return from(props, env);
    }

    /** Leaves the password out so it cannot end up in a log. */
    @Override
    public String toString() {
        return "ClientSettings[host=" + host + ", port=" + port + ", trustStore=" + trustStore + "]";
    }
}
