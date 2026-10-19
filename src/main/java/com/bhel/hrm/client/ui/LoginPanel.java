package com.bhel.hrm.client.ui;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Session;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/** The first screen: ID and password. Enter in either field logs in. */
public final class LoginPanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final JTextField idField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JButton loginButton = new JButton("Log in");
    private final JTextArea statusText = Ui.statusLine();

    public LoginPanel(HRMService service, Navigator navigator) {
        super(new GridBagLayout());
        this.service = service;
        this.navigator = navigator;
        buildLayout();
        idField.addActionListener(e -> attemptLogin());
        passwordField.addActionListener(e -> attemptLogin());
        loginButton.addActionListener(e -> attemptLogin());
    }

    private void buildLayout() {
        JLabel idLabel = new JLabel("IC/passport number:");
        idLabel.setLabelFor(idField);
        idLabel.setDisplayedMnemonic('I');
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setLabelFor(passwordField);
        passwordLabel.setDisplayedMnemonic('P');
        idField.getAccessibleContext().setAccessibleName("IC or passport number");
        passwordField.getAccessibleContext().setAccessibleName("Password");
        loginButton.setMnemonic('L');

        GridBagConstraints c = Ui.cell(0, 0);
        c.gridwidth = 2;
        add(Ui.title("BHEL HR Management System"), c);
        c = Ui.cell(0, 1);
        c.gridwidth = 2;
        add(Ui.hint("Log in with your IC/passport number and your password."), c);
        add(idLabel, Ui.cell(0, 2));
        c = Ui.cell(1, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        add(idField, c);
        add(passwordLabel, Ui.cell(0, 3));
        c = Ui.cell(1, 3);
        c.fill = GridBagConstraints.HORIZONTAL;
        add(passwordField, c);
        c = Ui.cell(1, 4);
        add(loginButton, c);
        c = Ui.cell(0, 5);
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(statusText, c);
    }

    private void attemptLogin() {
        String id = idField.getText().trim();
        char[] typed = passwordField.getPassword();
        if (id.isEmpty() || typed.length == 0) {
            Ui.showError(statusText, "Enter your ID and password.");
            return;
        }
        String password = new String(typed);
        Arrays.fill(typed, ' ');
        Ui.showInfo(statusText, "Logging in...");
        setBusy(true);
        Tasks.<Session>run(() -> service.login(id, password),
                session -> {
                    finishAttempt();
                    Ui.showInfo(statusText, "");
                    if (session.mustChangePassword()) {
                        navigator.showChangePassword(session);
                    } else {
                        navigator.showHome(session);
                    }
                },
                failure -> {
                    finishAttempt();
                    Ui.showError(statusText, ErrorMessages.forUser(failure));
                    passwordField.requestFocusInWindow();
                });
    }

    /** Never leaves a typed password on the screen after it has been used. */
    private void finishAttempt() {
        passwordField.setText("");
        setBusy(false);
    }

    private void setBusy(boolean busy) {
        idField.setEnabled(!busy);
        passwordField.setEnabled(!busy);
        loginButton.setEnabled(!busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    /** For messages from elsewhere, such as "Your session has expired". */
    public void showMessage(String message) {
        Ui.showError(statusText, message);
    }

    JTextField idField() {
        return idField;
    }

    JPasswordField passwordField() {
        return passwordField;
    }

    JButton loginButton() {
        return loginButton;
    }

    JTextArea statusText() {
        return statusText;
    }
}
