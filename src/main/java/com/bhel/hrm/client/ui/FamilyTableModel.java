package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.FamilyMember;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** The rows of the family table. Read-only: editing happens in the form beside it. */
final class FamilyTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Name", "Relationship", "Date of birth"};

    private List<FamilyMember> members = new ArrayList<>();

    void setMembers(List<FamilyMember> newMembers) {
        members = new ArrayList<>(newMembers);
        fireTableDataChanged();
    }

    FamilyMember memberAt(int row) {
        return members.get(row);
    }

    @Override
    public int getRowCount() {
        return members.size();
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
        FamilyMember member = members.get(row);
        return switch (column) {
            case 0 -> member.name();
            case 1 -> member.relationship();
            default -> Display.date(member.dateOfBirth());
        };
    }
}
