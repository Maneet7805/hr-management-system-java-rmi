package com.bhel.hrm.common.model;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * One family member of an employee.
 *
 * @param dateOfBirth optional; must not be in the future
 */
public record FamilyMember(long id, String name, String relationship, LocalDate dateOfBirth)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int MAX_TEXT_LENGTH = 50;

    public FamilyMember {
        Checks.requireText(name, "Name", MAX_TEXT_LENGTH);
        Checks.requireText(relationship, "Relationship", MAX_TEXT_LENGTH);
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth must not be in the future");
        }
    }
}
