package com.bhel.hrm.server.dao;

/** All the DAOs, bound to the same database connection, so they share one transaction. */
public interface DataAccess {

    EmployeeDao employees();

    LeaveTypeDao leaveTypes();
}
