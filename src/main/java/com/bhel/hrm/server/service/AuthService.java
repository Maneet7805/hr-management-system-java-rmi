package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.PermissionDeniedException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.Database;
import com.bhel.hrm.server.dao.LoginRecord;
import com.bhel.hrm.server.dao.StoredCredentials;
import com.bhel.hrm.server.security.PasswordHasher;
import com.bhel.hrm.server.security.PasswordPolicy;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.security.SessionManager.ActiveSession;
import java.util.Optional;

/**
 * Logging in, logging out, changing a password, and answering "who is calling and may they do this?"
 * for every other part of the service. All the permission rules for sessions live here, in one place.
 */
public final class AuthService {

    private static final int MAX_LOGIN_ID_LENGTH = 20;
    private static final String WRONG_LOGIN = "Wrong ID or password.";

    private final Database database;
    private final PasswordHasher hasher;
    private final SessionManager sessions;

    /**
     * Checked against when the ID does not exist, so an unknown ID takes as long as a wrong password
     * and the response time does not reveal which IDs are real.
     */
    private final StoredCredentials dummyCredentials;

    public AuthService(Database database, PasswordHasher hasher, SessionManager sessions) {
        this.database = database;
        this.hasher = hasher;
        this.sessions = sessions;
        this.dummyCredentials = hasher.hash("a password nobody has");
    }

    public Session login(String loginId, String password) throws HrmException {
        String id = loginId == null ? "" : loginId.trim();
        // Refuse impossible input before doing any expensive hashing.
        if (id.isEmpty() || id.length() > MAX_LOGIN_ID_LENGTH
                || password == null || password.isEmpty() || password.length() > PasswordPolicy.MAX_LENGTH) {
            throw new InvalidCredentialsException(WRONG_LOGIN);
        }
        Optional<LoginRecord> found;
        try {
            found = database.read(da -> da.employees().findLogin(id));
        } catch (DataAccessException e) {
            throw ServiceErrors.unavailable(e);
        }
        StoredCredentials toCheck = found.map(LoginRecord::credentials).orElse(dummyCredentials);
        boolean passwordMatches = hasher.verify(password, toCheck);
        if (found.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException(WRONG_LOGIN);
        }
        Employee employee = found.get().employee();
        String displayName = employee.firstName() + " " + employee.lastName();
        String token = sessions.create(new ActiveSession(employee.id(), employee.role(), displayName,
                employee.mustChangePassword()));
        return new Session(token, employee.id(), displayName, employee.role(), employee.mustChangePassword());
    }

    /** Ends the session. An unknown or expired token is not an error. */
    public void logout(String token) {
        sessions.remove(token);
    }

    /**
     * Works even while the person is still on a temporary password, because this is the one thing they must do.
     * The other sessions of the same person are ended, so a stolen old session stops working.
     */
    public void changePassword(String token, String oldPassword, String newPassword) throws HrmException {
        ActiveSession caller = findSession(token);
        try {
            database.transaction(da -> {
                // Locking the row means two password changes at the same moment cannot overlap.
                Employee employee = da.employees().lockById(caller.employeeId())
                        .orElseThrow(() -> expiredSession());
                LoginRecord record = da.employees().findLogin(employee.icPassport())
                        .orElseThrow(() -> expiredSession());
                if (oldPassword == null || !hasher.verify(oldPassword, record.credentials())) {
                    throw new InvalidCredentialsException("Your current password is wrong.");
                }
                PasswordPolicy.check(newPassword, employee.icPassport());
                if (newPassword.equals(oldPassword)) {
                    throw new ValidationException("The new password must be different from the current one.");
                }
                da.employees().setCredentials(employee.id(), hasher.hash(newPassword), false);
                return null;
            });
        } catch (DataAccessException e) {
            throw ServiceErrors.unavailable(e);
        }
        sessions.markPasswordChanged(token);
        sessions.removeOthers(caller.employeeId(), token);
    }

    /**
     * Who is calling? For every service method except login, logout and changePassword.
     *
     * @throws InvalidCredentialsException if the token is unknown or expired
     * @throws PermissionDeniedException   if the person has not yet replaced their temporary password
     */
    public ActiveSession requireSession(String token) throws HrmException {
        ActiveSession session = findSession(token);
        if (session.mustChangePassword()) {
            throw new PermissionDeniedException(
                    "You must change your temporary password before doing anything else.");
        }
        return session;
    }

    /** As {@link #requireSession}, and the person must also have this role. */
    public ActiveSession requireRole(String token, Role role) throws HrmException {
        ActiveSession session = requireSession(token);
        if (session.role() != role) {
            throw new PermissionDeniedException(
                    role == Role.HR ? "Only HR staff can do this." : "Only employees can do this.");
        }
        return session;
    }

    private ActiveSession findSession(String token) throws InvalidCredentialsException {
        return sessions.find(token).orElseThrow(AuthService::expiredSession);
    }

    private static InvalidCredentialsException expiredSession() {
        return new InvalidCredentialsException("Your session has expired. Please log in again.");
    }
}
