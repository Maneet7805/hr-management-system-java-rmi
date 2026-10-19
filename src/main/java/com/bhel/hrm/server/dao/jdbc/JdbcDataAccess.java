package com.bhel.hrm.server.dao.jdbc;

import com.bhel.hrm.server.dao.DataAccess;
import com.bhel.hrm.server.dao.EmployeeDao;
import com.bhel.hrm.server.dao.LeaveTypeDao;
import java.sql.Connection;

/** The MySQL DAOs, all sharing one connection and therefore one transaction. */
public final class JdbcDataAccess implements DataAccess {

    private final EmployeeDao employees;
    private final LeaveTypeDao leaveTypes;

    public JdbcDataAccess(Connection connection) {
        this.employees = new JdbcEmployeeDao(connection);
        this.leaveTypes = new JdbcLeaveTypeDao(connection);
    }

    @Override
    public EmployeeDao employees() {
        return employees;
    }

    @Override
    public LeaveTypeDao leaveTypes() {
        return leaveTypes;
    }
}
