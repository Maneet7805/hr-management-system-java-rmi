package com.bhel.hrm.server.security;

import com.bhel.hrm.common.model.Role;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers who is logged in. A session is a random token mapped to the person's id, role and name,
 * kept only in this server's memory, so a restart logs everyone out.
 *
 * <p>Many client calls arrive at the same time on different threads, so the map is a
 * {@link ConcurrentHashMap} and every change to one session is a single atomic step.
 */
public final class SessionManager {

    /** A session ends this long after it was last used (decision #32). */
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    private static final int TOKEN_BYTES = 16;

    /** What the server knows about a logged-in person. Not sent to clients; they get the contract's Session. */
    public record ActiveSession(long employeeId, Role role, String displayName, boolean mustChangePassword) {
    }

    private record Entry(ActiveSession session, Instant lastUsed) {
    }

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public SessionManager(Clock clock) {
        this.clock = clock;
    }

    /** Starts a session and returns its token. */
    public String create(ActiveSession session) {
        removeExpired();
        Entry entry = new Entry(session, clock.instant());
        while (true) {
            String token = newToken();
            if (entries.putIfAbsent(token, entry) == null) {
                return token;
            }
        }
    }

    /** The session for this token, if it exists and has not expired. Finding it counts as use. */
    public Optional<ActiveSession> find(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Entry entry = entries.computeIfPresent(token, (key, current) -> isExpired(current, now)
                ? null
                : new Entry(current.session(), now));
        return Optional.ofNullable(entry).map(Entry::session);
    }

    public void remove(String token) {
        if (token != null) {
            entries.remove(token);
        }
    }

    /** Ends every session of this person except {@code keepToken}, for example after a password change. */
    public void removeOthers(long employeeId, String keepToken) {
        entries.entrySet().removeIf(
                e -> e.getValue().session().employeeId() == employeeId && !e.getKey().equals(keepToken));
    }

    public void markPasswordChanged(String token) {
        entries.computeIfPresent(token, (key, current) -> new Entry(
                new ActiveSession(current.session().employeeId(), current.session().role(),
                        current.session().displayName(), false),
                current.lastUsed()));
    }

    public int activeCount() {
        return entries.size();
    }

    private void removeExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> isExpired(entry, now));
    }

    private static boolean isExpired(Entry entry, Instant now) {
        return !now.isBefore(entry.lastUsed().plus(IDLE_TIMEOUT));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
