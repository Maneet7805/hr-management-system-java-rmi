package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.FamilyMember;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Stores an employee's family members. Changing or removing a member always names the employee,
 * so an employee can never touch someone else's family.
 */
public interface FamilyMemberDao {

    FamilyMember insert(long employeeId, String name, String relationship, LocalDate dateOfBirth);

    /** In the order they were added. */
    List<FamilyMember> findByEmployee(long employeeId);

    /** Replaces name, relationship and date of birth. Empty if that member does not belong to this employee. */
    Optional<FamilyMember> update(long employeeId, FamilyMember member);

    /** {@code false} if that member does not exist or belongs to another employee. */
    boolean delete(long employeeId, long familyMemberId);
}
