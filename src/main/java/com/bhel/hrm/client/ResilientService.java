package com.bhel.hrm.client;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * Wraps the remote service so a short server outage does not reach the user (the fault tolerance asked for
 * in the brief). Screens use this instead of the raw RMI stub.
 *
 * <ul>
 *   <li><b>Reads</b> are tried up to three times, with a growing pause, looking the server up again each time
 *       (it may have restarted).</li>
 *   <li><b>Writes</b> are never repeated once the call was on its way: if the reply is lost, a second try could
 *       record the same leave application twice (decision #34). A write is only repeated when the connection
 *       could not be made at all, because then nothing was sent.</li>
 *   <li><b>Business errors</b> (wrong password, not enough days, ...) are returned at once. Retrying cannot help.</li>
 * </ul>
 */
public final class ResilientService implements InvocationHandler {

    /** Finds the server. Called on first use and again after a failure. */
    @FunctionalInterface
    public interface Connector {
        HRMService connect() throws ServiceUnavailableException;
    }

    /** Waits between attempts. A parameter so tests do not have to wait. */
    @FunctionalInterface
    public interface Pause {
        void sleep(long millis) throws InterruptedException;
    }

    private static final long[] DEFAULT_PAUSES_MILLIS = {250, 500};

    /** Calls that only look at data (login only starts a session), so doing one twice is harmless. */
    private static final Set<String> READS = Set.of(
            "ping", "login", "getMyProfile", "getMyFamily", "listEmployees", "listPendingLeave",
            "getLeaveTypes", "getMyLeaveBalances", "getMyLeaveApplications", "generateYearlyReport");

    /** Calls that change something. A test checks that every method of the service is in one list or the other. */
    private static final Set<String> WRITES = Set.of(
            "logout", "changePassword", "registerEmployee", "decideLeave", "updateMyContact",
            "addFamilyMember", "updateFamilyMember", "removeFamilyMember", "applyForLeave");

    private final Connector connector;
    private final long[] pausesMillis;
    private final Pause pause;
    private HRMService current;

    private ResilientService(Connector connector, long[] pausesMillis, Pause pause) {
        this.connector = connector;
        this.pausesMillis = pausesMillis.clone();
        this.pause = pause;
    }

    public static HRMService create(Connector connector) {
        return create(connector, DEFAULT_PAUSES_MILLIS, Thread::sleep);
    }

    /** One more attempt than there are pauses. */
    public static HRMService create(Connector connector, long[] pausesMillis, Pause pause) {
        return (HRMService) Proxy.newProxyInstance(HRMService.class.getClassLoader(),
                new Class<?>[] {HRMService.class}, new ResilientService(connector, pausesMillis, pause));
    }

    public static Set<String> readMethods() {
        return READS;
    }

    public static Set<String> writeMethods() {
        return WRITES;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        boolean isRead = READS.contains(method.getName());
        int attempts = pausesMillis.length + 1;
        for (int attempt = 1; ; attempt++) {
            HRMService service;
            try {
                service = connection();
            } catch (ServiceUnavailableException notConnected) {
                // Nothing was sent yet, so trying again is safe for reads and writes alike.
                if (attempt >= attempts || !waitBeforeNextAttempt(attempt)) {
                    throw notConnected;
                }
                continue;
            }
            try {
                return method.invoke(service, args);
            } catch (InvocationTargetException wrapped) {
                Throwable failure = wrapped.getCause();
                if (failure instanceof RemoteException) {
                    forget(service);   // the server may have restarted: look it up again next time
                    if (isRead && attempt < attempts && waitBeforeNextAttempt(attempt)) {
                        continue;
                    }
                }
                throw failure;
            }
        }
    }

    private synchronized HRMService connection() throws ServiceUnavailableException {
        if (current == null) {
            current = connector.connect();
        }
        return current;
    }

    /** Only drops the connection that failed, so a newer one made by another thread is kept. */
    private synchronized void forget(HRMService failed) {
        if (current == failed) {
            current = null;
        }
    }

    /** False if the thread was interrupted while waiting, in which case the caller should stop trying. */
    private boolean waitBeforeNextAttempt(int attempt) {
        try {
            pause.sleep(pausesMillis[attempt - 1]);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
