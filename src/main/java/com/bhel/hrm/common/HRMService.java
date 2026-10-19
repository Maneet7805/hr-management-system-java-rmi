package com.bhel.hrm.common;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.common.model.YearlyReport;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.time.LocalDate;
import java.util.List;

/**
 * The contract between every client and the server. This is the only thing a client can call.
 *
 * <p>Every method except {@link #ping()} and {@link #login(String, String)} takes the session token
 * from {@link Session#token()} as its first argument. Each method throws {@link RemoteException} for
 * network problems and {@link HrmException} (or a subclass) when the server refuses or cannot finish.
 * Change this interface only by group agreement: the server and every screen depend on it.
 */
public interface HRMService extends Remote {

    // ---- Health -----------------------------------------------------------------------------

    /** Succeeds when the server is reachable and can use its database. */
    void ping() throws RemoteException, HrmException;

    // ---- Account (any logged-in user) -------------------------------------------------------

    /** Starts a session. {@code loginId} is the IC/passport number. */
    Session login(String loginId, String password) throws RemoteException, HrmException;

    void logout(String token) throws RemoteException, HrmException;

    void changePassword(String token, String oldPassword, String newPassword)
            throws RemoteException, HrmException;

    // ---- HR only ----------------------------------------------------------------------------

    /** Creates the employee with a generated temporary password, returned once. */
    RegistrationResult registerEmployee(String token, String firstName, String lastName, String icPassport)
            throws RemoteException, HrmException;

    List<Employee> listEmployees(String token) throws RemoteException, HrmException;

    List<LeaveApplication> listPendingLeave(String token) throws RemoteException, HrmException;

    /** Approves or rejects a pending application and returns it as updated. */
    LeaveApplication decideLeave(String token, long applicationId, boolean approve)
            throws RemoteException, HrmException;

    YearlyReport generateYearlyReport(String token, long employeeId, int year)
            throws RemoteException, HrmException;

    // ---- Employee: profile and family -------------------------------------------------------

    Employee getMyProfile(String token) throws RemoteException, HrmException;

    /** Employees may change only their contact details, not their name or IC/passport number. */
    Employee updateMyContact(String token, String phone, String email, String address)
            throws RemoteException, HrmException;

    List<FamilyMember> getMyFamily(String token) throws RemoteException, HrmException;

    FamilyMember addFamilyMember(String token, String name, String relationship, LocalDate dateOfBirth)
            throws RemoteException, HrmException;

    FamilyMember updateFamilyMember(String token, FamilyMember member) throws RemoteException, HrmException;

    void removeFamilyMember(String token, long familyMemberId) throws RemoteException, HrmException;

    // ---- Employee: leave --------------------------------------------------------------------

    List<LeaveType> getLeaveTypes(String token) throws RemoteException, HrmException;

    List<LeaveBalance> getMyLeaveBalances(String token, int year) throws RemoteException, HrmException;

    LeaveApplication applyForLeave(String token, long leaveTypeId, LocalDate startDate, LocalDate endDate,
            String reason) throws RemoteException, HrmException;

    List<LeaveApplication> getMyLeaveApplications(String token) throws RemoteException, HrmException;
}
