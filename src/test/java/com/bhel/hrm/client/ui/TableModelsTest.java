package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableModelsTest {

    private static final LeaveType ANNUAL = new LeaveType(1L, "Annual", 14);
    private static final LeaveType UNPAID = new LeaveType(4L, "Unpaid", null);

    @Test
    void theFamilyTableShowsNameRelationshipAndDateOfBirth() {
        FamilyMember spouse = new FamilyMember(7L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));
        FamilyMember child = new FamilyMember(8L, "Cara Tan", "Child", null);
        FamilyTableModel model = new FamilyTableModel();

        model.setMembers(List.of(spouse, child));

        assertEquals(2, model.getRowCount());
        assertEquals(3, model.getColumnCount());
        assertEquals("Name", model.getColumnName(0));
        assertEquals("Date of birth", model.getColumnName(2));
        assertEquals("Bob Tan", model.getValueAt(0, 0));
        assertEquals("Spouse", model.getValueAt(0, 1));
        assertEquals("1990-05-17", model.getValueAt(0, 2));
        assertEquals("", model.getValueAt(1, 2), "no date of birth shows as empty");
        assertSame(child, model.memberAt(1));
        assertFalse(model.isCellEditable(0, 0), "editing happens in the form, not in the table");
    }

    @Test
    void theBalanceTableShowsAllowanceUsedAndRemaining() {
        BalanceTableModel model = new BalanceTableModel();

        model.setBalances(List.of(
                new LeaveBalance(ANNUAL, 2026, 3, 2),
                new LeaveBalance(UNPAID, 2026, 5, 0)));

        assertEquals(5, model.getColumnCount());
        assertEquals("Leave type", model.getColumnName(0));
        assertEquals("Remaining", model.getColumnName(4));
        assertEquals("Annual", model.getValueAt(0, 0));
        assertEquals("14", model.getValueAt(0, 1));
        assertEquals("3", model.getValueAt(0, 2));
        assertEquals("2", model.getValueAt(0, 3));
        assertEquals("9", model.getValueAt(0, 4));
        assertEquals("Unlimited", model.getValueAt(1, 1));
        assertEquals("Unlimited", model.getValueAt(1, 4));
    }

    @Test
    void theApplicationTableShowsDatesTypeStatusAndReason() {
        LeaveApplication pending = new LeaveApplication(11L, 1L, "Alice Tan", ANNUAL,
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3, "Family trip",
                LeaveStatus.PENDING, LocalDateTime.of(2026, 3, 1, 9, 0), null);
        LeaveApplication approved = new LeaveApplication(12L, 1L, "Alice Tan", UNPAID,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1), 1, null,
                LeaveStatus.APPROVED, LocalDateTime.of(2026, 3, 20, 10, 30), LocalDateTime.of(2026, 3, 21, 8, 0));
        ApplicationTableModel model = new ApplicationTableModel();

        model.setApplications(List.of(pending, approved));

        assertEquals(8, model.getColumnCount());
        assertEquals("From", model.getColumnName(0));
        assertEquals("2026-03-10", model.getValueAt(0, 0));
        assertEquals("2026-03-12", model.getValueAt(0, 1));
        assertEquals("3 days", model.getValueAt(0, 2));
        assertEquals("Annual", model.getValueAt(0, 3));
        assertEquals("Pending", model.getValueAt(0, 4));
        assertEquals("Family trip", model.getValueAt(0, 5));
        assertEquals("2026-03-01 09:00", model.getValueAt(0, 6));
        assertEquals("", model.getValueAt(0, 7), "not decided yet");
        assertEquals("1 day", model.getValueAt(1, 2));
        assertEquals("", model.getValueAt(1, 5), "no reason shows as empty");
        assertEquals("Approved", model.getValueAt(1, 4));
        assertEquals("2026-03-21 08:00", model.getValueAt(1, 7));
        assertSame(approved, model.applicationAt(1));
    }

    @Test
    void settingNewRowsReplacesTheOldOnes() {
        FamilyTableModel model = new FamilyTableModel();
        model.setMembers(List.of(new FamilyMember(1L, "A", "B", null), new FamilyMember(2L, "C", "D", null)));

        model.setMembers(List.of());

        assertEquals(0, model.getRowCount());
    }
}
