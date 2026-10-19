package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * "Leave": the balance of each leave type for a year, a form to apply for leave, and the person's own
 * applications with their status. Everything the server refuses is shown in its own words.
 */
public final class LeavePanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final BalanceTableModel balanceModel = new BalanceTableModel();
    private final ApplicationTableModel applicationModel = new ApplicationTableModel();
    private final JTable balancesTable = new JTable(balanceModel);
    private final JTable applicationsTable = new JTable(applicationModel);
    private final JTextField yearField = new JTextField(String.valueOf(LocalDate.now().getYear()), 6);
    private final JButton refreshButton = new JButton("Refresh");
    private final JComboBox<LeaveType> typeCombo = new JComboBox<>();
    private final JTextField startField = new JTextField(10);
    private final JTextField endField = new JTextField(10);
    private final JTextField reasonField = new JTextField(30);
    private final JButton applyButton = new JButton("Apply for leave");
    private final JTextArea statusText = Ui.statusLine();

    public LeavePanel(HRMService service, Navigator navigator, Session session) {
        super(new BorderLayout(8, 8));
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        buildLayout();
        refreshButton.addActionListener(e -> loadBalances());
        yearField.addActionListener(e -> loadBalances());
        applyButton.addActionListener(e -> apply());
        loadTypes();
        loadBalances();
        loadApplications();
    }

    private void buildLayout() {
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                return super.getListCellRendererComponent(list, value instanceof LeaveType type ? type.name() : "",
                        index, selected, focused);
            }
        });
        balancesTable.getAccessibleContext().setAccessibleName("Leave balances");
        applicationsTable.getAccessibleContext().setAccessibleName("My leave applications");
        yearField.getAccessibleContext().setAccessibleName("Year");
        typeCombo.getAccessibleContext().setAccessibleName("Leave type");
        startField.getAccessibleContext().setAccessibleName("Start date");
        endField.getAccessibleContext().setAccessibleName("End date");
        reasonField.getAccessibleContext().setAccessibleName("Reason");
        refreshButton.setMnemonic('R');
        applyButton.setMnemonic('A');

        JPanel yearRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        yearRow.add(Ui.title("My leave"));
        yearRow.add(label("Year:", yearField, 'Y'));
        yearRow.add(yearField);
        yearRow.add(refreshButton);
        JScrollPane balancesScroll = Ui.scrollableTable(balancesTable, 560, 110);

        JPanel form = new JPanel(new GridBagLayout());
        form.add(label("Leave type:", typeCombo, 'T'), Ui.cell(0, 0));
        form.add(typeCombo, Ui.cell(1, 0));
        form.add(label("From:", startField, 'F'), Ui.cell(2, 0));
        form.add(startField, Ui.cell(3, 0));
        form.add(label("To:", endField, 'O'), Ui.cell(4, 0));
        form.add(endField, Ui.cell(5, 0));
        GridBagConstraints c = Ui.cell(6, 0);
        form.add(Ui.hint("yyyy-MM-dd"), c);
        form.add(label("Reason:", reasonField, 'E'), Ui.cell(0, 1));
        c = Ui.cell(1, 1);
        c.gridwidth = 4;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(reasonField, c);
        form.add(applyButton, Ui.cell(5, 1));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(Ui.leftAligned(yearRow));
        top.add(Ui.leftAligned(balancesScroll));
        top.add(Ui.leftAligned(Ui.hint("Pending days already count against your balance.")));
        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formRow.add(form);
        top.add(Ui.leftAligned(formRow));
        top.add(Ui.leftAligned(statusText));

        JScrollPane applicationsScroll = Ui.scrollableTable(applicationsTable, 560, 160);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(new JLabel("My applications"), BorderLayout.NORTH);
        bottom.add(applicationsScroll, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(bottom, BorderLayout.CENTER);
    }

    private static JLabel label(String text, java.awt.Component field, char mnemonic) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        return label;
    }

    private void loadTypes() {
        Tasks.<List<LeaveType>>run(() -> service.getLeaveTypes(session.token()),
                types -> {
                    typeCombo.removeAllItems();
                    types.forEach(typeCombo::addItem);
                },
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void loadBalances() {
        int year;
        try {
            year = Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            Ui.showError(statusText, "Year must be a number such as " + LocalDate.now().getYear() + ".");
            return;
        }
        Tasks.<List<LeaveBalance>>run(() -> service.getMyLeaveBalances(session.token(), year),
                balanceModel::setBalances,
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void loadApplications() {
        Tasks.<List<LeaveApplication>>run(() -> service.getMyLeaveApplications(session.token()),
                applicationModel::setApplications,
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void apply() {
        LeaveType type = (LeaveType) typeCombo.getSelectedItem();
        if (type == null) {
            Ui.showError(statusText, "Choose a leave type.");
            return;
        }
        LocalDate start;
        LocalDate end;
        try {
            start = DateInput.required(startField.getText(), "Start date");
            end = DateInput.required(endField.getText(), "End date");
        } catch (IllegalArgumentException problem) {
            Ui.showError(statusText, problem.getMessage());
            return;
        }
        String typed = reasonField.getText().trim();
        String reason = typed.isEmpty() ? null : typed;
        applyButton.setEnabled(false);
        Ui.showInfo(statusText, "Sending your application...");
        Tasks.<LeaveApplication>run(() -> service.applyForLeave(session.token(), type.id(), start, end, reason),
                saved -> {
                    applyButton.setEnabled(true);
                    startField.setText("");
                    endField.setText("");
                    reasonField.setText("");
                    Ui.showInfo(statusText, "Application sent: " + Display.days(saved.days()) + " of "
                            + saved.leaveType().name() + " leave, waiting for HR's decision.");
                    loadBalances();
                    loadApplications();
                },
                failure -> {
                    applyButton.setEnabled(true);   // what was typed stays, so it can be corrected
                    Ui.handleFailure(failure, navigator, statusText);
                });
    }

    JTextField yearField() {
        return yearField;
    }

    JButton refreshButton() {
        return refreshButton;
    }

    JComboBox<LeaveType> typeCombo() {
        return typeCombo;
    }

    JTextField startField() {
        return startField;
    }

    JTextField endField() {
        return endField;
    }

    JTextField reasonField() {
        return reasonField;
    }

    JButton applyButton() {
        return applyButton;
    }

    JTable balancesTable() {
        return balancesTable;
    }

    JTable applicationsTable() {
        return applicationsTable;
    }

    JTextArea statusText() {
        return statusText;
    }
}
