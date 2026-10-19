package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.client.StubService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.LeaveApplication;
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
class PendingLeavePanelTest {

    private static final Session HR = new Session("token-hr", 9L, "Hana Lim", Role.HR, false);
    private static final LeaveType ANNUAL = new LeaveType(1L, "Annual", 14);
    private static final LeaveType MEDICAL = new LeaveType(2L, "Medical", 14);

    private final RecordingNavigator navigator = new RecordingNavigator();
    private final List<LeaveApplication> waiting = Collections.synchronizedList(new ArrayList<>());

    private static LeaveApplication application(long id, String employee, LeaveType type, int fromDay, int toDay) {
        return new LeaveApplication(id, 7L, employee, type, LocalDate.of(2026, 3, fromDay), LocalDate.of(2026, 3, toDay),
                toDay - fromDay + 1, "A reason", LeaveStatus.PENDING, LocalDateTime.of(2026, 3, 1, 9, 0), null);
    }

    private void twoWaiting() {
        waiting.add(application(11L, "Alice Tan", ANNUAL, 10, 12));
        waiting.add(application(12L, "Bob Lee", MEDICAL, 15, 15));
    }

    private StubService stub() {
        return new StubService().on("listPendingLeave", args -> List.copyOf(waiting));
    }

    private PendingLeavePanel loaded(StubService stub, boolean confirmAnswer) throws Exception {
        PendingLeavePanel panel = onEdt(() -> new PendingLeavePanel(stub.service(), navigator, HR,
                new StubConfirmer(confirmAnswer)));
        awaitTrue("list loaded", () -> stub.count("listPendingLeave") >= 1 && panel.table().getRowCount() == waiting.size());
        return panel;
    }

    private static void select(PendingLeavePanel panel, int row) throws Exception {
        onEdt(() -> panel.table().setRowSelectionInterval(row, row));
    }

    @Test
    void waitingRequestsAreListedWithTheEmployeesName() throws Exception {
        twoWaiting();
        PendingLeavePanel panel = loaded(stub(), true);

        assertEquals("Alice Tan", onEdt(() -> panel.table().getValueAt(0, 0)));
        assertEquals("3 days", onEdt(() -> panel.table().getValueAt(0, 3)));
        assertEquals("Medical", onEdt(() -> panel.table().getValueAt(1, 4)));
        assertFalse(onEdt(() -> panel.emptyLabel().isVisible()));
    }

    @Test
    void whenNothingIsWaitingTheScreenSaysSo() throws Exception {
        PendingLeavePanel panel = loaded(stub(), true);

        awaitTrue("the empty message", () -> panel.emptyLabel().isVisible());
        assertTrue(onEdt(() -> panel.emptyLabel().isVisible()));
        assertEquals("No leave requests are waiting for a decision.", onEdt(() -> panel.emptyLabel().getText()));
    }

    @Test
    void approveAndRejectAreOffUntilARequestIsSelected() throws Exception {
        twoWaiting();
        PendingLeavePanel panel = loaded(stub(), true);

        assertFalse(onEdt(() -> panel.approveButton().isEnabled()));
        assertFalse(onEdt(() -> panel.rejectButton().isEnabled()));
        select(panel, 0);
        assertTrue(onEdt(() -> panel.approveButton().isEnabled()));
        assertTrue(onEdt(() -> panel.rejectButton().isEnabled()));
    }

