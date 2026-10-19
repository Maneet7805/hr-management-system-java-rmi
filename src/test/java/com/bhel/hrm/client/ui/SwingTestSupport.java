package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.fail;

import com.bhel.hrm.common.model.Session;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

/** Helpers for testing screens without a display: do things on the event thread, and wait for server answers. */
final class SwingTestSupport {

    private SwingTestSupport() {
    }

    static <T> T onEdt(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** Waits (up to 10 seconds) until the condition, checked on the event thread, is true. */
    static void awaitTrue(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!onEdt(condition)) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for: " + what);
            }
            Thread.sleep(20);
        }
    }

    /** Remembers where the screens asked to go. */
    static final class RecordingNavigator implements Navigator {
        volatile String loginMessage = "<never shown>";
        volatile int loginShown;
        volatile Session changePasswordFor;
        volatile Session homeFor;

        @Override
        public void showLogin(String message) {
            loginShown++;
            loginMessage = message;
        }

        @Override
        public void showChangePassword(Session session) {
            changePasswordFor = session;
        }

        @Override
        public void showHome(Session session) {
            homeFor = session;
        }
    }
}
