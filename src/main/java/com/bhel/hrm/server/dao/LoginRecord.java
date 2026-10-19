package com.bhel.hrm.server.dao;

import com.bhel.hrm.common.model.Employee;

/** An employee together with the stored password, found by IC/passport number when someone logs in. */
public record LoginRecord(Employee employee, StoredCredentials credentials) {
}
