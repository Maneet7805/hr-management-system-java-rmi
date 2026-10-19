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

/**
 * Choose a new password. Shown straight after login to someone on a temporary password, who cannot reach
 * anything else until they have done this (the server enforces it too).
 */
public final class ChangePasswordPanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final JLabel noticeLabel = new JLabel();
    private final JPasswordField currentField = new JPasswordField(18);
    private final JPasswordField newField = new JPasswordField(18);
    private final JPasswordField confirmField = new JPasswordField(18);
    private final JButton changeButton = new JButton("Change password");
    private final JButton logOutButton = new JButton("Log out");
    private final JTextArea statusText = Ui.statusLine();

    public ChangePasswordPanel(HRMService service, Navigator navigator, Session session) {
        super(new GridBagLayout());
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        noticeLabel.setText(session.mustChangePassword()
                ? "<html>You are using a temporary password.<br>Choose your own password to continue.</html>"
                : "Choose a new password.");
        buildLayout();
        changeButton.addActionListener(e -> attemptChange());
        confirmField.addActionListener(e -> attemptChange());
        logOutButton.addActionListener(e -> logOut());
    }

    private void buildLayout() {
        currentField.getAccessibleContext().setAccessibleName("Current password");
        newField.getAccessibleContext().setAccessibleName("New password");
        confirmField.getAccessibleContext().setAccessibleName("New password again");
        changeButton.setMnemonic('C');
        logOutButton.setMnemonic('O');

        GridBagConstraints c = Ui.cell(0, 0);
        c.gridwidth = 2;
        add(Ui.title("Choose a new password"), c);
        c = Ui.cell(0, 1);
        c.gridwidth = 2;
        add(noticeLabel, c);
        c = Ui.cell(0, 2);
        c.gridwidth = 2;
        add(Ui.hint("At least 8 characters. A short sentence works well."), c);
        addRow(3, "Current password:", currentField, 'U');
        addRow(4, "New password:", newField, 'N');
        addRow(5, "New password again:", confirmField, 'A');
        c = Ui.cell(1, 6);
        add(changeButton, c);
        c = Ui.cell(0, 7);
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(statusText, c);
        c = Ui.cell(0, 8);
        c.gridwidth = 2;
        add(logOutButton, c);
    }

    private void addRow(int row, String text, JPasswordField field, char mnemonic) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        add(label, Ui.cell(0, row));
        GridBagConstraints c = Ui.cell(1, row);
        c.fill = GridBagConstraints.HORIZONTAL;
        add(field, c);
    }

    private void attemptChange() {
        String current = new String(currentField.getPassword());
        String next = new String(newField.getPassword());
        String confirmation = new String(confirmField.getPassword());
        if (current.isEmpty() || next.isEmpty() || confirmation.isEmpty()) {
            Ui.showError(statusText, "Fill in all three fields.");
            return;
        }
        if (!next.equals(confirmation)) {
            Ui.showError(statusText, "The new password and its confirmation are not the same.");
            return;
        }
        Ui.showInfo(statusText, "Saving...");
        setBusy(true);
        Tasks.<Void>run(() -> {
            service.changePassword(session.token(), current, next);
            return null;
        }, ignored -> {
            clearFields();
            setBusy(false);
            navigator.showHome(new Session(session.token(), session.employeeId(), session.displayName(),
                    session.role(), false));
        }, failure -> {
            clearFields();
            setBusy(false);
            if (ErrorMessages.isSessionExpired(failure)) {
                navigator.showLogin(ErrorMessages.SESSION_EXPIRED);
            } else {
                Ui.showError(statusText, ErrorMessages.forUser(failure));
                currentField.requestFocusInWindow();
            }
        });
    }

    private void logOut() {
        setBusy(true);
        // Whether or not the server answers, the user leaves this screen.
        Tasks.<Void>run(() -> {
            service.logout(session.token());
            return null;
        }, ignored -> navigator.showLogin(null), failure -> navigator.showLogin(null));
    }

    /** Never leaves typed passwords on the screen after they have been used. */
    private void clearFields() {
        for (JPasswordField field : new JPasswordField[] {currentField, newField, confirmField}) {
            char[] typed = field.getPassword();
            Arrays.fill(typed, ' ');
            field.setText("");
        }
    }

    private void setBusy(boolean busy) {
        currentField.setEnabled(!busy);
        newField.setEnabled(!busy);
        confirmField.setEnabled(!busy);
        changeButton.setEnabled(!busy);
        logOutButton.setEnabled(!busy);
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    JLabel noticeLabel() {
        return noticeLabel;
    }

    JPasswordField currentField() {
        return currentField;
    }

    JPasswordField newField() {
        return newField;
    }

    JPasswordField confirmField() {
        return confirmField;
    }

    JButton changeButton() {
        return changeButton;
    }

    JButton logOutButton() {
        return logOutButton;
    }

    JTextArea statusText() {
        return statusText;
    }
}
