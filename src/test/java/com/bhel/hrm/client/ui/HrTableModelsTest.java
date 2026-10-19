package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HrTableModelsTest {

    @Test
    void theEmployeeTableShowsNameIdentityRoleAndContactDetails() {
        Employee alice = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
                "012-3456789", "alice@bhel.example", "1 Jalan Satu", false);
        Employee hana = new Employee(9L, "Hana", "Lim", "HR900001", Role.HR, null, null, null, false);
        EmployeeTableModel model = new EmployeeTableModel();

        model.setEmployees(List.of(alice, hana));

        assertEquals(2, model.getRowCount());
        assertEquals(5, model.getColumnCount());
        assertEquals("Name", model.getColumnName(0));
        assertEquals("IC/passport number", model.getColumnName(1));
        assertEquals("Alice Tan", model.getValueAt(0, 0));
        assertEquals("AB123456", model.getValueAt(0, 1));
        assertEquals("Employee", model.getValueAt(0, 2));
        assertEquals("012-3456789", model.getValueAt(0, 3));
        assertEquals("alice@bhel.example", model.getValueAt(0, 4));
        assertEquals("HR staff", model.getValueAt(1, 2));
        assertEquals("", model.getValueAt(1, 3), "no phone shows as empty");
        assertSame(hana, model.employeeAt(1));
        assertFalse(model.isCellEditable(0, 0));
    }

    @Test
    void thePendingTableNamesTheEmployeeAndLeavesOutStatusBecauseEveryRowIsPending() {
        LeaveApplication application = new LeaveApplication(11L, 7L, "Alice Tan", new LeaveType(1L, "Annual", 14),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3, "Family trip",
                LeaveStatus.PENDING, LocalDateTime.of(2026, 3, 1, 9, 0), null);
        PendingTableModel model = new PendingTableModel();

        model.setApplications(List.of(application));

        assertEquals(7, model.getColumnCount());
        assertEquals("Employee", model.getColumnName(0));
        assertEquals("Applied", model.getColumnName(6));
        assertEquals("Alice Tan", model.getValueAt(0, 0));
        assertEquals("2026-03-10", model.getValueAt(0, 1));
        assertEquals("2026-03-12", model.getValueAt(0, 2));
        assertEquals("3 days", model.getValueAt(0, 3));
        assertEquals("Annual", model.getValueAt(0, 4));
        assertEquals("Family trip", model.getValueAt(0, 5));
        assertEquals("2026-03-01 09:00", model.getValueAt(0, 6));
        assertSame(application, model.applicationAt(0));
    }
}
