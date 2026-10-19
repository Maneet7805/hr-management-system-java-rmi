package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Session;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * "My details": the name and IC/passport number (only HR can change those) and the phone, email and address
 * the person keeps up to date themselves.
 */
public final class ProfilePanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final JLabel nameLabel = new JLabel(" ");
    private final JLabel icLabel = new JLabel(" ");
    private final JLabel roleLabel = new JLabel(" ");
    private final JTextField phoneField = new JTextField(24);
    private final JTextField emailField = new JTextField(24);
    private final JTextField addressField = new JTextField(36);
    private final JButton saveButton = new JButton("Save");
    private final JTextArea statusText = Ui.statusLine();

    public ProfilePanel(HRMService service, Navigator navigator, Session session) {
        super(new GridBagLayout());
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        buildLayout();
        saveButton.addActionListener(e -> save());
        setEditable(false);   // nothing to edit until the details have arrived
        load();
    }

    private void buildLayout() {
        phoneField.getAccessibleContext().setAccessibleName("Phone");
        emailField.getAccessibleContext().setAccessibleName("Email");
        addressField.getAccessibleContext().setAccessibleName("Address");
        saveButton.setMnemonic('S');

        GridBagConstraints c = Ui.cell(0, 0);
        c.gridwidth = 2;
        add(Ui.title("My details"), c);
        addReadOnlyRow(1, "Name:", nameLabel);
        addReadOnlyRow(2, "IC/passport number:", icLabel);
        addReadOnlyRow(3, "Role:", roleLabel);
        c = Ui.cell(0, 4);
        c.gridwidth = 2;
        add(Ui.hint("Only HR can change your name or IC/passport number."), c);
        addFieldRow(5, "Phone:", phoneField, 'P');
        addFieldRow(6, "Email:", emailField, 'E');
        addFieldRow(7, "Address:", addressField, 'A');
        add(saveButton, Ui.cell(1, 8));
        c = Ui.cell(0, 9);
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(statusText, c);
    }

    private void addReadOnlyRow(int row, String text, JLabel value) {
        add(new JLabel(text), Ui.cell(0, row));
        add(value, Ui.cell(1, row));
    }

    private void addFieldRow(int row, String text, JTextField field, char mnemonic) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        add(label, Ui.cell(0, row));
        GridBagConstraints c = Ui.cell(1, row);
        c.fill = GridBagConstraints.HORIZONTAL;
        add(field, c);
    }

    private void load() {
        Tasks.<Employee>run(() -> service.getMyProfile(session.token()),
                employee -> {
                    show(employee);
                    setEditable(true);
                },
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void save() {
        setEditable(false);
        Ui.showInfo(statusText, "Saving...");
        Tasks.<Employee>run(() -> service.updateMyContact(session.token(), phoneField.getText(),
                        emailField.getText(), addressField.getText()),
                employee -> {
                    show(employee);
                    setEditable(true);
                    Ui.showInfo(statusText, "Saved.");
                },
                failure -> {
                    setEditable(true);   // what was typed stays, so the person can correct it
                    Ui.handleFailure(failure, navigator, statusText);
                });
    }

    private void show(Employee employee) {
        nameLabel.setText(employee.firstName() + " " + employee.lastName());
        icLabel.setText(employee.icPassport());
        roleLabel.setText(Display.role(employee.role()));
        phoneField.setText(Display.text(employee.phone()));
        emailField.setText(Display.text(employee.email()));
        addressField.setText(Display.text(employee.address()));
    }

    private void setEditable(boolean editable) {
        phoneField.setEnabled(editable);
        emailField.setEnabled(editable);
        addressField.setEnabled(editable);
        saveButton.setEnabled(editable);
    }

    JLabel nameLabel() {
        return nameLabel;
    }

    JLabel icLabel() {
        return icLabel;
    }

    JLabel roleLabel() {
        return roleLabel;
    }

    JTextField phoneField() {
        return phoneField;
    }

    JTextField emailField() {
        return emailField;
    }

    JTextField addressField() {
        return addressField;
    }

    JButton saveButton() {
        return saveButton;
    }

    JTextArea statusText() {
        return statusText;
    }
}
