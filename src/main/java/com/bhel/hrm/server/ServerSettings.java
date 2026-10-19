package com.bhel.hrm.server;

import com.bhel.hrm.net.SettingsSupport;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * What the server needs to start. Ports and file names come from {@code config/server.properties};
 * the keystore password comes only from the environment.
 *
 * @param objectPort fixed port for the exported service, so a firewall needs just two known ports
 * @param hostname   optional; the name clients should use to reach this machine (sets java.rmi.server.hostname)
 */
public record ServerSettings(int registryPort, int objectPort, Path keyStore, String keyStorePassword,
        String hostname) {

    private static final int DEFAULT_REGISTRY_PORT = 1099;
    private static final int DEFAULT_OBJECT_PORT = 1100;
    private static final String DEFAULT_KEYSTORE = "config/server.keystore";

    public static ServerSettings from(Properties props, Map<String, String> env) {
        return new ServerSettings(
                SettingsSupport.port(props, "rmi.port", DEFAULT_REGISTRY_PORT),
                SettingsSupport.port(props, "rmi.objectPort", DEFAULT_OBJECT_PORT),
                Path.of(SettingsSupport.text(props, "tls.keystore", DEFAULT_KEYSTORE)),
                SettingsSupport.requireEnv(env, "HRM_KEYSTORE_PASSWORD"),
                SettingsSupport.optionalText(props, "rmi.hostname"));
    }

    public static ServerSettings load(Path file, Map<String, String> env) throws IOException {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            props.load(reader);
        }
        return from(props, env);
    }

    /** Leaves the password out so it cannot end up in a log. */
    @Override
    public String toString() {
        return "ServerSettings[registryPort=" + registryPort + ", objectPort=" + objectPort
                + ", keyStore=" + keyStore + ", hostname=" + hostname + "]";
    }
}
