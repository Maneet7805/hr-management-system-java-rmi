package com.bhel.hrm.server.security;

import java.io.ObjectInputFilter;

/**
 * Decides which classes the server will rebuild from bytes sent by a client.
 *
 * <p>Rebuilding arbitrary classes from the network is a known attack, so anything not on this
 * list is refused before it is created. {@code java.util.*} is wider than strictly needed, but it
 * keeps the list short enough to explain; its sub-packages (such as {@code java.util.concurrent})
 * are not included.
 */
public final class RequestFilters {

    /**
     * Patterns are checked left to right. The final {@code !*} refuses everything not listed above it,
     * because a class that matches no pattern would otherwise be accepted.
     */
    private static final String CONTRACT_PATTERN = String.join(";",
            "maxdepth=20",
            "maxarray=100000",
            "maxrefs=100000",
            "com.bhel.hrm.common.**",
            "java.lang.*",
            "java.util.*",
            "java.time.*",
            "!*");

    private RequestFilters() {
    }

    public static ObjectInputFilter contractFilter() {
        return ObjectInputFilter.Config.createFilter(CONTRACT_PATTERN);
    }
}
