package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.common.model.YearlyReport;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * "Yearly report": choose an employee and a year, read the report on screen, and save it as a text file.
 * The text on screen is exactly what is saved.
 */
public final class ReportPanel extends JPanel {

    private final HRMService service;
    private final Navigator navigator;
    private final Session session;
    private final FileSaver fileSaver;
    private final JComboBox<Employee> employeeCombo = new JComboBox<>();
    private final JTextField yearField = new JTextField(String.valueOf(LocalDate.now().getYear()), 6);
    private final JButton generateButton = new JButton("Generate report");
    private final JButton saveButton = new JButton("Save as text file...");
    private final JTextArea reportText = new JTextArea(24, 80);
    private final JTextArea statusText = Ui.statusLine();
    private YearlyReport currentReport;

    public ReportPanel(HRMService service, Navigator navigator, Session session, FileSaver fileSaver) {
        super(new BorderLayout(8, 8));
        this.service = service;
        this.navigator = navigator;
        this.session = session;
        this.fileSaver = fileSaver;
        buildLayout();
        generateButton.addActionListener(e -> generate());
        yearField.addActionListener(e -> generate());
        saveButton.addActionListener(e -> save());
        saveButton.setEnabled(false);
        loadEmployees();
    }

    private void buildLayout() {
        employeeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focused) {
                String text = value instanceof Employee e
                        ? e.firstName() + " " + e.lastName() + " (" + e.icPassport() + ")" : "";
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        employeeCombo.getAccessibleContext().setAccessibleName("Employee");
        yearField.getAccessibleContext().setAccessibleName("Year");
        reportText.setEditable(false);
        reportText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        reportText.getAccessibleContext().setAccessibleName("Yearly report");
        generateButton.setMnemonic('G');
        saveButton.setMnemonic('S');

        JPanel choose = new JPanel(new FlowLayout(FlowLayout.LEFT));
        choose.add(Ui.title("Yearly report"));
        choose.add(label("Employee:", employeeCombo, 'E'));
        choose.add(employeeCombo);
        choose.add(label("Year:", yearField, 'Y'));
        choose.add(yearField);
        choose.add(generateButton);
        choose.add(saveButton);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.add(Ui.leftAligned(statusText));

        add(choose, BorderLayout.NORTH);
        add(new JScrollPane(reportText), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private static JLabel label(String text, Component field, char mnemonic) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        label.setDisplayedMnemonic(mnemonic);
        return label;
    }

    private void loadEmployees() {
        Tasks.<List<Employee>>run(() -> service.listEmployees(session.token()),
                employees -> {
                    employeeCombo.removeAllItems();
                    employees.forEach(employeeCombo::addItem);
                },
                failure -> Ui.handleFailure(failure, navigator, statusText));
    }

    private void generate() {
        Employee employee = (Employee) employeeCombo.getSelectedItem();
        if (employee == null) {
            Ui.showError(statusText, "Choose an employee.");
            return;
        }
        int year;
        try {
            year = Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            Ui.showError(statusText, "Year must be a number such as " + LocalDate.now().getYear() + ".");
            return;
        }
        // The old report goes first, so it can never be mistaken for the one being asked for.
        clearReport();
        generateButton.setEnabled(false);
        Ui.showInfo(statusText, "Building the report...");
        Tasks.<YearlyReport>run(() -> service.generateYearlyReport(session.token(), employee.id(), year),
                report -> {
                    generateButton.setEnabled(true);
                    currentReport = report;
                    reportText.setText(ReportFormatter.format(report));
                    reportText.setCaretPosition(0);
                    saveButton.setEnabled(true);
                    Ui.showInfo(statusText, "Report ready.");
                },
                failure -> {
                    generateButton.setEnabled(true);
                    Ui.handleFailure(failure, navigator, statusText);
                });
    }

    private void save() {
        if (currentReport == null) {
            return;
        }
        String text = ReportFormatter.format(currentReport);
        Optional<Path> chosen = fileSaver.chooseFile(ReportFormatter.fileName(currentReport));
        if (chosen.isEmpty()) {
            return;   // the person cancelled; that is not an error
        }
        Path file = chosen.get();
        Tasks.<Path>run(() -> Files.writeString(file, text, StandardCharsets.UTF_8),
                written -> Ui.showInfo(statusText, "Saved to " + file + "."),
                failure -> Ui.showError(statusText, "The file could not be saved. Choose another place and try again."));
    }

    private void clearReport() {
        currentReport = null;
        reportText.setText("");
        saveButton.setEnabled(false);
    }

    JComboBox<Employee> employeeCombo() {
        return employeeCombo;
    }

    JTextField yearField() {
        return yearField;
    }

    JButton generateButton() {
        return generateButton;
    }

    JButton saveButton() {
        return saveButton;
    }

    JTextArea reportText() {
        return reportText;
    }

    JTextArea statusText() {
        return statusText;
    }
}
