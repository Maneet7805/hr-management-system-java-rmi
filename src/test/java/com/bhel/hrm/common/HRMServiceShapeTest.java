package com.bhel.hrm.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.HrmException;
import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Guards the frozen contract: a method added, renamed or removed by accident fails here. */
class HRMServiceShapeTest {

    private static final Set<String> EXPECTED_METHODS = new TreeSet<>(List.of(
            "ping", "login", "logout", "changePassword",
            "registerEmployee", "listEmployees", "listPendingLeave", "decideLeave", "generateYearlyReport",
            "getMyProfile", "updateMyContact", "getMyFamily",
            "addFamilyMember", "updateFamilyMember", "removeFamilyMember",
            "getLeaveTypes", "getMyLeaveBalances", "applyForLeave", "getMyLeaveApplications"));

    private static final Set<String> METHODS_WITHOUT_TOKEN = Set.of("ping", "login");

    @Test
    void serviceIsAnRmiRemoteInterface() {
        assertTrue(Remote.class.isAssignableFrom(HRMService.class));
        assertTrue(HRMService.class.isInterface());
    }

    @Test
    void serviceOffersExactlyTheAgreedMethods() {
        Set<String> actual = new TreeSet<>();
        for (Method method : HRMService.class.getDeclaredMethods()) {
            actual.add(method.getName());
        }

        assertEquals(EXPECTED_METHODS, actual);
    }

    @Test
    void everyMethodDeclaresRemoteExceptionAndHrmException() {
        for (Method method : HRMService.class.getDeclaredMethods()) {
            List<Class<?>> thrown = Arrays.asList(method.getExceptionTypes());

            assertTrue(thrown.contains(RemoteException.class), method.getName() + " must declare RemoteException");
            assertTrue(thrown.contains(HrmException.class), method.getName() + " must declare HrmException");
        }
    }

    @Test
    void everyMethodExceptPingAndLoginTakesTheSessionTokenFirst() {
        for (Method method : HRMService.class.getDeclaredMethods()) {
            if (METHODS_WITHOUT_TOKEN.contains(method.getName())) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();

            assertTrue(parameters.length >= 1 && parameters[0] == String.class,
                    method.getName() + " must take the String session token first");
        }
    }
}
