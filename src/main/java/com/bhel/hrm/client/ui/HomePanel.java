package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The screen after login. For now it only welcomes the person and offers Log out; the employee screens
 * (slice 7b) and the HR screens (slice 7c) replace the placeholder text.
 */
public final class HomePanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final JLabel welcomeLabel;
    private final JButton logOutButton = new JButton("Log out");

    public HomePanel(HRMService service, Navigator navigator, Session session) {
        super(new GridBagLayout());
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        this.welcomeLabel = new JLabel("Welcome, " + session.displayName() + " (" + describe(session.role()) + ")");

        logOutButton.setMnemonic('L');
        GridBagConstraints c = Ui.cell(0, 0);
        add(Ui.title("BHEL HR Management System"), c);
        add(welcomeLabel, Ui.cell(0, 1));
        add(Ui.hint("Your screens will appear here."), Ui.cell(0, 2));
        add(logOutButton, Ui.cell(0, 3));
        logOutButton.addActionListener(e -> logOut());
    }

    private static String describe(Role role) {
        return role == Role.HR ? "HR staff" : "Employee";
    }

    private void logOut() {
        logOutButton.setEnabled(false);
        // Whether or not the server answers, the user leaves this screen.
        Tasks.<Void>run(() -> {
            service.logout(session.token());
            return null;
        }, ignored -> navigator.showLogin(null), failure -> navigator.showLogin(null));
    }

    JLabel welcomeLabel() {
        return welcomeLabel;
    }

    JButton logOutButton() {
        return logOutButton;
    }
}
