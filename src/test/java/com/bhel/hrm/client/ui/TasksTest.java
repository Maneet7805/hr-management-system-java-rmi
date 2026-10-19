package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.ValidationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** CLAUDE.md rule 4: the server is never called on the Swing event thread, and the screen is only touched on it. */
@Timeout(20)
class TasksTest {

    @Test
    void theWorkRunsOffTheEventThreadAndTheSuccessCallbackRunsOnIt() throws Exception {
        AtomicBoolean workOnEventThread = new AtomicBoolean(true);
        AtomicBoolean callbackOnEventThread = new AtomicBoolean(false);
        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(() -> Tasks.<String>run(
                () -> {
                    workOnEventThread.set(SwingUtilities.isEventDispatchThread());
                    return "answer";
                },
                result -> {
                    callbackOnEventThread.set(SwingUtilities.isEventDispatchThread());
                    received.set(result);
                    done.countDown();
                },
                failure -> done.countDown()));

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertFalse(workOnEventThread.get(), "the server call must not run on the event thread");
        assertTrue(callbackOnEventThread.get(), "screens may only be touched on the event thread");
        assertEquals("answer", received.get());
    }

    @Test
    void anExceptionFromTheWorkReachesTheFailureCallbackUnwrappedOnTheEventThread() throws Exception {
        ValidationException problem = new ValidationException("Not enough Annual leave left");
        AtomicReference<Throwable> received = new AtomicReference<>();
        AtomicBoolean callbackOnEventThread = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(() -> Tasks.<String>run(
                () -> {
                    throw problem;
                },
                result -> done.countDown(),
                failure -> {
                    callbackOnEventThread.set(SwingUtilities.isEventDispatchThread());
                    received.set(failure);
                    done.countDown();
                }));

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertSame(problem, received.get(), "the original exception, not a wrapper");
        assertTrue(callbackOnEventThread.get());
    }

    @Test
    void aProgramErrorInTheWorkAlsoReachesTheFailureCallback() throws Exception {
        AtomicReference<Throwable> received = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        SwingUtilities.invokeAndWait(() -> Tasks.<String>run(
                () -> {
                    throw new IllegalStateException("bug");
                },
                result -> done.countDown(),
                failure -> {
                    received.set(failure);
                    done.countDown();
                }));

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(received.get() instanceof IllegalStateException);
    }
}
