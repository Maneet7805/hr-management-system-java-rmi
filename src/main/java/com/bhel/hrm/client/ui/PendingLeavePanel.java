package com.bhel.hrm.client.ui;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;

/**
 * "Leave requests": everything waiting for HR's decision, oldest first. A decision cannot be undone, so
 * approving and rejecting both ask first and name the person, the days and the dates.
 */
public final class PendingLeavePanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final Confirmer confirmer;
    private final PendingTableModel model = new PendingTableModel();
    private final JTable table = new JTable(model);
    private final JLabel emptyLabel = new JLabel("No leave requests are waiting for a decision.");
    private final JButton approveButton = new JButton("Approve");
    private final JButton rejectButton = new JButton("Reject");
    private final JButton refreshButton = new JButton("Refresh");
    private final JTextArea statusText = Ui.statusLine();

    public PendingLeavePanel(HRMService service, Navigator navigator, Session session, Confirmer confirmer) {
        super(new BorderLayout(8, 8));
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        this.confirmer = confirmer;
        buildLayout();
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        approveButton.addActionListener(e -> decide(true));
        rejectButton.addActionListener(e -> decide(false));
        refreshButton.addActionListener(e -> reload());
        selectionChanged();
        reload();
    }

    private void buildLayout() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getAccessibleContext().setAccessibleName("Leave requests waiting for a decision");
        approveButton.setMnemonic('A');
        rejectButton.setMnemonic('J');
        refreshButton.setMnemonic('R');
        emptyLabel.setVisible(false);

        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT));
        head.add(Ui.title("Leave requests"));
        head.add(refreshButton);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(approveButton);
        buttons.add(rejectButton);
        buttons.add(Ui.hint("Select a request first. A decision cannot be undone."));

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(Ui.leftAligned(emptyLabel));
        south.add(Ui.leftAligned(buttons));
        south.add(Ui.leftAligned(statusText));

        add(head, BorderLayout.NORTH);
        add(Ui.scrollableTable(table, 640, 240), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void selectionChanged() {
        boolean selected = table.getSelectedRow() >= 0;
        approveButton.setEnabled(selected);
        rejectButton.setEnabled(selected);
    }

    private void reload() {
        Tasks.<List<LeaveApplication>>run(() -> service.listPendingLeave(session.token()),
                applications -> {
                    model.setApplications(applications);
                    emptyLabel.setVisible(applications.isEmpty());
                    selectionChanged();
                },
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void decide(boolean approve) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        LeaveApplication application = model.applicationAt(table.convertRowIndexToModel(row));
        String verb = approve ? "Approve" : "Reject";
        String what = Display.days(application.days()) + " of " + application.leaveType().name() + " leave for "
                + application.employeeName() + " (" + Display.date(application.startDate()) + " to "
                + Display.date(application.endDate()) + ")";
        if (!confirmer.confirm(verb + " " + what + "?")) {
            return;
        }
        approveButton.setEnabled(false);
        rejectButton.setEnabled(false);
        Ui.showInfo(statusText, "Saving the decision...");
        Tasks.<LeaveApplication>run(() -> service.decideLeave(session.token(), application.id(), approve),
                decided -> {
                    Ui.showInfo(statusText, (approve ? "Approved " : "Rejected ") + application.employeeName()
                            + "'s " + application.leaveType().name() + " leave ("
                            + Display.date(application.startDate()) + " to " + Display.date(application.endDate()) + ").");
                    reload();
                },
                failure -> {
                    Ui.handleFailure(failure, navigator, statusText);
                    // "Already decided" or "not found" means the list on screen was out of date.
                    if (!ErrorMessages.isSessionExpired(failure)) {
                        reload();
                    }
                });
    }

    JTable table() {
        return table;
    }

    JLabel emptyLabel() {
        return emptyLabel;
    }

    JButton approveButton() {
        return approveButton;
    }

    JButton rejectButton() {
        return rejectButton;
    }

    JTextArea statusText() {
        return statusText;
    }
}
