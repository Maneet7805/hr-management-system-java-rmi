package com.bhel.hrm.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.lang.reflect.Method;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientServiceTest {

    private static final long[] TWO_PAUSES = {250, 500};

    private final List<Long> pauses = new ArrayList<>();
    private final AtomicInteger connects = new AtomicInteger();

    private static RemoteException connectionLost() {
        return new ConnectException("Connection refused");
    }

    /** A service that connects to the given stand-in every time, and counts how often it was asked to. */
    private HRMService resilient(ScriptedService scripted) {
        return ResilientService.create(() -> {
            connects.incrementAndGet();
            return scripted.service();
        }, TWO_PAUSES, pauses::add);
    }

    // ---- reads --------------------------------------------------------------------------------

    @Test
    void aReadIsRetriedAfterAConnectionFailureAndThenSucceeds() throws Exception {
        List<Employee> answer = List.of();
        ScriptedService scripted = new ScriptedService(connectionLost(), answer);

        Object result = resilient(scripted).listEmployees("token");

        assertSame(answer, result);
        assertEquals(2, scripted.calls());
        assertEquals(2, connects.get(), "the server is looked up again after a failure");
    }

    @Test
    void aReadGivesUpAfterThreeAttemptsAndThrowsTheLastFailure() {
        ScriptedService scripted = new ScriptedService(
                connectionLost(), connectionLost(), connectionLost(), connectionLost());

        assertThrows(RemoteException.class, () -> resilient(scripted).listEmployees("token"));

        assertEquals(3, scripted.calls());
    }

    @Test
    void thePausesBetweenAttemptsGrow() {
        ScriptedService scripted = new ScriptedService(connectionLost(), connectionLost(), connectionLost());

        assertThrows(RemoteException.class, () -> resilient(scripted).getMyProfile("token"));

        assertEquals(List.of(250L, 500L), pauses);
    }

    @Test
    void aBusinessErrorIsReturnedAtOnceAndNeverRetried() {
        ScriptedService scripted = new ScriptedService(new ValidationException("Not enough Annual leave left"));

        ValidationException error = assertThrows(ValidationException.class,
                () -> resilient(scripted).getMyLeaveBalances("token", 2026));

        assertEquals("Not enough Annual leave left", error.getMessage());
        assertEquals(1, scripted.calls());
        assertTrue(pauses.isEmpty());
    }

    @Test
    void loginCountsAsARead() throws Exception {
        Session session = new Session("token-value", 1L, "Alice Tan", Role.EMPLOYEE, false);
        ScriptedService scripted = new ScriptedService(connectionLost(), session);

        Session result = resilient(scripted).login("AB123456", "a password");

        assertSame(session, result);
        assertEquals(2, scripted.calls());
    }

    @Test
    void argumentsAndResultsPassThroughUnchanged() throws Exception {
        Session session = new Session("token-value", 1L, "Alice Tan", Role.EMPLOYEE, false);
        ScriptedService scripted = new ScriptedService(session);

        assertSame(session, resilient(scripted).login("AB123456", "a password"));
        assertEquals(List.of("login"), scripted.methodsCalled());
    }

    // ---- writes -------------------------------------------------------------------------------

    @Test
    void aWriteIsNotRetriedOnceTheCallWasOnItsWay() {
        ScriptedService scripted = new ScriptedService(connectionLost(), null);

        assertThrows(RemoteException.class, () -> resilient(scripted).applyForLeave(
                "token", 1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "A reason"));

        assertEquals(1, scripted.calls(), "a lost reply must not become a second application");
        assertTrue(pauses.isEmpty());
    }

    @Test
    void everyKindOfWriteIsNotRetried() {
        for (String name : ResilientService.writeMethods()) {
            Method method = Arrays.stream(HRMService.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
            ScriptedService scripted = new ScriptedService(connectionLost(), null);
            Object[] arguments = defaultArguments(method);

            assertThrows(RemoteException.class, () -> {
                try {
                    method.invoke(resilient(scripted), arguments);
                } catch (java.lang.reflect.InvocationTargetException wrapped) {
                    throw wrapped.getCause();
                }
            }, name);

            assertEquals(1, scripted.calls(), name + " must not be retried");
        }
    }

    @Test
    void aWriteIsRetriedWhenTheConnectionCouldNotBeMadeBecauseNothingWasSent() throws Exception {
        ScriptedService scripted = new ScriptedService((Object) null);
        AtomicInteger attempts = new AtomicInteger();
        HRMService service = ResilientService.create(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ServiceUnavailableException("Cannot reach the HR server at localhost:1099.");
            }
            return scripted.service();
        }, TWO_PAUSES, pauses::add);

        service.applyForLeave("token", 1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), null);

        assertEquals(3, attempts.get());
        assertEquals(1, scripted.calls(), "the call itself was sent exactly once");
    }

    @Test
    void theServerIsLookedUpAgainAfterAWriteFailedSoTheNextCallRecovers() throws Exception {
        ScriptedService scripted = new ScriptedService(connectionLost(), null);
        HRMService service = resilient(scripted);

        assertThrows(RemoteException.class, () -> service.removeFamilyMember("token", 1L));
        service.getMyFamily("token");

        assertEquals(2, connects.get());
    }

    // ---- connection problems ------------------------------------------------------------------

    @Test
    void whenTheServerCannotBeReachedAtAllTheLastReasonIsReported() {
        AtomicInteger attempts = new AtomicInteger();
        HRMService service = ResilientService.create(() -> {
            attempts.incrementAndGet();
            throw new ServiceUnavailableException("Cannot reach the HR server at localhost:1099.");
        }, TWO_PAUSES, pauses::add);

        ServiceUnavailableException error = assertThrows(ServiceUnavailableException.class, service::ping);

        assertEquals("Cannot reach the HR server at localhost:1099.", error.getMessage());
        assertEquals(3, attempts.get());
    }

    @Test
    void theConnectionIsMadeOnFirstUseNotWhenTheWrapperIsCreated() {
        ResilientService.create(() -> {
            connects.incrementAndGet();
            return new ScriptedService().service();
        }, TWO_PAUSES, pauses::add);

        assertEquals(0, connects.get());
    }

    // ---- bookkeeping --------------------------------------------------------------------------

    @Test
    void everyMethodOfTheServiceIsClassifiedAsExactlyOneOfReadOrWrite() {
        Set<String> all = new HashSet<>();
        for (Method method : HRMService.class.getMethods()) {
            all.add(method.getName());
        }
        Set<String> reads = ResilientService.readMethods();
        Set<String> writes = ResilientService.writeMethods();

        Set<String> both = new HashSet<>(reads);
        both.retainAll(writes);
        Set<String> classified = new HashSet<>(reads);
        classified.addAll(writes);

        assertTrue(both.isEmpty(), "in both lists: " + both);
        assertEquals(all, classified, "a method of HRMService is missing from the read or write list");
    }

    @Test
    void objectMethodsOnTheWrapperDoNotCallTheServer() {
        ScriptedService scripted = new ScriptedService();
        HRMService service = resilient(scripted);

        service.toString();
        service.hashCode();

        assertEquals(0, scripted.calls());
        assertEquals(0, connects.get());
    }

    /** Dummy values so any HRMService method can be called by reflection. */
    private static Object[] defaultArguments(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            if (types[i] == long.class) {
                values[i] = 1L;
            } else if (types[i] == int.class) {
                values[i] = 2026;
            } else if (types[i] == boolean.class) {
                values[i] = true;
            }
        }
        return values;
    }
}
