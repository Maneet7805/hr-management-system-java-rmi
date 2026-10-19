package com.bhel.hrm.client.ui;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * The only way a screen talks to the server (CLAUDE.md rule 4). The work runs on a background thread so the
 * window stays responsive; both callbacks run on the Swing event thread, where it is safe to change the screen.
 */
public final class Tasks {

    /** A server call. It may throw anything; the failure callback receives it. */
    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }

    private Tasks() {
    }

    /** Call from the event thread. Exactly one of the two callbacks is called, later, on the event thread. */
    public static <T> void run(Work<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.run();
            }

            @Override
            protected void done() {
                T result;
                try {
                    result = get();
                } catch (ExecutionException e) {
                    // The original exception, not the wrapper SwingWorker puts around it.
                    onFailure.accept(e.getCause());
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onFailure.accept(e);
                    return;
                }
                onSuccess.accept(result);
            }
        }.execute();
    }
}
