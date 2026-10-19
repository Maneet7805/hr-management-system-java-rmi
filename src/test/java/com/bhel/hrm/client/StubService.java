package com.bhel.hrm.client;

import com.bhel.hrm.common.HRMService;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/**
 * A stand-in {@link HRMService} for tests where several calls can happen at once, so the answer is chosen
 * by method name, not by order. A method nobody prepared is recorded as unexpected and fails.
 */
public final class StubService {

    private final Map<String, Function<Object[], Object>> handlers = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final Map<String, List<Object[]>> argumentsByMethod = new HashMap<>();
    private final List<String> unexpected = new ArrayList<>();
    private int callsOnEventThread;
    private final HRMService proxy;

    public StubService() {
        this.proxy = (HRMService) Proxy.newProxyInstance(HRMService.class.getClassLoader(),
                new Class<?>[] {HRMService.class}, (ignored, method, args) -> {
                    Function<Object[], Object> handler;
                    Object[] arguments = args == null ? new Object[0] : args;
                    synchronized (this) {
                        calls.add(method.getName());
                        argumentsByMethod.computeIfAbsent(method.getName(), name -> new ArrayList<>()).add(arguments);
                        if (SwingUtilities.isEventDispatchThread()) {
                            callsOnEventThread++;
                        }
                        handler = handlers.get(method.getName());
                        if (handler == null) {
                            unexpected.add(method.getName());
                        }
                    }
                    if (handler == null) {
                        throw new UnsupportedOperationException("unexpected call: " + method.getName());
                    }
                    Object result = handler.apply(arguments);
                    if (result instanceof Throwable failure) {
                        throw failure;
                    }
                    return result;
                });
    }

    /** Answers everything the home screen asks for on arrival with empty data, and accepts logout. */
    public static StubService forHome(com.bhel.hrm.common.model.Employee profile) {
        return new StubService()
                .returns("getMyProfile", profile)
                .returns("getMyFamily", List.of())
                .returns("getLeaveTypes", List.of())
                .returns("getMyLeaveBalances", List.of())
                .returns("getMyLeaveApplications", List.of())
                .returns("logout", null);
    }

    /** As {@link #forHome}, plus what the HR tabs ask for on arrival. */
    public static StubService forHrHome(com.bhel.hrm.common.model.Employee profile) {
        return forHome(profile)
                .returns("listEmployees", List.of())
                .returns("listPendingLeave", List.of());
    }

    /** What the method returns (or throws, if the value is a Throwable). */
    public synchronized StubService returns(String method, Object value) {
        handlers.put(method, arguments -> value);
        return this;
    }

    /** Chooses the answer from the arguments. */
    public synchronized StubService on(String method, Function<Object[], Object> handler) {
        handlers.put(method, handler);
        return this;
    }

    public HRMService service() {
        return proxy;
    }

    public synchronized int count(String method) {
        return (int) calls.stream().filter(method::equals).count();
    }

    /** The arguments of the last call of this method. */
    public synchronized List<Object> lastArguments(String method) {
        List<Object[]> all = argumentsByMethod.get(method);
        return Arrays.asList(all.get(all.size() - 1));
    }

    public synchronized List<String> unexpectedCalls() {
        return List.copyOf(unexpected);
    }

    public synchronized int callsOnEventThread() {
        return callsOnEventThread;
    }
}
