package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.LeaveApplication;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Leave applications waiting for HR's decision. Every row is pending, so there is no status column. */
final class PendingTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Employee", "From", "To", "Days", "Type", "Reason", "Applied"};

    private List<LeaveApplication> applications = new ArrayList<>();

    void setApplications(List<LeaveApplication> newApplications) {
        applications = new ArrayList<>(newApplications);
        fireTableDataChanged();
    }

    LeaveApplication applicationAt(int row) {
        return applications.get(row);
    }

    @Override
    public int getRowCount() {
        return applications.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        LeaveApplication application = applications.get(row);
        return switch (column) {
            case 0 -> application.employeeName();
            case 1 -> Display.date(application.startDate());
            case 2 -> Display.date(application.endDate());
            case 3 -> Display.days(application.days());
            case 4 -> application.leaveType().name();
            case 5 -> Display.text(application.reason());
            default -> Display.dateTime(application.appliedAt());
        };
    }
}
