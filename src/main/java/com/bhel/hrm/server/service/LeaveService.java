package com.bhel.hrm.server.service;

import static com.bhel.hrm.server.service.ServiceSupport.optionalText;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.NewLeaveApplication;
import com.bhel.hrm.server.security.SessionManager.ActiveSession;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leave: employees see types and balances, apply, and follow their applications; HR sees what is
 * waiting and decides. The rules from DECISION.md (#19-#25) are enforced here, on the server, whatever
 * a client sends.
 */
public final class LeaveService {

    private static final int MAX_REASON = 200;

    private final Database database;
    private final AuthService auth;
    private final Clock clock;

    public LeaveService(Database database, AuthService auth, Clock clock) {
        this.database = database;
        this.auth = auth;
        this.clock = clock;
    }

    // ---- employees ----------------------------------------------------------------------------

    public List<LeaveType> getLeaveTypes(String token) throws HrmException {
        auth.requireRole(token, Role.EMPLOYEE);
        return ServiceSupport.read(database, da -> da.leaveTypes().findAll());
    }

    public List<LeaveBalance> getMyLeaveBalances(String token, int year) throws HrmException {
        ActiveSession me = auth.requireRole(token, Role.EMPLOYEE);
        BalanceCalculator.checkYear(year);
        // A transaction gives the approved and pending sums one consistent view of the data.
        return ServiceSupport.transaction(database, da -> BalanceCalculator.compute(da, me.employeeId(), year));
    }

    /**
     * Saves a PENDING application if the rules allow it. The employee's row is locked first (decision #20),
     * so two requests from the same person cannot both see the same free days and both be accepted.
     */
    public LeaveApplication applyForLeave(String token, long leaveTypeId, LocalDate startDate, LocalDate endDate,
            String reason) throws HrmException {
        ActiveSession me = auth.requireRole(token, Role.EMPLOYEE);
        if (startDate == null || endDate == null) {
            throw new ValidationException("Start date and end date are required.");
        }
        BalanceCalculator.checkYear(startDate.getYear());
        BalanceCalculator.checkYear(endDate.getYear());
        NewLeaveApplication request;
        try {
            request = new NewLeaveApplication(me.employeeId(), leaveTypeId, startDate, endDate,
                    optionalText(reason, "Reason", MAX_REASON), now());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }

        return ServiceSupport.<LeaveApplication, HrmException>transaction(database, da -> {
            da.employees().lockById(me.employeeId()).orElseThrow(() -> new NotFoundException("Employee not found."));
            LeaveType type = da.leaveTypes().findById(leaveTypeId)
                    .orElseThrow(() -> new NotFoundException("Leave type not found."));
            if (da.leave().overlaps(me.employeeId(), startDate, endDate)) {
                throw new ValidationException("You already have leave on some of those dates.");
            }
            if (type.annualAllowance() != null) {
                int year = startDate.getYear();
                Map<Long, Integer> approved = da.leave().daysUsed(me.employeeId(), year, LeaveStatus.APPROVED);
                Map<Long, Integer> pending = da.leave().daysUsed(me.employeeId(), year, LeaveStatus.PENDING);
                int remaining = type.annualAllowance()
                        - approved.getOrDefault(type.id(), 0) - pending.getOrDefault(type.id(), 0);
                if (request.days() > remaining) {
                    throw new ValidationException("Not enough " + type.name() + " leave left: " + remaining
                            + " day(s) remaining, " + request.days() + " requested.");
                }
            }
            return da.leave().insert(request);
        });
    }

    public List<LeaveApplication> getMyLeaveApplications(String token) throws HrmException {
        ActiveSession me = auth.requireRole(token, Role.EMPLOYEE);
        return ServiceSupport.read(database, da -> da.leave().findByEmployee(me.employeeId()));
    }

    // ---- HR -----------------------------------------------------------------------------------

    /** Everything still waiting for a decision, oldest first. */
    public List<LeaveApplication> listPendingLeave(String token) throws HrmException {
        auth.requireRole(token, Role.HR);
        return ServiceSupport.read(database, da -> da.leave().findByStatus(LeaveStatus.PENDING));
    }

    /**
     * Approves or rejects a pending application. Pending days already count against the balance, so
     * approving needs no second balance check.
     */
    public LeaveApplication decideLeave(String token, long applicationId, boolean approve) throws HrmException {
        ActiveSession hr = auth.requireRole(token, Role.HR);
        LeaveStatus decision = approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED;
        LocalDateTime decidedAt = now();
        return ServiceSupport.<LeaveApplication, HrmException>transaction(database, da -> {
            Optional<LeaveApplication> decided = da.leave().decide(applicationId, decision, hr.employeeId(), decidedAt);
            if (decided.isPresent()) {
                return decided.get();
            }
            // Nothing changed: either there is no such application, or someone decided it first.
            if (da.leave().findById(applicationId).isEmpty()) {
                throw new NotFoundException("Leave application not found.");
            }
            throw new ValidationException("This application has already been decided.");
        });
    }

    /** The database stores whole seconds, so the time is cut to seconds here and what is returned equals what is stored. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }
}
