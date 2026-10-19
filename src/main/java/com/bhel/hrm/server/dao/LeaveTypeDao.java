package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.LeaveType;
import java.util.List;
import java.util.Optional;

/** Reads the leave types (Annual, Medical, ...). They are seed data and are not changed by the program. */
public interface LeaveTypeDao {

    /** All types in id order. */
    List<LeaveType> findAll();

    Optional<LeaveType> findById(long id);
}
