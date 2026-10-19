package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.LeaveBalance;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** The leave balances of one year, one row per leave type. */
final class BalanceTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Leave type", "Allowance", "Approved", "Pending", "Remaining"};

    private List<LeaveBalance> balances = new ArrayList<>();

    void setBalances(List<LeaveBalance> newBalances) {
        balances = new ArrayList<>(newBalances);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return balances.size();
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
        LeaveBalance balance = balances.get(row);
        return switch (column) {
            case 0 -> balance.leaveType().name();
            case 1 -> Display.allowance(balance.leaveType());
            case 2 -> String.valueOf(balance.approvedDays());
            case 3 -> String.valueOf(balance.pendingDays());
            default -> Display.remaining(balance);
        };
    }
}
