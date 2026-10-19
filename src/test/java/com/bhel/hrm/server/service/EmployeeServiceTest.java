package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.PermissionDeniedException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmployeeServiceTest extends ServiceTestBase {

    // ---- register -----------------------------------------------------------------------------

    @Test
    void hrRegistersAnEmployeeWhoMustChangeTheTemporaryPasswordAtFirstLogin() throws Exception {
        String hr = hrToken();

        RegistrationResult result = employees.registerEmployee(hr, "Alice", "Tan", "AB123456");

        assertEquals("Alice", result.employee().firstName());
        assertEquals("AB123456", result.employee().icPassport());
        assertEquals(Role.EMPLOYEE, result.employee().role());
        assertTrue(result.employee().mustChangePassword());
        assertEquals(12, result.temporaryPassword().length());
        Session session = auth.login("AB123456", result.temporaryPassword());
        assertTrue(session.mustChangePassword());
    }

    @Test
    void everyRegistrationGetsADifferentTemporaryPassword() throws Exception {
        String hr = hrToken();

        String first = employees.registerEmployee(hr, "Alice", "Tan", "AB123456").temporaryPassword();
        String second = employees.registerEmployee(hr, "Bob", "Lee", "CD123456").temporaryPassword();

        assertNotEquals(first, second);
    }

    @Test
    void namesAndTheIcNumberAreTrimmed() throws Exception {
        String hr = hrToken();

        Employee employee = employees.registerEmployee(hr, "  Alice ", " Tan  ", "  AB123456 ").employee();

        assertEquals("Alice", employee.firstName());
        assertEquals("Tan", employee.lastName());
        assertEquals("AB123456", employee.icPassport());
    }

    @Test
    void anEmployeeCannotRegisterAnotherEmployee() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        PermissionDeniedException error = assertThrows(PermissionDeniedException.class,
                () -> employees.registerEmployee(alice, "Bob", "Lee", "CD123456"));

        assertEquals("Only HR staff can do this.", error.getMessage());
    }

    @Test
    void registeringNeedsALiveSession() {
        assertThrows(InvalidCredentialsException.class,
                () -> employees.registerEmployee("no-such-token", "Alice", "Tan", "AB123456"));
        assertThrows(InvalidCredentialsException.class,
                () -> employees.registerEmployee(null, "Alice", "Tan", "AB123456"));
    }

    @Test
    void aDuplicateIcNumberInAnyLetterCaseIsRefused() throws Exception {
        String hr = hrToken();
        employees.registerEmployee(hr, "Alice", "Tan", "AB123456");

        ValidationException error = assertThrows(ValidationException.class,
                () -> employees.registerEmployee(hr, "Other", "Person", "ab123456"));

        assertEquals("An employee with that IC/passport number already exists.", error.getMessage());
    }

    @Test
    void invalidNamesAndIcNumbersAreRefusedWithTheReason() throws Exception {
        String hr = hrToken();

        ValidationException blankName = assertThrows(ValidationException.class,
                () -> employees.registerEmployee(hr, "   ", "Tan", "AB123456"));
        ValidationException badIc = assertThrows(ValidationException.class,
                () -> employees.registerEmployee(hr, "Alice", "Tan", "x"));
        assertThrows(ValidationException.class, () -> employees.registerEmployee(hr, null, "Tan", "AB123456"));
        assertThrows(ValidationException.class, () -> employees.registerEmployee(hr, "Alice", "Tan", null));

        assertTrue(blankName.getMessage().contains("First name"), blankName.getMessage());
        assertTrue(badIc.getMessage().contains("IC/passport"), badIc.getMessage());
        assertTrue(employees.listEmployees(hr).stream().noneMatch(e -> e.icPassport().equals("x")));
    }

    // ---- list ---------------------------------------------------------------------------------

    @Test
    void hrListsEveryoneOrderedByLastNameThenFirstName() throws Exception {
        String hr = hrToken();
        employees.registerEmployee(hr, "Zed", "Zulu", "ZZ123456");
        employees.registerEmployee(hr, "Amy", "Alpha", "AA123456");

        List<Employee> all = employees.listEmployees(hr);

        assertEquals(List.of("Amy Alpha", "Hana Lim", "Zed Zulu"),
                all.stream().map(e -> e.firstName() + " " + e.lastName()).toList());
    }

    @Test
    void anEmployeeCannotListEveryone() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        assertThrows(PermissionDeniedException.class, () -> employees.listEmployees(alice));
    }

    // ---- temporary password lockdown ----------------------------------------------------------

    @Test
    void someoneOnATemporaryPasswordCannotUseTheirProfileOrFamily() throws Exception {
        String hr = hrToken();
        RegistrationResult registered = employees.registerEmployee(hr, "Alice", "Tan", "AB123456");
        String temporary = auth.login("AB123456", registered.temporaryPassword()).token();

        assertThrows(PermissionDeniedException.class, () -> employees.getMyProfile(temporary));
        assertThrows(PermissionDeniedException.class, () -> employees.getMyFamily(temporary));
        assertThrows(PermissionDeniedException.class,
                () -> employees.addFamilyMember(temporary, "Bob Tan", "Spouse", null));
    }

    // ---- profile and contact ------------------------------------------------------------------

    @Test
    void getMyProfileReturnsOnlyTheCallersOwnRecord() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");
        employeeToken(hr, "Bob", "Lee", "CD123456");

        assertEquals("Alice", employees.getMyProfile(alice).firstName());
        assertEquals("Hana", employees.getMyProfile(hr).firstName());
    }

    @Test
    void updateMyContactSavesTheDetailsAndChangesNothingElse() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        Employee updated = employees.updateMyContact(alice, "012-3456789", "alice@bhel.example", "1 Jalan Satu");

        assertEquals("012-3456789", updated.phone());
        assertEquals("alice@bhel.example", updated.email());
        assertEquals("1 Jalan Satu", updated.address());
        assertEquals("Alice", updated.firstName());
        assertEquals("AB123456", updated.icPassport());
        assertEquals(updated, employees.getMyProfile(alice));
    }

    @Test
    void updateMyContactTrimsAndTurnsBlankIntoNoValue() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        Employee updated = employees.updateMyContact(alice, "  012-3456789  ", "   ", null);

        assertEquals("012-3456789", updated.phone());
        assertNull(updated.email());
        assertNull(updated.address());
    }

    @Test
    void contactDetailsAreCheckedAgainstTheColumnLimits() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        assertEquals("Phone must be at most 30 characters.", assertThrows(ValidationException.class,
                () -> employees.updateMyContact(alice, "1".repeat(31), null, null)).getMessage());
        assertEquals("Email must be at most 100 characters.", assertThrows(ValidationException.class,
                () -> employees.updateMyContact(alice, null, "a".repeat(95) + "@b.com", null)).getMessage());
        assertEquals("Address must be at most 255 characters.", assertThrows(ValidationException.class,
                () -> employees.updateMyContact(alice, null, null, "x".repeat(256))).getMessage());
        assertEquals("Email must contain '@'.", assertThrows(ValidationException.class,
                () -> employees.updateMyContact(alice, null, "not-an-email", null)).getMessage());
    }

    @Test
    void theLongestAllowedContactDetailsAreAccepted() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");
        String email = "a".repeat(94) + "@b.com";

        Employee updated = employees.updateMyContact(alice, "1".repeat(30), email, "x".repeat(255));

        assertEquals(100, updated.email().length());
        assertEquals(255, updated.address().length());
    }

    @Test
    void oneEmployeesContactUpdateNeverTouchesAnother() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");
        String bob = employeeToken(hr, "Bob", "Lee", "CD123456");

        employees.updateMyContact(alice, "111", "alice@bhel.example", "Alice street");

        assertNull(employees.getMyProfile(bob).phone());
    }

    // ---- family -------------------------------------------------------------------------------

    @Test
    void anEmployeeAddsListsUpdatesAndRemovesFamilyMembers() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        FamilyMember spouse = employees.addFamilyMember(alice, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));
        FamilyMember child = employees.addFamilyMember(alice, "Cara Tan", "Child", null);
        assertEquals(List.of(spouse, child), employees.getMyFamily(alice));

        FamilyMember changed = employees.updateFamilyMember(alice,
                new FamilyMember(spouse.id(), "Robert Tan", "Husband", LocalDate.of(1990, 5, 17)));
        assertEquals("Robert Tan", changed.name());

        employees.removeFamilyMember(alice, child.id());
        assertEquals(List.of(changed), employees.getMyFamily(alice));
    }

    @Test
    void familyDetailsAreTrimmed() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        FamilyMember member = employees.addFamilyMember(alice, "  Bob Tan ", " Spouse  ", null);

        assertEquals("Bob Tan", member.name());
        assertEquals("Spouse", member.relationship());
    }

    @Test
    void invalidFamilyDetailsAreRefusedWithTheReasonAndNothingIsSaved() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        ValidationException blank = assertThrows(ValidationException.class,
                () -> employees.addFamilyMember(alice, " ", "Spouse", null));
        ValidationException future = assertThrows(ValidationException.class,
                () -> employees.addFamilyMember(alice, "Baby", "Child", LocalDate.now().plusDays(1)));

        assertTrue(blank.getMessage().contains("Name"), blank.getMessage());
        assertTrue(future.getMessage().contains("future"), future.getMessage());
        assertTrue(employees.getMyFamily(alice).isEmpty());
    }

    @Test
    void anEmployeeCannotSeeChangeOrRemoveSomeoneElsesFamilyMember() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");
        String bob = employeeToken(hr, "Bob", "Lee", "CD123456");
        FamilyMember aliceSpouse = employees.addFamilyMember(alice, "Bob Tan", "Spouse", null);

        NotFoundException update = assertThrows(NotFoundException.class, () -> employees.updateFamilyMember(bob,
                new FamilyMember(aliceSpouse.id(), "Hacked", "Nobody", null)));
        NotFoundException remove = assertThrows(NotFoundException.class,
                () -> employees.removeFamilyMember(bob, aliceSpouse.id()));

        assertEquals("Family member not found.", update.getMessage());
        assertEquals("Family member not found.", remove.getMessage());
        assertTrue(employees.getMyFamily(bob).isEmpty());
        assertEquals(List.of(aliceSpouse), employees.getMyFamily(alice));
    }

    @Test
    void updatingOrRemovingAMemberThatDoesNotExistIsNotFound() throws Exception {
        String hr = hrToken();
        String alice = employeeToken(hr, "Alice", "Tan", "AB123456");

        assertThrows(NotFoundException.class,
                () -> employees.updateFamilyMember(alice, new FamilyMember(999_999L, "X", "Y", null)));
        assertThrows(NotFoundException.class, () -> employees.removeFamilyMember(alice, 999_999L));
    }

    @Test
    void hrStaffCanKeepTheirOwnFamilyToo() throws Exception {
        String hr = hrToken();

        FamilyMember member = employees.addFamilyMember(hr, "Sam Lim", "Child", null);

        assertEquals(List.of(member), employees.getMyFamily(hr));
    }

    @Test
    void familyMethodsNeedALiveSession() {
        assertThrows(InvalidCredentialsException.class, () -> employees.getMyFamily("no-such-token"));
    }
}
