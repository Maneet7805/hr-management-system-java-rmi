package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.RegistrationResult;
import com.bhel.hrm.common.model.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * HR's list of everyone, and the form to register a new employee. A new employee gets a one-time temporary
 * password, shown here in its own box until HR presses Done, starts another registration, or leaves the tab.
 */
public final class EmployeesPanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final ClipboardWriter clipboard;
    private final EmployeeTableModel model = new EmployeeTableModel();
    private final JTable table = new JTable(model);
    private final JButton refreshButton = new JButton("Refresh");
    private final JTextField firstNameField = new JTextField(14);
    private final JTextField lastNameField = new JTextField(14);
    private final JTextField icField = new JTextField(16);
    private final JButton registerButton = new JButton("Register employee");
    private final JPanel passwordPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JTextField passwordField = new JTextField(16);
    private final JButton copyButton = new JButton("Copy");
    private final JButton doneButton = new JButton("Done");
    private final JTextArea statusText = Ui.statusLine();

    public EmployeesPanel(HRMService service, Navigator navigator, Session session, ClipboardWriter clipboard) {
        super(new BorderLayout(8, 8));
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        this.clipboard = clipboard;
        buildLayout();
        refreshButton.addActionListener(e -> reload());
        registerButton.addActionListener(e -> register());
        icField.addActionListener(e -> register());
        copyButton.addActionListener(e -> clipboard.copy(passwordField.getText()));
        doneButton.addActionListener(e -> hidePassword());
        // Choosing another tab hides this panel: the password must not wait here for the next person to see.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent event) {
                hidePassword();
            }
        });
        reload();
    }

    private void buildLayout() {
        table.getAccessibleContext().setAccessibleName("Employees");
        firstNameField.getAccessibleContext().setAccessibleName("First name");
        lastNameField.getAccessibleContext().setAccessibleName("Last name");
        icField.getAccessibleContext().setAccessibleName("IC or passport number");
        passwordField.setEditable(false);
        passwordField.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.BOLD, 14));
        passwordField.getAccessibleContext().setAccessibleName("Temporary password");
        refreshButton.setMnemonic('R');
        registerButton.setMnemonic('G');
        copyButton.setMnemonic('C');
        doneButton.setMnemonic('D');

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT));
        head.add(Ui.title("Employees"));
        head.add(refreshButton);

        JPanel form = new JPanel(new GridBagLayout());
        form.add(label("First name:", firstNameField, 'F'), Ui.cell(0, 0));
        form.add(firstNameField, Ui.cell(1, 0));
        form.add(label("Last name:", lastNameField, 'L'), Ui.cell(2, 0));
        form.add(lastNameField, Ui.cell(3, 0));
        form.add(label("IC/passport number:", icField, 'I'), Ui.cell(0, 1));
        form.add(icField, Ui.cell(1, 1));
        form.add(registerButton, Ui.cell(2, 1));
        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formRow.add(form);

        passwordPanel.add(new JLabel("Temporary password (shown once):"));
        passwordPanel.add(passwordField);
        passwordPanel.add(copyButton);
        passwordPanel.add(doneButton);
        passwordPanel.add(Ui.hint("Give it to the employee in person. They must choose their own at first login."));
        passwordPanel.setVisible(false);

        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));
        south.add(Ui.leftAligned(formRow));
        south.add(Ui.leftAligned(passwordPanel));
        south.add(Ui.leftAligned(statusText));

        add(head, BorderLayout.NORTH);
        add(Ui.scrollableTable(table, 640, 220), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private static JLabel label(String text, JTextField field, char mnemonic) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        return label;
    }

    private void reload() {
        Tasks.<List<Employee>>run(() -> service.listEmployees(session.token()),
                model::setEmployees,
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void register() {
        String first = firstNameField.getText().trim();
        String last = lastNameField.getText().trim();
        String ic = icField.getText().trim();
        if (first.isEmpty() || last.isEmpty() || ic.isEmpty()) {
            Ui.showError(statusText, "Enter the first name, last name and IC/passport number.");
            return;
        }
        hidePassword();
        registerButton.setEnabled(false);
        Ui.showInfo(statusText, "Registering...");
        Tasks.<RegistrationResult>run(() -> service.registerEmployee(session.token(), first, last, ic),
                result -> {
                    registerButton.setEnabled(true);
                    firstNameField.setText("");
                    lastNameField.setText("");
                    icField.setText("");
                    passwordField.setText(result.temporaryPassword());
                    passwordPanel.setVisible(true);
                    // The password is only in its own box, never in this message.
                    Ui.showInfo(statusText, "Registered " + result.employee().firstName() + " "
                            + result.employee().lastName() + ". Give them the temporary password below.");
                    reload();
                },
                failure -> {
                    registerButton.setEnabled(true);   // what was typed stays, so it can be corrected
                    Ui.handleFailure(failure, navigator, statusText);
                });
    }

    private void hidePassword() {
        passwordField.setText("");
        passwordPanel.setVisible(false);
    }

    JTable table() {
        return table;
    }

    JTextField firstNameField() {
        return firstNameField;
    }

    JTextField lastNameField() {
        return lastNameField;
    }

    JTextField icField() {
        return icField;
    }

    JButton registerButton() {
        return registerButton;
    }

    JPanel passwordPanel() {
        return passwordPanel;
    }

    JTextField passwordField() {
        return passwordField;
    }

    JButton copyButton() {
        return copyButton;
    }

    JButton doneButton() {
        return doneButton;
    }

    JTextArea statusText() {
        return statusText;
    }
}
