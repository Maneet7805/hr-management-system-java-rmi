package com.bhel.hrm.server.dao.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.StoredCredentials;
import com.bhel.hrm.server.db.DatabaseTestBase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FamilyMemberDaoTest extends DatabaseTestBase {

    private static final StoredCredentials CREDENTIALS =
            new StoredCredentials(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1000);

    private static long employee(String icPassport) {
        return database.transaction(da -> da.employees()
                .insert(new NewEmployee("Alice", "Tan", icPassport, Role.EMPLOYEE, false), CREDENTIALS).id());
    }

    private static FamilyMember add(long employeeId, String name, String relationship, LocalDate dateOfBirth) {
        return database.transaction(da -> da.family().insert(employeeId, name, relationship, dateOfBirth));
    }

    @Test
    void insertReturnsTheMemberWithAGeneratedId() {
        long alice = employee("FM000001");

        FamilyMember saved = add(alice, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));

        assertTrue(saved.id() > 0);
        assertEquals("Bob Tan", saved.name());
        assertEquals("Spouse", saved.relationship());
        assertEquals(LocalDate.of(1990, 5, 17), saved.dateOfBirth());
    }

    @Test
    void dateOfBirthIsOptional() {
        FamilyMember saved = add(employee("FM000002"), "Baby Tan", "Child", null);

        assertNull(saved.dateOfBirth());
    }

    @Test
    void findByEmployeeReturnsOnlyThatEmployeesFamilyInTheOrderAdded() {
        long alice = employee("FM000003");
        long other = employee("FM000004");
        FamilyMember first = add(alice, "Bob Tan", "Spouse", null);
        FamilyMember second = add(alice, "Cara Tan", "Child", null);
        add(other, "Someone Else", "Parent", null);

        List<FamilyMember> family = database.read(da -> da.family().findByEmployee(alice));

        assertEquals(List.of(first, second), family);
    }

    @Test
    void anEmployeeWithNoFamilyGetsAnEmptyList() {
        long alice = employee("FM000005");

        assertTrue(database.read(da -> da.family().findByEmployee(alice)).isEmpty());
    }

    @Test
    void updateChangesTheMemberOfTheRightEmployee() {
        long alice = employee("FM000006");
        FamilyMember saved = add(alice, "Bob Tan", "Spouse", null);
        FamilyMember changed = new FamilyMember(saved.id(), "Robert Tan", "Husband", LocalDate.of(1990, 5, 17));

        Optional<FamilyMember> updated = database.transaction(da -> da.family().update(alice, changed));

        assertEquals(Optional.of(changed), updated);
        assertEquals(List.of(changed), database.read(da -> da.family().findByEmployee(alice)));
    }

    @Test
    void anotherEmployeeCannotUpdateSomeoneElsesFamilyMember() {
        long alice = employee("FM000007");
        long mallory = employee("FM000008");
        FamilyMember saved = add(alice, "Bob Tan", "Spouse", null);
        FamilyMember forged = new FamilyMember(saved.id(), "Hacked", "Nobody", null);

        Optional<FamilyMember> updated = database.transaction(da -> da.family().update(mallory, forged));

        assertTrue(updated.isEmpty());
        assertEquals(List.of(saved), database.read(da -> da.family().findByEmployee(alice)));
    }

    @Test
    void updatingAMemberThatDoesNotExistReturnsEmpty() {
        long alice = employee("FM000009");

        assertTrue(database.transaction(da -> da.family().update(alice, new FamilyMember(999_999L, "X", "Y", null)))
                .isEmpty());
    }

    @Test
    void deleteRemovesTheMember() {
        long alice = employee("FM000010");
        FamilyMember saved = add(alice, "Bob Tan", "Spouse", null);

        boolean deleted = database.transaction(da -> da.family().delete(alice, saved.id()));

        assertTrue(deleted);
        assertTrue(database.read(da -> da.family().findByEmployee(alice)).isEmpty());
    }

    @Test
    void anotherEmployeeCannotDeleteSomeoneElsesFamilyMember() {
        long alice = employee("FM000011");
        long mallory = employee("FM000012");
        FamilyMember saved = add(alice, "Bob Tan", "Spouse", null);

        boolean deleted = database.transaction(da -> da.family().delete(mallory, saved.id()));

        assertFalse(deleted);
        assertEquals(List.of(saved), database.read(da -> da.family().findByEmployee(alice)));
    }

    @Test
    void deletingAMemberThatDoesNotExistReturnsFalse() {
        long alice = employee("FM000013");

        boolean deleted = database.transaction(da -> da.family().delete(alice, 999_999L));

        assertFalse(deleted);
    }

    @Test
    void addingAMemberForAnEmployeeThatDoesNotExistFailsWithoutSqlInTheMessage() {
        DataAccessException error = assertThrows(DataAccessException.class,
                () -> add(999_999L, "Bob Tan", "Spouse", null));

        assertFalse(error.getMessage().toUpperCase().contains("INSERT"), error.getMessage());
    }

    @Test
    void removingAnEmployeeAlsoRemovesTheirFamily() throws SQLException {
        long alice = employee("FM000014");
        add(alice, "Bob Tan", "Spouse", null);

        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM employee WHERE id = ?")) {
            statement.setLong(1, alice);
            statement.executeUpdate();
        }

        assertTrue(database.read(da -> da.family().findByEmployee(alice)).isEmpty());
    }
}
