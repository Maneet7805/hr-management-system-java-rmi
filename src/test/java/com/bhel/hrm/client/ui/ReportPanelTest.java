package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.client.StubService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import com.bhel.hrm.common.model.YearlyReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(30)
class ReportPanelTest {

    private static final Session HR = new Session("token-hr", 9L, "Hana Lim", Role.HR, false);
    private static final Employee ALICE = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
            "012-3456789", null, null, false);
    private static final Employee BOB = new Employee(8L, "Bob", "Lee", "CD123456", Role.EMPLOYEE,
            null, null, null, false);
    private static final int THIS_YEAR = LocalDate.now().getYear();

    private final RecordingNavigator navigator = new RecordingNavigator();

    private static YearlyReport reportFor(Employee employee, int year) {
        return new YearlyReport(employee, year, List.of(), List.of(),
                List.of(new LeaveBalance(new LeaveType(1L, "Annual", 14), year, 0, 0)));
    }

    private StubService stub() {
        return new StubService()
                .returns("listEmployees", List.of(ALICE, BOB))
                .on("generateYearlyReport", args -> reportFor(args[1].equals(7L) ? ALICE : BOB, (Integer) args[2]));
    }

    private ReportPanel loaded(StubService stub, FileSaver saver) throws Exception {
        ReportPanel panel = onEdt(() -> new ReportPanel(stub.service(), navigator, HR, saver));
        awaitTrue("employees loaded", () -> panel.employeeCombo().getItemCount() == 2);
        return panel;
    }

    @Test
    void theEmployeesCanBeChosenFromAList() throws Exception {
        ReportPanel panel = loaded(stub(), new StubFileSaver(null));

        assertEquals(ALICE, onEdt(() -> panel.employeeCombo().getItemAt(0)));
        assertEquals(String.valueOf(THIS_YEAR), onEdt(() -> panel.yearField().getText()));
    }

    @Test
    void generatingShowsTheFormattedReportOfTheChosenEmployeeAndYear() throws Exception {
        StubService stub = stub();
        ReportPanel panel = loaded(stub, new StubFileSaver(null));
        onEdt(() -> {
            panel.employeeCombo().setSelectedIndex(1);
            panel.yearField().setText("2025");
            panel.generateButton().doClick();
        });
        awaitTrue("report shown", () -> panel.reportText().getText().startsWith("YEARLY REPORT 2025"));

        assertEquals(List.of("token-hr", 8L, 2025), stub.lastArguments("generateYearlyReport"));
        assertTrue(onEdt(() -> panel.reportText().getText()).contains("Bob Lee"));
        assertEquals("Report ready.", onEdt(() -> panel.statusText().getText()));
        assertTrue(onEdt(() -> panel.saveButton().isEnabled()));
    }

    @Test
    void savingIsOffUntilAReportExists() throws Exception {
        ReportPanel panel = loaded(stub(), new StubFileSaver(null));

        assertFalse(onEdt(() -> panel.saveButton().isEnabled()));
    }

    @Test
    void aYearThatIsNotANumberIsRefusedWithoutCallingTheServer() throws Exception {
        StubService stub = stub();
        ReportPanel panel = loaded(stub, new StubFileSaver(null));

        onEdt(() -> {
            panel.yearField().setText("last year");
            panel.generateButton().doClick();
        });

        assertEquals("Year must be a number such as " + THIS_YEAR + ".", onEdt(() -> panel.statusText().getText()));
        assertEquals(0, stub.count("generateYearlyReport"));
    }

    @Test
    void withNoEmployeeChosenTheScreenAsksForOne() throws Exception {
        StubService stub = new StubService().returns("listEmployees", List.of());
        ReportPanel panel = onEdt(() -> new ReportPanel(stub.service(), navigator, HR, new StubFileSaver(null)));
        awaitTrue("list loaded", () -> stub.count("listEmployees") == 1);

        onEdt(() -> panel.generateButton().doClick());

        assertEquals("Choose an employee.", onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void savingWritesExactlyTheTextOnScreenToTheChosenFile(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("report.txt");
        StubFileSaver saver = new StubFileSaver(target);
        ReportPanel panel = loaded(stub(), saver);
        onEdt(() -> panel.generateButton().doClick());
        awaitTrue("report shown", () -> !panel.reportText().getText().isEmpty());

        onEdt(() -> panel.saveButton().doClick());
        awaitTrue("saved message", () -> panel.statusText().getText().startsWith("Saved to"));

        assertEquals(onEdt(() -> panel.reportText().getText()), Files.readString(target));
        assertEquals(List.of("yearly-report-AB123456-" + THIS_YEAR + ".txt"), saver.suggestedNames());
        assertEquals("Saved to " + target + ".", onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void cancellingTheSaveDialogWritesNothing(@TempDir Path dir) throws Exception {
        StubFileSaver saver = new StubFileSaver(null);
        ReportPanel panel = loaded(stub(), saver);
        onEdt(() -> panel.generateButton().doClick());
        awaitTrue("report shown", () -> !panel.reportText().getText().isEmpty());

        onEdt(() -> panel.saveButton().doClick());

        assertEquals("Report ready.", onEdt(() -> panel.statusText().getText()), "cancelling is not an error");
        try (var files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void aFileThatCannotBeWrittenIsReportedInPlainWords(@TempDir Path dir) throws Exception {
        StubFileSaver saver = new StubFileSaver(dir);   // a folder, so writing to it fails
        ReportPanel panel = loaded(stub(), saver);
        onEdt(() -> panel.generateButton().doClick());
        awaitTrue("report shown", () -> !panel.reportText().getText().isEmpty());

        onEdt(() -> panel.saveButton().doClick());
        awaitTrue("the failure message", () -> panel.statusText().getText().startsWith("The file could not be saved"));

        assertEquals("The file could not be saved. Choose another place and try again.",
                onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void aFailedReportClearsTheOldOneSoItCannotBeMistakenForTheNewEmployee() throws Exception {
        StubService stub = stub();
        ReportPanel panel = loaded(stub, new StubFileSaver(null));
        onEdt(() -> panel.generateButton().doClick());
        awaitTrue("report shown", () -> !panel.reportText().getText().isEmpty());
        stub.returns("generateYearlyReport", new NotFoundException("Employee not found."));

        onEdt(() -> {
            panel.employeeCombo().setSelectedIndex(1);
            panel.generateButton().doClick();
        });
        awaitTrue("the message", () -> panel.statusText().getText().equals("Employee not found."));

        assertEquals("", onEdt(() -> panel.reportText().getText()));
        assertFalse(onEdt(() -> panel.saveButton().isEnabled()));
    }

    @Test
    void anExpiredSessionLeadsBackToTheLoginScreen() throws Exception {
        StubService stub = stub().returns("generateYearlyReport", new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        ReportPanel panel = loaded(stub, new StubFileSaver(null));

        onEdt(() -> panel.generateButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        StubService stub = stub();
        ReportPanel panel = loaded(stub, new StubFileSaver(null));
        onEdt(() -> panel.generateButton().doClick());
        awaitTrue("report shown", () -> !panel.reportText().getText().isEmpty());

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
    }

    /** Guards the test helper itself: writing to a folder really does fail on this machine. */
    @Test
    void writingToAFolderFailsAsTheOtherTestAssumes(@TempDir Path dir) {
        assertThrows(IOException.class, () -> Files.writeString(dir, "x"));
    }
}
