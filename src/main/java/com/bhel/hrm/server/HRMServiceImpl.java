package com.bhel.hrm.server;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.common.model.YearlyReport;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.service.AuthService;
import com.bhel.hrm.server.service.EmployeeService;
import com.bhel.hrm.server.service.LeaveService;
import com.bhel.hrm.server.service.ReportService;
import com.bhel.hrm.server.service.ServiceErrors;
import java.time.LocalDate;
import java.util.List;

/**
 * The object clients call over RMI. It holds no rules of its own: every method hands the call to the
 * service that owns it (accounts, employees, leave, reports), and those services check who is calling.
 */
public class HRMServiceImpl implements HRMService {

    private final Database database;
    private final AuthService auth;
    private final EmployeeService employees;
    private final LeaveService leave;
    private final ReportService reports;

    public HRMServiceImpl(Database database, AuthService auth, EmployeeService employees, LeaveService leave,
            ReportService reports) {
        this.database = database;
        this.auth = auth;
        this.employees = employees;
        this.leave = leave;
        this.reports = reports;
    }

    /** Succeeds only if the database answers too, so "the server is up" really means it can work. */
    @Override
    public void ping() throws ServiceUnavailableException {
        try {
            database.read(da -> da.employees().findById(0));
        } catch (DataAccessException e) {
            throw ServiceErrors.unavailable(e);
        }
    }

    // ---- account ------------------------------------------------------------------------------

    @Override
    public Session login(String loginId, String password) throws HrmException {
        return auth.login(loginId, password);
    }

    @Override
    public void logout(String token) {
        auth.logout(token);
    }

    @Override
    public void changePassword(String token, String oldPassword, String newPassword) throws HrmException {
        auth.changePassword(token, oldPassword, newPassword);
    }

    // ---- HR -----------------------------------------------------------------------------------

    @Override
    public RegistrationResult registerEmployee(String token, String firstName, String lastName, String icPassport)
            throws HrmException {
        return employees.registerEmployee(token, firstName, lastName, icPassport);
    }

    @Override
    public List<Employee> listEmployees(String token) throws HrmException {
        return employees.listEmployees(token);
    }

    @Override
    public List<LeaveApplication> listPendingLeave(String token) throws HrmException {
        return leave.listPendingLeave(token);
    }

    @Override
    public LeaveApplication decideLeave(String token, long applicationId, boolean approve) throws HrmException {
        return leave.decideLeave(token, applicationId, approve);
    }

    @Override
    public YearlyReport generateYearlyReport(String token, long employeeId, int year) throws HrmException {
        return reports.generateYearlyReport(token, employeeId, year);
    }

    // ---- employee: profile and family ---------------------------------------------------------

    @Override
    public Employee getMyProfile(String token) throws HrmException {
        return employees.getMyProfile(token);
    }

    @Override
    public Employee updateMyContact(String token, String phone, String email, String address)
            throws HrmException {
        return employees.updateMyContact(token, phone, email, address);
    }

    @Override
    public List<FamilyMember> getMyFamily(String token) throws HrmException {
        return employees.getMyFamily(token);
    }

    @Override
    public FamilyMember addFamilyMember(String token, String name, String relationship, LocalDate dateOfBirth)
            throws HrmException {
        return employees.addFamilyMember(token, name, relationship, dateOfBirth);
    }

    @Override
    public FamilyMember updateFamilyMember(String token, FamilyMember member) throws HrmException {
        return employees.updateFamilyMember(token, member);
    }

    @Override
    public void removeFamilyMember(String token, long familyMemberId) throws HrmException {
        employees.removeFamilyMember(token, familyMemberId);
    }

    // ---- employee: leave ----------------------------------------------------------------------

    @Override
    public List<LeaveType> getLeaveTypes(String token) throws HrmException {
        return leave.getLeaveTypes(token);
    }

    @Override
    public List<LeaveBalance> getMyLeaveBalances(String token, int year) throws HrmException {
        return leave.getMyLeaveBalances(token, year);
    }

    @Override
    public LeaveApplication applyForLeave(String token, long leaveTypeId, LocalDate startDate, LocalDate endDate,
            String reason) throws HrmException {
        return leave.applyForLeave(token, leaveTypeId, startDate, endDate, reason);
    }

    @Override
    public List<LeaveApplication> getMyLeaveApplications(String token) throws HrmException {
        return leave.getMyLeaveApplications(token);
    }
}
