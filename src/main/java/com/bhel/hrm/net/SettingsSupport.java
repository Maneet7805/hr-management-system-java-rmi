package com.bhel.hrm.net;

import java.util.Map;
import java.util.Properties;

/**
 * Reads values from a properties file and the environment for the server and client settings.
 * Every error names the setting so the person editing the file knows what to fix.
 */
public final class SettingsSupport {

    private SettingsSupport() {
    }

    /** A TCP port; the default is used when the key is absent. */
    public static int port(Properties props, String key, int defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            int port = Integer.parseInt(raw.trim());
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException(key + " must be between 1 and 65535, but was " + port);
            }
            return port;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number, but was '" + raw + "'");
        }
    }

    /** Text with a default when the key is absent; a key that is present must not be blank. */
    public static String text(Properties props, String key, String defaultValue) {
        String raw = props.getProperty(key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.isBlank()) {
            throw new IllegalArgumentException(key + " must not be blank");
        }
        return raw.trim();
    }

    /** Text that may be left out; returns {@code null} when absent or blank. */
    public static String optionalText(Properties props, String key) {
        String raw = props.getProperty(key);
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    /** A secret that must come from the environment, never from a file. */
    public static String requireEnv(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Set the environment variable " + name + " before starting");
        }
        return value;
    }
}
