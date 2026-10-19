package com.bhel.hrm.server;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.common.model.YearlyReport;
import java.time.LocalDate;
import java.util.List;

/**
 * Walking-skeleton version of the service: it answers {@link #ping()} and nothing else, so the
 * secure connection can be proven before any business logic exists. Phases 4 and 5 replace each
 * "not implemented" method with the real one.
 */
public class HRMServiceImpl implements HRMService {

    @Override
    public void ping() {
        // Reaching this line means the TLS connection and the RMI call both worked.
    }

    @Override
    public Session login(String loginId, String password) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public void logout(String token) throws ServiceUnavailableException {
        throw notImplemented();
    }

    @Override
    public void changePassword(String token, String oldPassword, String newPassword)
            throws ServiceUnavailableException {
        throw notImplemented();
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
