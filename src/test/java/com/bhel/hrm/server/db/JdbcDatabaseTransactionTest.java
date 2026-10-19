package com.bhel.hrm.server.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import org.junit.jupiter.api.Test;

class JdbcDatabaseTransactionTest extends DatabaseTestBase {

    private static final StoredCredentials CREDENTIALS =
            new StoredCredentials(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1000);

    /** A business error thrown inside a transaction, like the service layer will do later. */
    private static final class Rejected extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static NewEmployee newEmployee(String icPassport) {
        return new NewEmployee("Alice", "Tan", icPassport, Role.EMPLOYEE, false);
    }

    private static int employeeCount() {
        return database.read(da -> da.employees().findAll().size());
    }

    @Test
    void aFinishedTransactionIsCommitted() {
        database.transaction(da -> da.employees().insert(newEmployee("TX000001"), CREDENTIALS));

        assertEquals(1, employeeCount());
    }

    @Test
    void aTransactionThatThrowsAnUncheckedExceptionIsRolledBack() {
        assertThrows(IllegalStateException.class, () -> database.transaction(da -> {
            da.employees().insert(newEmployee("TX000002"), CREDENTIALS);
            throw new IllegalStateException("boom");
        }));

        assertEquals(0, employeeCount());
    }

    @Test
    void aTransactionThatThrowsACheckedBusinessExceptionIsRolledBackAndTheExceptionReachesTheCaller() {
        assertThrows(Rejected.class, () -> database.transaction(da -> {
            da.employees().insert(newEmployee("TX000003"), CREDENTIALS);
            throw new Rejected();
        }));

        assertEquals(0, employeeCount());
    }

    @Test
    void workDoneByReadIsVisibleToTheNextRead() {
        database.read(da -> da.employees().insert(newEmployee("TX000004"), CREDENTIALS));

        assertEquals(1, employeeCount());
    }
}
