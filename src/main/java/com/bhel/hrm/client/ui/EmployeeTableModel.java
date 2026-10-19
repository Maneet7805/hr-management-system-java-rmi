package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.model.Employee;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/** Everyone in the company, as HR sees them. Read-only. */
final class EmployeeTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Name", "IC/passport number", "Role", "Phone", "Email"};

    private List<Employee> employees = new ArrayList<>();

    void setEmployees(List<Employee> newEmployees) {
        employees = new ArrayList<>(newEmployees);
        fireTableDataChanged();
    }

    Employee employeeAt(int row) {
        return employees.get(row);
    }

    @Override
    public int getRowCount() {
        return employees.size();
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
        Employee employee = employees.get(row);
        return switch (column) {
            case 0 -> employee.firstName() + " " + employee.lastName();
            case 1 -> employee.icPassport();
            case 2 -> Display.role(employee.role());
            case 3 -> Display.text(employee.phone());
            default -> Display.text(employee.email());
        };
    }
}
