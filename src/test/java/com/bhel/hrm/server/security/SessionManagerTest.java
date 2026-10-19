package com.bhel.hrm.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.security.SessionManager.ActiveSession;
import com.bhel.hrm.server.testsupport.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class SessionManagerTest {

    private final MutableClock clock = new MutableClock();
    private final SessionManager sessions = new SessionManager(clock);

    private static ActiveSession employee(long id) {
        return new ActiveSession(id, Role.EMPLOYEE, "Alice Tan", false);
    }

    @Test
    void aCreatedSessionCanBeFoundByItsToken() {
        String token = sessions.create(employee(1L));

        assertEquals(Optional.of(employee(1L)), sessions.find(token));
    }

    @Test
    void unknownAndMissingTokensAreNotFound() {
        assertTrue(sessions.find("no-such-token").isEmpty());
        assertTrue(sessions.find(null).isEmpty());
        assertTrue(sessions.find("").isEmpty());
    }

    @Test
    void aSessionEndsAfter30MinutesWithoutUse() {
        String token = sessions.create(employee(1L));

        clock.advance(Duration.ofMinutes(29).plusSeconds(59));
        assertTrue(sessions.find(token).isPresent(), "still alive just before 30 minutes");

        clock.advance(Duration.ofMinutes(30));
        assertTrue(sessions.find(token).isEmpty(), "gone after 30 minutes without use");
    }

    @Test
    void usingASessionRestartsTheIdleTime() {
        String token = sessions.create(employee(1L));

        clock.advance(Duration.ofMinutes(20));
        assertTrue(sessions.find(token).isPresent());
        clock.advance(Duration.ofMinutes(20));

        assertTrue(sessions.find(token).isPresent(), "40 minutes in total, but only 20 since the last use");
    }

    @Test
    void anExpiredSessionStaysGoneEvenIfItIsAskedForAgain() {
        String token = sessions.create(employee(1L));
        clock.advance(Duration.ofMinutes(31));
        sessions.find(token);

        assertTrue(sessions.find(token).isEmpty());
    }

    @Test
    void removeEndsTheSession() {
        String token = sessions.create(employee(1L));

        sessions.remove(token);

        assertTrue(sessions.find(token).isEmpty());
    }

    @Test
    void removingAnUnknownTokenIsHarmless() {
        sessions.remove("no-such-token");
        sessions.remove(null);
    }

    @Test
    void removeOthersKeepsTheNamedSessionAndOtherPeoplesSessions() {
        String keep = sessions.create(employee(1L));
        String otherLogin = sessions.create(employee(1L));
        String someoneElse = sessions.create(employee(2L));

        sessions.removeOthers(1L, keep);

        assertTrue(sessions.find(keep).isPresent());
        assertTrue(sessions.find(otherLogin).isEmpty());
        assertTrue(sessions.find(someoneElse).isPresent());
    }

    @Test
    void markPasswordChangedClearsTheMustChangeFlag() {
        String token = sessions.create(new ActiveSession(1L, Role.EMPLOYEE, "Alice Tan", true));

        sessions.markPasswordChanged(token);

        assertFalse(sessions.find(token).orElseThrow().mustChangePassword());
    }

    @Test
    void tokensAreUniqueAndAtLeast22CharactersOfUrlSafeText() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = sessions.create(employee(i));
            assertTrue(token.length() >= 22, token);
            assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
            tokens.add(token);
        }

        assertEquals(1000, tokens.size());
    }

    @Test
    void expiredSessionsAreClearedOutWhenNewOnesAreCreated() {
        sessions.create(employee(1L));
        sessions.create(employee(2L));
        clock.advance(Duration.ofMinutes(45));

        sessions.create(employee(3L));

        assertEquals(1, sessions.activeCount());
    }

    @Test
    @Timeout(30)
    void manyThreadsCreatingSessionsAtOnceGetDistinctWorkingTokens() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                long id = i;
                Callable<String> create = () -> sessions.create(employee(id));
                futures.add(pool.submit(create));
            }
            Set<String> tokens = new HashSet<>();
            for (Future<String> future : futures) {
                tokens.add(future.get(10, TimeUnit.SECONDS));
            }

            assertEquals(50, tokens.size());
            for (String token : tokens) {
                assertTrue(sessions.find(token).isPresent());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
