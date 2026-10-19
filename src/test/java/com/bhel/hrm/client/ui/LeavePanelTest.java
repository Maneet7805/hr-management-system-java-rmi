package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.client.StubService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class LeavePanelTest {

    private static final Session SESSION = new Session("token-1", 7L, "Alice Tan", Role.EMPLOYEE, false);
    private static final LeaveType ANNUAL = new LeaveType(1L, "Annual", 14);
    private static final LeaveType UNPAID = new LeaveType(4L, "Unpaid", null);
    private static final int THIS_YEAR = LocalDate.now().getYear();

    private final RecordingNavigator navigator = new RecordingNavigator();
    private final List<LeaveApplication> applications = Collections.synchronizedList(new ArrayList<>());

    private StubService stub() {
        return new StubService()
                .returns("getLeaveTypes", List.of(ANNUAL, UNPAID))
                .on("getMyLeaveBalances", args -> List.of(
                        new LeaveBalance(ANNUAL, (Integer) args[1], 0, applications.size() * 3),
                        new LeaveBalance(UNPAID, (Integer) args[1], 0, 0)))
                .on("getMyLeaveApplications", args -> List.copyOf(applications));
    }

    private LeavePanel loaded(StubService stub) throws Exception {
        LeavePanel panel = onEdt(() -> new LeavePanel(stub.service(), navigator, SESSION));
        awaitTrue("everything loaded", () -> panel.typeCombo().getItemCount() == 2
                && panel.balancesTable().getRowCount() == 2);
        return panel;
    }

    private static void fillApplication(LeavePanel panel, String start, String end, String reason) throws Exception {
        onEdt(() -> {
            panel.startField().setText(start);
            panel.endField().setText(end);
            panel.reasonField().setText(reason);
        });
    }

    private static LeaveApplication application(long id, LocalDate start, LocalDate end, int days) {
        return new LeaveApplication(id, 7L, "Alice Tan", ANNUAL, start, end, days, "Trip",
                LeaveStatus.PENDING, LocalDateTime.of(2026, 3, 1, 9, 0), null);
    }

    @Test
    void typesBalancesAndApplicationsAreLoadedAndShown() throws Exception {
        applications.add(application(11L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), 3));
        LeavePanel panel = loaded(stub());
        awaitTrue("applications", () -> panel.applicationsTable().getRowCount() == 1);

        assertEquals("Annual", onEdt(() -> panel.balancesTable().getValueAt(0, 0)));
        assertEquals("14", onEdt(() -> panel.balancesTable().getValueAt(0, 1)));
        assertEquals("Unlimited", onEdt(() -> panel.balancesTable().getValueAt(1, 4)));
        assertEquals("2026-03-10", onEdt(() -> panel.applicationsTable().getValueAt(0, 0)));
        assertEquals("Pending", onEdt(() -> panel.applicationsTable().getValueAt(0, 4)));
        assertEquals(String.valueOf(THIS_YEAR), onEdt(() -> panel.yearField().getText()));
    }

    @Test
    void applyingSendsTheChosenTypeDatesAndReasonThenShowsWhatHappened() throws Exception {
        StubService stub = stub().on("applyForLeave", args -> {
            LeaveApplication saved = application(11L, (LocalDate) args[2], (LocalDate) args[3], 3);
            applications.add(saved);
            return saved;
        });
        LeavePanel panel = loaded(stub);
        fillApplication(panel, "2026-03-10", "2026-03-12", "  Family trip ");

        onEdt(() -> panel.applyButton().doClick());
        awaitTrue("one application listed", () -> panel.applicationsTable().getRowCount() == 1);

        assertEquals(List.of("token-1", 1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12), "Family trip"),
                stub.lastArguments("applyForLeave"));
        assertEquals("Application sent: 3 days of Annual leave, waiting for HR's decision.",
                onEdt(() -> panel.statusText().getText()));
        assertEquals("", onEdt(() -> panel.startField().getText()), "the form is ready for the next one");
        assertEquals("11", onEdt(() -> panel.balancesTable().getValueAt(0, 4)), "balances are read again");
    }

    @Test
    void theSecondLeaveTypeCanBeChosen() throws Exception {
        StubService stub = stub().on("applyForLeave", args -> application(12L, (LocalDate) args[2], (LocalDate) args[3], 1));
        LeavePanel panel = loaded(stub);
        onEdt(() -> panel.typeCombo().setSelectedIndex(1));
        fillApplication(panel, "2026-05-04", "2026-05-04", "");

        onEdt(() -> panel.applyButton().doClick());
        awaitTrue("sent", () -> panel.statusText().getText().startsWith("Application sent"));

        assertEquals(4L, stub.lastArguments("applyForLeave").get(1));
        assertEquals(null, stub.lastArguments("applyForLeave").get(4), "an empty reason is sent as no reason");
    }

    @Test
    void aDateThatIsMissingOrWrongIsExplainedWithoutCallingTheServer() throws Exception {
        StubService stub = stub();
        LeavePanel panel = loaded(stub);

        fillApplication(panel, "", "2026-03-12", "");
        onEdt(() -> panel.applyButton().doClick());
        assertEquals("Enter the start date.", onEdt(() -> panel.statusText().getText()));

        fillApplication(panel, "2026-03-10", "12 March", "");
        onEdt(() -> panel.applyButton().doClick());
        assertEquals("End date must be a real date in the form yyyy-MM-dd, for example 2026-03-15.",
                onEdt(() -> panel.statusText().getText()));

        assertEquals(0, stub.count("applyForLeave"));
    }

    @Test
    void theServersReasonForRefusingIsShownAndTheFormKeepsWhatWasTyped() throws Exception {
        StubService stub = stub().returns("applyForLeave",
                new ValidationException("Not enough Annual leave left: 3 day(s) remaining, 5 requested."));
        LeavePanel panel = loaded(stub);
        fillApplication(panel, "2026-03-10", "2026-03-14", "Trip");

        onEdt(() -> panel.applyButton().doClick());
        awaitTrue("the reason", () -> panel.statusText().getText()
                .equals("Not enough Annual leave left: 3 day(s) remaining, 5 requested."));

        assertEquals("2026-03-10", onEdt(() -> panel.startField().getText()));
        assertTrue(onEdt(() -> panel.applyButton().isEnabled()));
    }

    @Test
    void changingTheYearReadsTheBalancesOfThatYear() throws Exception {
        StubService stub = stub();
        LeavePanel panel = loaded(stub);

        onEdt(() -> {
            panel.yearField().setText("2027");
            panel.refreshButton().doClick();
        });
        awaitTrue("balances for 2027", () -> {
            List<Object> last = stub.lastArguments("getMyLeaveBalances");
            return last.get(1).equals(2027);
        });
    }

    @Test
    void aYearThatIsNotANumberIsRefusedWithoutCallingTheServer() throws Exception {
        StubService stub = stub();
        LeavePanel panel = loaded(stub);
        int before = stub.count("getMyLeaveBalances");

        onEdt(() -> {
            panel.yearField().setText("next year");
            panel.refreshButton().doClick();
        });

        assertEquals("Year must be a number such as " + THIS_YEAR + ".", onEdt(() -> panel.statusText().getText()));
        assertEquals(before, stub.count("getMyLeaveBalances"));
    }

    @Test
    void anExpiredSessionLeadsBackToTheLoginScreen() throws Exception {
        StubService stub = stub().returns("applyForLeave", new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        LeavePanel panel = loaded(stub);
        fillApplication(panel, "2026-03-10", "2026-03-12", "");

        onEdt(() -> panel.applyButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        StubService stub = stub().on("applyForLeave", args -> {
            LeaveApplication saved = application(11L, (LocalDate) args[2], (LocalDate) args[3], 3);
            applications.add(saved);
            return saved;
        });
        LeavePanel panel = loaded(stub);
        fillApplication(panel, "2026-03-10", "2026-03-12", "");
        onEdt(() -> panel.applyButton().doClick());
        awaitTrue("listed", () -> panel.applicationsTable().getRowCount() == 1);

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
    }
}