    @Test
    void approvingAsksWithTheFullDetailsThenDecidesAndReloads() throws Exception {
        twoWaiting();
        StubService stub = stub().on("decideLeave", args -> {
            waiting.removeIf(a -> a.id() == (Long) args[1]);
            return application(11L, "Alice Tan", ANNUAL, 10, 12);
        });
        StubConfirmer yes = new StubConfirmer(true);
        PendingLeavePanel panel = onEdt(() -> new PendingLeavePanel(stub.service(), navigator, HR, yes));
        awaitTrue("list loaded", () -> panel.table().getRowCount() == 2);
        select(panel, 0);

        onEdt(() -> panel.approveButton().doClick());
        awaitTrue("one left", () -> panel.table().getRowCount() == 1);

        String question = yes.questions().get(0);
        assertTrue(question.startsWith("Approve"), question);
        assertTrue(question.contains("Alice Tan") && question.contains("3 days") && question.contains("Annual")
                && question.contains("2026-03-10") && question.contains("2026-03-12"), question);
        assertEquals(List.of("token-hr", 11L, true), stub.lastArguments("decideLeave"));
        assertEquals("Approved Alice Tan's Annual leave (2026-03-10 to 2026-03-12).",
                onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void rejectingAsksAndSendsFalse() throws Exception {
        twoWaiting();
        StubService stub = stub().on("decideLeave", args -> {
            waiting.removeIf(a -> a.id() == (Long) args[1]);
            return application(12L, "Bob Lee", MEDICAL, 15, 15);
        });
        StubConfirmer yes = new StubConfirmer(true);
        PendingLeavePanel panel = onEdt(() -> new PendingLeavePanel(stub.service(), navigator, HR, yes));
        awaitTrue("list loaded", () -> panel.table().getRowCount() == 2);
        select(panel, 1);

        onEdt(() -> panel.rejectButton().doClick());
        awaitTrue("one left", () -> panel.table().getRowCount() == 1);

        assertTrue(yes.questions().get(0).startsWith("Reject"), yes.questions().get(0));
        assertEquals(List.of("token-hr", 12L, false), stub.lastArguments("decideLeave"));
        assertEquals("Rejected Bob Lee's Medical leave (2026-03-15 to 2026-03-15).",
                onEdt(() -> panel.statusText().getText()));
    }

    @Test
    void sayingNoToTheQuestionChangesNothingAndCallsNothing() throws Exception {
        twoWaiting();
        StubService stub = stub();
        StubConfirmer no = new StubConfirmer(false);
        PendingLeavePanel panel = onEdt(() -> new PendingLeavePanel(stub.service(), navigator, HR, no));
        awaitTrue("list loaded", () -> panel.table().getRowCount() == 2);
        select(panel, 0);

        onEdt(() -> panel.approveButton().doClick());

        assertEquals(1, no.questions().size());
        assertEquals(0, stub.count("decideLeave"));
        assertEquals(2, onEdt(() -> panel.table().getRowCount()));
    }

    @Test
    void ifSomeoneElseAlreadyDecidedItTheMessageIsShownAndTheListIsRefreshed() throws Exception {
        twoWaiting();
        StubService stub = stub().on("decideLeave", args -> {
            waiting.removeIf(a -> a.id() == (Long) args[1]);
            return new ValidationException("This application has already been decided.");
        });
        PendingLeavePanel panel = loaded(stub, true);
        select(panel, 0);

        onEdt(() -> panel.approveButton().doClick());
        awaitTrue("the message", () -> panel.statusText().getText().equals("This application has already been decided."));
        awaitTrue("list refreshed", () -> panel.table().getRowCount() == 1);
    }

    @Test
    void anExpiredSessionLeadsBackToTheLoginScreen() throws Exception {
        twoWaiting();
        StubService stub = stub().returns("decideLeave", new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED));
        PendingLeavePanel panel = loaded(stub, true);
        select(panel, 0);

        onEdt(() -> panel.approveButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(ErrorMessages.SESSION_EXPIRED, navigator.loginMessage);
    }

    @Test
    void theServerIsNeverCalledOnTheEventThread() throws Exception {
        twoWaiting();
        StubService stub = stub().on("decideLeave", args -> {
            waiting.removeIf(a -> a.id() == (Long) args[1]);
            return application(11L, "Alice Tan", ANNUAL, 10, 12);
        });
        PendingLeavePanel panel = loaded(stub, true);
        select(panel, 0);
        onEdt(() -> panel.approveButton().doClick());
        awaitTrue("one left", () -> panel.table().getRowCount() == 1);

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
    }
}
