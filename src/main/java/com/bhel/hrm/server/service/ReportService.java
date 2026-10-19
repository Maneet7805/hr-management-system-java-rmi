package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.YearlyReport;
import com.bhel.hrm.server.dao.Database;

/** The yearly report HR asks for: profile, family details and leave history of one employee. */
public final class ReportService {

    private final Database database;
    private final AuthService auth;

    public ReportService(Database database, AuthService auth) {
        this.database = database;
        this.auth = auth;
    }

    /**
     * Everything is read in one transaction, so the profile, family, applications and balances all
     * describe the same moment, even if someone changes data while the report is being built.
     */
    public YearlyReport generateYearlyReport(String token, long employeeId, int year) throws HrmException {
        auth.requireRole(token, Role.HR);
        BalanceCalculator.checkYear(year);
        return ServiceSupport.<YearlyReport, HrmException>transaction(database, da -> {
            Employee employee = da.employees().findById(employeeId)
                    .orElseThrow(() -> new NotFoundException("Employee not found."));
            return new YearlyReport(employee, year,
                    da.family().findByEmployee(employeeId),
                    da.leave().findByEmployeeAndYear(employeeId, year),
                    BalanceCalculator.compute(da, employeeId, year));
        });
    }
}
