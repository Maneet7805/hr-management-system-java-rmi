package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.server.dao.DataAccess;
import java.util.List;
import java.util.Map;

/**
 * Works out leave balances. Nothing is stored (decision #19): a balance is the yearly allowance minus the
 * days of approved and pending applications that start in that year, so it can never drift from the history.
 */
final class BalanceCalculator {

    private static final int FIRST_YEAR = 2000;
    private static final int LAST_YEAR = 2100;

    private BalanceCalculator() {
    }

    /** One balance per leave type, in leave-type order. Call it inside the transaction that needs it. */
    static List<LeaveBalance> compute(DataAccess data, long employeeId, int year) {
        Map<Long, Integer> approved = data.leave().daysUsed(employeeId, year, LeaveStatus.APPROVED);
        Map<Long, Integer> pending = data.leave().daysUsed(employeeId, year, LeaveStatus.PENDING);
        return data.leaveTypes().findAll().stream()
                .map(type -> new LeaveBalance(type, year,
                        approved.getOrDefault(type.id(), 0), pending.getOrDefault(type.id(), 0)))
                .toList();
    }

    /** Refuses obviously wrong years (typos such as 20206) before they reach the database. */
    static void checkYear(int year) throws ValidationException {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new ValidationException("Year must be between " + FIRST_YEAR + " and " + LAST_YEAR + ".");
        }
    }
}
