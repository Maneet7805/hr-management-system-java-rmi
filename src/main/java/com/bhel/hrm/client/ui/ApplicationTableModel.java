package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.LeaveApplication;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** A list of leave applications: dates, type, status, reason, and when it was applied for and decided. */
final class ApplicationTableModel extends AbstractTableModel {

    private static final String[] COLUMNS =
            {"From", "To", "Days", "Type", "Status", "Reason", "Applied", "Decided"};

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
            case 0 -> Display.date(application.startDate());
            case 1 -> Display.date(application.endDate());
            case 2 -> Display.days(application.days());
            case 3 -> application.leaveType().name();
            case 4 -> Display.status(application.status());
            case 5 -> Display.text(application.reason());
            case 6 -> Display.dateTime(application.appliedAt());
            default -> Display.dateTime(application.decidedAt());
        };
    }
}
