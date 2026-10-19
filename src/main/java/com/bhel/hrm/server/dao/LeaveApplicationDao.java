package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stores leave applications. It records and finds; the service decides whether a request is allowed
 * (enough days, no overlap), using {@link #overlaps} and {@link #daysUsed} inside one transaction.
 */
public interface LeaveApplicationDao {

    /** Saves a new application as PENDING and returns it as stored. */
    LeaveApplication insert(NewLeaveApplication application);

    Optional<LeaveApplication> findById(long id);

    /** One employee's applications, newest start date first. */
    List<LeaveApplication> findByEmployee(long employeeId);

    /** All applications with this status, oldest application first. */
    List<LeaveApplication> findByStatus(LeaveStatus status);

    /** One employee's applications that start in {@code year}, earliest first. */
    List<LeaveApplication> findByEmployeeAndYear(long employeeId, int year);

    /**
     * Whether the employee already has PENDING or APPROVED leave sharing at least one day with
     * the given dates (both ends included). Rejected leave never counts.
     */
    boolean overlaps(long employeeId, LocalDate startDate, LocalDate endDate);

    /** Days taken per leave type id, for applications with this status that start in {@code year}. */
    Map<Long, Integer> daysUsed(long employeeId, int year, LeaveStatus status);

    /**
     * Approves or rejects an application, but only if it is still PENDING. The check and the change
     * are one statement, so when two people decide at once exactly one succeeds.
     *
     * @param decision must be APPROVED or REJECTED
     * @return the updated application, or empty if it does not exist or was already decided
     */
    Optional<LeaveApplication> decide(long applicationId, LeaveStatus decision, long decidedBy,
            LocalDateTime decidedAt);
}
