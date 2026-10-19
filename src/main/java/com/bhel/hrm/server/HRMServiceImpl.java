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
import com.bhel.hrm.server.service.ServiceErrors;
import java.time.LocalDate;
import java.util.List;

/**
 * The object clients call over RMI. Account methods are real; the employee and HR features are still
 * "not implemented" and arrive in Phase 5.
 */
public class HRMServiceImpl implements HRMService {

    private final Database database;
    private final AuthService auth;

    public HRMServiceImpl(Database database, AuthService auth) {
        this.database = database;
        this.auth = auth;
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

    @Override
    public RegistrationResult registerEmployee(String token, String firstName, String lastName, String icPassport)
            throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public List<Employee> listEmployees(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public List<LeaveApplication> listPendingLeave(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public LeaveApplication decideLeave(String token, long applicationId, boolean approve)
            throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public YearlyReport generateYearlyReport(String token, long employeeId, int year)
            throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public Employee getMyProfile(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public Employee updateMyContact(String token, String phone, String email, String address)
            throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public List<FamilyMember> getMyFamily(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public FamilyMember addFamilyMember(String token, String name, String relationship, LocalDate dateOfBirth)
            throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public FamilyMember updateFamilyMember(String token, FamilyMember member) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public void removeFamilyMember(String token, long familyMemberId) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public List<LeaveType> getLeaveTypes(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public List<LeaveBalance> getMyLeaveBalances(String token, int year) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public LeaveApplication applyForLeave(String token, long leaveTypeId, LocalDate startDate, LocalDate endDate,
            String reason) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public List<LeaveApplication> getMyLeaveApplications(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    private static ServiceUnavailableException notImplemented() {
        return new ServiceUnavailableException("Not implemented yet");
    }
}
