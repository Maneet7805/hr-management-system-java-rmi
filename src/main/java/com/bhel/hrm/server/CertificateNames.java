package com.bhel.hrm.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * The host names and IP addresses the server certificate is valid for. A client refuses the server when the
 * address it connects to is not one of them, so the server checks its own {@code rmi.hostname} against this list
 * at start-up instead of letting the problem show up only as a refused connection on another computer.
 */
public final class CertificateNames {

    /** Entry types inside a certificate's Subject Alternative Names, as numbered by the X.509 standard. */
    private static final int DNS_NAME = 2;
    private static final int IP_ADDRESS = 7;

    private CertificateNames() {
    }

    /** The names in the first certificate of the keystore. */
    public static List<String> read(Path keyStore, String password) {
        try {
            KeyStore store = KeyStore.getInstance(keyStore.toFile(), password.toCharArray());
            for (String alias : Collections.list(store.aliases())) {
                if (store.getCertificate(alias) instanceof X509Certificate certificate) {
                    return namesOf(certificate);
                }
            }
            return List.of();
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot read keystore '" + keyStore + "': " + e.getMessage(), e);
        }
    }

    /**
     * The DNS and IP entries of the certificate. A certificate without DNS entries is matched on its CN instead,
     * which is how the key scripts made certificates before they listed addresses.
     */
    static List<String> namesOf(X509Certificate certificate) throws CertificateParsingException {
        List<String> names = new ArrayList<>();
        boolean hasDnsName = false;
        Collection<List<?>> entries = certificate.getSubjectAlternativeNames();
        if (entries != null) {
            for (List<?> entry : entries) {
                int type = (Integer) entry.get(0);
                if (type == DNS_NAME || type == IP_ADDRESS) {
                    names.add((String) entry.get(1));
                    hasDnsName |= type == DNS_NAME;
                }
            }
        }
        if (!hasDnsName) {
            String commonName = commonName(certificate);
            if (commonName != null) {
                names.add(commonName);
            }
        }
        return names;
    }

    /**
     * Whether a client that connects to {@code host} accepts the certificate. Only exact matches count, ignoring
     * upper and lower case: the key scripts never make wildcard names such as {@code *.example.com}. IP addresses
     * are compared in one standard form, because Java writes {@code ::1} as {@code 0:0:0:0:0:0:0:1}.
     */
    public static boolean covers(List<String> names, String host) {
        String wanted = standardForm(host);
        return names.stream().anyMatch(name -> standardForm(name).equals(wanted));
    }

    /**
     * The address written as an IP address, or {@code null} for a host name. Only text made of IP address characters
     * is parsed, so this never looks a name up on the network.
     */
    static InetAddress ipAddress(String host) {
        boolean ipv4 = host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                && Arrays.stream(host.split("\\.")).allMatch(part -> Integer.parseInt(part) <= 255);
        boolean ipv6 = host.contains(":") && host.matches("[0-9A-Fa-f:.]+");
        if (!ipv4 && !ipv6) {
            return null;   // a name, or something like 999.1.1.1 that Java would otherwise look up as a name
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            return null;   // text with colons that is still not a valid IPv6 address
        }
    }

    private static String standardForm(String host) {
        InetAddress ip = ipAddress(host);
        return ip != null ? ip.getHostAddress() : host.toLowerCase(Locale.ROOT);
    }

    private static String commonName(X509Certificate certificate) {
        try {
            for (Rdn part : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
                if (part.getType().equalsIgnoreCase("CN")) {
                    return part.getValue().toString();
                }
            }
        } catch (InvalidNameException e) {
            // A subject Java itself produced is always readable, so this cannot happen; fall through.
        }
        return null;
    }
}
