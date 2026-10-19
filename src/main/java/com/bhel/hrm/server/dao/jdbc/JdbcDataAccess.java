package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.server.dao.DataAccess;
import com.bhel.hrm.server.dao.EmployeeDao;
import com.bhel.hrm.server.dao.FamilyMemberDao;
import com.bhel.hrm.server.dao.LeaveApplicationDao;
import com.bhel.hrm.server.dao.LeaveTypeDao;
import java.sql.Connection;

/** The MySQL DAOs, all sharing one connection and therefore one transaction. */
public final class JdbcDataAccess implements DataAccess {

    private final EmployeeDao employees;
    private final FamilyMemberDao family;
    private final LeaveTypeDao leaveTypes;
    private final LeaveApplicationDao leave;

    public JdbcDataAccess(Connection connection) {
        this.employees = new JdbcEmployeeDao(connection);
        this.family = new JdbcFamilyMemberDao(connection);
        this.leaveTypes = new JdbcLeaveTypeDao(connection);
        this.leave = new JdbcLeaveApplicationDao(connection);
    }

    @Override
    public EmployeeDao employees() {
        return employees;
    }

    @Override
    public FamilyMemberDao family() {
        return family;
    }

    @Override
    public LeaveTypeDao leaveTypes() {
        return leaveTypes;
    }

    @Override
    public LeaveApplicationDao leave() {
        return leave;
    }
}
