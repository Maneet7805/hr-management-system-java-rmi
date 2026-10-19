package com.bhel.hrm.client;

import com.bhel.hrm.common.HRMService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stand-in {@link HRMService} for tests: each call takes the next prepared outcome from a list.
 * A {@link Throwable} outcome is thrown; anything else is returned (ignored by methods that return nothing).
 * When the list is empty, calls succeed and return null.
 */
public final class ScriptedService {

    /** A LinkedList, because an outcome of null ("returns nothing") is allowed and ArrayDeque refuses null. */
    private final Deque<Object> outcomes = new LinkedList<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger callsOnEventThread = new AtomicInteger();
    private final List<String> methodsCalled = new ArrayList<>();
    private final List<List<Object>> argumentsOfCalls = new ArrayList<>();
    private final HRMService proxy;

    public ScriptedService(Object... prepared) {
        for (Object outcome : prepared) {
            outcomes.add(outcome);
        }
        InvocationHandler handler = (ignored, method, args) -> {
            synchronized (this) {
                calls.incrementAndGet();
                if (javax.swing.SwingUtilities.isEventDispatchThread()) {
                    callsOnEventThread.incrementAndGet();
                }
                methodsCalled.add(method.getName());
                argumentsOfCalls.add(args == null ? List.of() : java.util.Arrays.asList(args));
                Object next = outcomes.pollFirst();
                if (next instanceof Throwable failure) {
                    throw failure;
                }
                return next;
            }
        };
        this.proxy = (HRMService) Proxy.newProxyInstance(HRMService.class.getClassLoader(),
                new Class<?>[] {HRMService.class}, handler);
    }

    public HRMService service() {
        return proxy;
    }

    public int calls() {
        return calls.get();
    }

    /** How many calls were made on the Swing event thread. For screens this must always be zero. */
    public int callsOnEventThread() {
        return callsOnEventThread.get();
    }

    public synchronized List<String> methodsCalled() {
        return List.copyOf(methodsCalled);
    }

    /** The arguments of the n-th call (0 is the first). */
    public synchronized List<Object> argumentsOfCall(int index) {
        return argumentsOfCalls.get(index);
    }
}
