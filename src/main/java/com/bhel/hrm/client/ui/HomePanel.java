package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * The screen after login: who you are, buttons to change your password or log out, and the tabs for what
 * you can do. Everyone has "My details" and "Family". Employees also have "Leave" (first, because it is what
 * they come for). HR staff have "Employees", "Leave requests" and "Yearly report" first instead, because they do
 * not apply for leave themselves.
 */
public final class HomePanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final JLabel welcomeLabel;
    private final JButton changePasswordButton = new JButton("Change password");
    private final JButton logOutButton = new JButton("Log out");
    private final JTabbedPane tabs = new JTabbedPane();

    public HomePanel(HRMService service, Navigator navigator, Session session) {
        super(new BorderLayout(8, 8));
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        this.welcomeLabel = new JLabel("Welcome, " + session.displayName() + " (" + Display.role(session.role()) + ")");

        changePasswordButton.setMnemonic('C');
        logOutButton.setMnemonic('L');
        welcomeLabel.setFont(welcomeLabel.getFont().deriveFont(java.awt.Font.BOLD));
        JPanel header = new JPanel(new BorderLayout());
        header.add(welcomeLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(changePasswordButton);
        buttons.add(logOutButton);
        header.add(buttons, BorderLayout.EAST);

        if (session.role() == Role.HR) {
            tabs.addTab("Employees", new EmployeesPanel(service, navigator, session, ClipboardWriter.system()));
            tabs.addTab("Leave requests", new PendingLeavePanel(service, navigator, session, Confirmer.dialog(this)));
            tabs.addTab("Yearly report", new ReportPanel(service, navigator, session, FileSaver.dialog(this)));
        } else {
            tabs.addTab("Leave", new LeavePanel(service, navigator, session));
        }
        tabs.addTab("My details", new ProfilePanel(service, navigator, session));
        tabs.addTab("Family", new FamilyPanel(service, navigator, session, Confirmer.dialog(this)));

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 12, 8, 12));

        changePasswordButton.addActionListener(e -> navigator.showChangePassword(session));
        logOutButton.addActionListener(e -> logOut());
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

    JButton changePasswordButton() {
        return changePasswordButton;
    }

    JButton logOutButton() {
        return logOutButton;
    }

    JTabbedPane tabs() {
        return tabs;
    }
}
