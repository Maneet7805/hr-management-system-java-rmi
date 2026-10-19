package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * "Family": the person's family members in a table, and a form to add one, or to change or remove the one
 * selected. Removing asks first.
 */
public final class FamilyPanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final Confirmer confirmer;
    private final FamilyTableModel model = new FamilyTableModel();
    private final JTable table = new JTable(model);
    private final JTextField nameField = new JTextField(20);
    private final JTextField relationshipField = new JTextField(14);
    private final JTextField dobField = new JTextField(10);
    private final JButton addButton = new JButton("Add");
    private final JButton updateButton = new JButton("Update selected");
    private final JButton removeButton = new JButton("Remove selected");
    private final JTextArea statusText = Ui.statusLine();

    public FamilyPanel(HRMService service, Navigator navigator, Session session, Confirmer confirmer) {
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
        addButton.addActionListener(e -> add());
        updateButton.addActionListener(e -> update());
        removeButton.addActionListener(e -> remove());
        selectionChanged();
        reload();
    }

    private void buildLayout() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getAccessibleContext().setAccessibleName("Family members");
        nameField.getAccessibleContext().setAccessibleName("Family member name");
        relationshipField.getAccessibleContext().setAccessibleName("Relationship");
        dobField.getAccessibleContext().setAccessibleName("Date of birth");
        addButton.setMnemonic('A');
        updateButton.setMnemonic('U');
        removeButton.setMnemonic('R');

        JScrollPane scroll = Ui.scrollableTable(table, 560, 160);

        JPanel form = new JPanel(new GridBagLayout());
        form.add(label("Name:", nameField, 'N'), Ui.cell(0, 0));
        form.add(nameField, Ui.cell(1, 0));
        form.add(label("Relationship:", relationshipField, 'L'), Ui.cell(2, 0));
        form.add(relationshipField, Ui.cell(3, 0));
        form.add(label("Date of birth:", dobField, 'D'), Ui.cell(0, 1));
        JPanel dobRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dobRow.add(dobField);
        dobRow.add(Ui.hint("yyyy-MM-dd, may be left empty"));
        GridBagConstraints c = Ui.cell(1, 1);
        c.gridwidth = 3;
        form.add(dobRow, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(addButton);
        buttons.add(updateButton);
        buttons.add(removeButton);

        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formRow.add(form);
        JPanel south = new JPanel(new BorderLayout());
        south.add(formRow, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);
        south.add(statusText, BorderLayout.SOUTH);

        add(Ui.title("Family"), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private static JLabel label(String text, JTextField field, char mnemonic) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        return label;
    }

    private void selectionChanged() {
        int row = table.getSelectedRow();
        boolean selected = row >= 0;
        updateButton.setEnabled(selected);
        removeButton.setEnabled(selected);
        if (selected) {
            FamilyMember member = model.memberAt(table.convertRowIndexToModel(row));
            nameField.setText(member.name());
            relationshipField.setText(member.relationship());
            dobField.setText(Display.date(member.dateOfBirth()));
        }
    }

    private void reload() {
        Tasks.<List<FamilyMember>>run(() -> service.getMyFamily(session.token()),
                members -> {
                    model.setMembers(members);
                    selectionChanged();
                },
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void add() {
        LocalDate dateOfBirth;
        try {
            dateOfBirth = DateInput.optional(dobField.getText(), "Date of birth");
        } catch (IllegalArgumentException problem) {
            Ui.showError(statusText, problem.getMessage());
            return;
        }
        String name = nameField.getText().trim();
        String relationship = relationshipField.getText().trim();
        Ui.showInfo(statusText, "Adding...");
        Tasks.<FamilyMember>run(() -> service.addFamilyMember(session.token(), name, relationship, dateOfBirth),
                added -> {
                    clearForm();
                    Ui.showInfo(statusText, "Added " + added.name() + ".");
                    reload();
                },
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void update() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        FamilyMember edited;
        try {
            long id = model.memberAt(table.convertRowIndexToModel(row)).id();
            edited = new FamilyMember(id, nameField.getText().trim(), relationshipField.getText().trim(),
                    DateInput.optional(dobField.getText(), "Date of birth"));
        } catch (IllegalArgumentException problem) {
            // Either the date is not a real date, or the shared data class refused a blank name or relationship.
            Ui.showError(statusText, problem.getMessage());
            return;
        }
        Ui.showInfo(statusText, "Saving...");
        Tasks.<FamilyMember>run(() -> service.updateFamilyMember(session.token(), edited),
                saved -> {
                    clearForm();
                    Ui.showInfo(statusText, "Updated.");
                    reload();
                },
                failure -> handleChangeFailure(failure));
    }

    private void remove() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        FamilyMember member = model.memberAt(table.convertRowIndexToModel(row));
        if (!confirmer.confirm("Remove " + member.name() + " from your family details?")) {
            return;
        }
        Ui.showInfo(statusText, "Removing...");
        Tasks.<Void>run(() -> {
            service.removeFamilyMember(session.token(), member.id());
            return null;
        }, ignored -> {
            clearForm();
            Ui.showInfo(statusText, "Removed.");
            reload();
        }, failure -> handleChangeFailure(failure));
    }

    /** If the member is already gone (removed elsewhere), the list is refreshed so the table tells the truth. */
    private void handleChangeFailure(Throwable failure) {
        Ui.handleFailure(failure, navigator, statusText);
        if (failure instanceof NotFoundException) {
            reload();
        }
    }

    private void clearForm() {
        table.clearSelection();
        nameField.setText("");
        relationshipField.setText("");
        dobField.setText("");
    }

    JTable table() {
        return table;
    }

    JTextField nameField() {
        return nameField;
    }

    JTextField relationshipField() {
        return relationshipField;
    }

    JTextField dobField() {
        return dobField;
    }

    JButton addButton() {
        return addButton;
    }

    JButton updateButton() {
        return updateButton;
    }

    JButton removeButton() {
        return removeButton;
    }

    JTextArea statusText() {
        return statusText;
    }
}
