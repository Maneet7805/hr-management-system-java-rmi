package com.bhel.hrm.server.dao.jdbc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.DuplicateValueException;
import com.bhel.hrm.server.dao.LoginRecord;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import com.bhel.hrm.server.db.DatabaseTestBase;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class EmployeeDaoTest extends DatabaseTestBase {

    private static final StoredCredentials CREDENTIALS =
            new StoredCredentials(new byte[] {1, 2, 3, 4}, new byte[] {9, 8, 7}, 210_000);

    private static NewEmployee newEmployee(String first, String last, String icPassport) {
        return new NewEmployee(first, last, icPassport, Role.EMPLOYEE, true);
    }

    private static Employee insert(String first, String last, String icPassport) {
        return database.transaction(da -> da.employees().insert(newEmployee(first, last, icPassport), CREDENTIALS));
    }

    @Test
    void insertReturnsTheEmployeeWithAGeneratedId() {
        Employee saved = insert("Alice", "Tan", "ED000001");

        assertTrue(saved.id() > 0);
        assertEquals("Alice", saved.firstName());
        assertEquals("ED000001", saved.icPassport());
        assertEquals(Role.EMPLOYEE, saved.role());
        assertTrue(saved.mustChangePassword());
        assertNull(saved.phone());
    }

    @Test
    void findByIdReturnsTheSameEmployee() {
        Employee saved = insert("Alice", "Tan", "ED000002");

        assertEquals(Optional.of(saved), database.read(da -> da.employees().findById(saved.id())));
    }

    @Test
    void findByIdForAnUnknownEmployeeIsEmpty() {
        assertEquals(Optional.empty(), database.read(da -> da.employees().findById(999_999L)));
    }

    @Test
    void anHrEmployeeKeepsTheHrRole() {
        Employee hr = database.transaction(da -> da.employees()
                .insert(new NewEmployee("Hana", "Lim", "ED000003", Role.HR, false), CREDENTIALS));

        assertEquals(Role.HR, hr.role());
        assertFalse(hr.mustChangePassword());
    }

    @Test
    void findLoginReturnsTheEmployeeAndTheStoredCredentials() {
        Employee saved = insert("Alice", "Tan", "ED000004");

        LoginRecord login = database.read(da -> da.employees().findLogin("ED000004")).orElseThrow();

        assertEquals(saved, login.employee());
        assertArrayEquals(CREDENTIALS.hash(), login.credentials().hash());
        assertArrayEquals(CREDENTIALS.salt(), login.credentials().salt());
        assertEquals(210_000, login.credentials().iterations());
    }

    @Test
    void findLoginIgnoresCaseBecauseTheIcNumberIsUniqueIgnoringCase() {
        insert("Alice", "Tan", "ab000005");

        assertTrue(database.read(da -> da.employees().findLogin("AB000005")).isPresent());
    }

    @Test
    void findLoginForAnUnknownNumberIsEmpty() {
        assertTrue(database.read(da -> da.employees().findLogin("NOBODY99")).isEmpty());
    }

    @Test
    void aSecondEmployeeWithTheSameIcNumberIgnoringCaseIsRefused() {
        insert("Alice", "Tan", "AB000006");

        assertThrows(DuplicateValueException.class, () -> insert("Bob", "Lee", "ab000006"));
    }

    @Test
    void findAllOrdersByLastNameThenFirstName() {
        insert("Zed", "Zulu", "ED000007");
        insert("Bob", "Alpha", "ED000008");
        insert("Amy", "Alpha", "ED000009");

        List<Employee> all = database.read(da -> da.employees().findAll());

        assertEquals(List.of("Amy", "Bob", "Zed"), all.stream().map(Employee::firstName).toList());
    }

    @Test
    void updateContactChangesOnlyPhoneEmailAndAddress() {
        Employee saved = insert("Alice", "Tan", "ED000010");

        Employee updated = database.transaction(da -> da.employees()
                .updateContact(saved.id(), "012-3456789", "alice@bhel.example", "1 Jalan Satu")).orElseThrow();

        assertEquals("012-3456789", updated.phone());
        assertEquals("alice@bhel.example", updated.email());
        assertEquals("1 Jalan Satu", updated.address());
        assertEquals("Alice", updated.firstName());
        assertEquals("ED000010", updated.icPassport());
        assertEquals(Optional.of(updated), database.read(da -> da.employees().findById(saved.id())));
    }

    @Test
    void updateContactForAnUnknownEmployeeIsEmpty() {
        assertTrue(database.transaction(da -> da.employees().updateContact(999_999L, "1", "a@b.c", "x")).isEmpty());
    }

    @Test
    void setCredentialsReplacesThePasswordAndTheMustChangeFlag() {
        Employee saved = insert("Alice", "Tan", "ED000011");
        StoredCredentials changed = new StoredCredentials(new byte[] {5, 5, 5}, new byte[] {6, 6}, 300_000);

        boolean done = database.transaction(da -> da.employees().setCredentials(saved.id(), changed, false));
        LoginRecord login = database.read(da -> da.employees().findLogin("ED000011")).orElseThrow();

        assertTrue(done);
        assertArrayEquals(changed.hash(), login.credentials().hash());
        assertEquals(300_000, login.credentials().iterations());
        assertFalse(login.employee().mustChangePassword());
    }

    @Test
    void setCredentialsForAnUnknownEmployeeReturnsFalse() {
        boolean done = database.transaction(da -> da.employees().setCredentials(999_999L, CREDENTIALS, false));

        assertFalse(done);
    }

    @Test
    void lockByIdForAnUnknownEmployeeIsEmpty() {
        assertTrue(database.transaction(da -> da.employees().lockById(999_999L)).isEmpty());
    }

    /** The apply-leave rule (#20) relies on this: a second transaction must wait for the first to finish. */
    @Test
    @Timeout(30)
    void lockByIdMakesASecondTransactionWaitUntilTheFirstFinishes() throws Exception {
        long id = insert("Alice", "Tan", "ED000012").id();
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch letFirstFinish = new CountDownLatch(1);
        AtomicBoolean secondGotLock = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try {
                database.transaction(da -> {
                    da.employees().lockById(id);
                    firstHoldsLock.countDown();
                    awaitQuietly(letFirstFinish);
                    return null;
                });
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });
        Thread second = new Thread(() -> {
            try {
                database.transaction(da -> da.employees().lockById(id));
                secondGotLock.set(true);
            } catch (RuntimeException e) {
                failure.set(e);
            }
        });

        first.start();
        assertTrue(firstHoldsLock.await(10, TimeUnit.SECONDS));
        second.start();
        Thread.sleep(1000);
        assertFalse(secondGotLock.get(), "the second transaction must still be waiting for the lock");

        letFirstFinish.countDown();
        first.join(10_000);
        second.join(10_000);

        assertNull(failure.get());
        assertTrue(secondGotLock.get(), "the second transaction should get the lock once the first has finished");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
