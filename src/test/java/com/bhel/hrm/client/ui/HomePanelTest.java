package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.StubService;
import com.bhel.hrm.client.ui.SwingTestSupport.RecordingNavigator;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class HomePanelTest {

    private static final Session EMPLOYEE = new Session("token-1", 7L, "Alice Tan", Role.EMPLOYEE, false);
    private static final Session HR = new Session("token-2", 9L, "Hana Lim", Role.HR, false);
    private static final Employee ALICE = new Employee(7L, "Alice", "Tan", "AB123456", Role.EMPLOYEE,
            null, null, null, false);
    private static final Employee HANA = new Employee(9L, "Hana", "Lim", "HR900001", Role.HR,
            null, null, null, false);

    private final RecordingNavigator navigator = new RecordingNavigator();

    private static List<String> tabTitles(HomePanel home) throws Exception {
        return onEdt(() -> {
            List<String> titles = new ArrayList<>();
            for (int i = 0; i < home.tabs().getTabCount(); i++) {
                titles.add(home.tabs().getTitleAt(i));
            }
            return titles;
        });
    }

    @Test
    void anEmployeeSeesLeaveFirstThenTheirDetailsAndFamily() throws Exception {
        HomePanel home = onEdt(() -> new HomePanel(StubService.forHome(ALICE).service(), navigator, EMPLOYEE));

        assertEquals(List.of("Leave", "My details", "Family"), tabTitles(home));
        assertInstanceOf(LeavePanel.class, onEdt(() -> home.tabs().getComponentAt(0)));
        assertInstanceOf(ProfilePanel.class, onEdt(() -> home.tabs().getComponentAt(1)));
        assertInstanceOf(FamilyPanel.class, onEdt(() -> home.tabs().getComponentAt(2)));
    }

    @Test
    void hrStaffGetTheHrTabsFirstAndNoLeaveTabBecauseTheyDoNotApplyForLeave() throws Exception {
        HomePanel home = onEdt(() -> new HomePanel(StubService.forHrHome(HANA).service(), navigator, HR));

        assertEquals(List.of("Employees", "Leave requests", "Yearly report", "My details", "Family"), tabTitles(home));
        assertInstanceOf(EmployeesPanel.class, onEdt(() -> home.tabs().getComponentAt(0)));
        assertInstanceOf(PendingLeavePanel.class, onEdt(() -> home.tabs().getComponentAt(1)));
        assertInstanceOf(ReportPanel.class, onEdt(() -> home.tabs().getComponentAt(2)));
    }

    @Test
    void anEmployeeNeverGetsTheHrTabs() throws Exception {
        HomePanel home = onEdt(() -> new HomePanel(StubService.forHome(ALICE).service(), navigator, EMPLOYEE));

        assertTrue(tabTitles(home).stream().noneMatch(title -> title.equals("Employees")
                || title.equals("Leave requests") || title.equals("Yearly report")));
    }

    @Test
    void theWelcomeLineNamesThePersonAndTheirRole() throws Exception {
        HomePanel employee = onEdt(() -> new HomePanel(StubService.forHome(ALICE).service(), navigator, EMPLOYEE));
        HomePanel hr = onEdt(() -> new HomePanel(StubService.forHrHome(HANA).service(), navigator, HR));

        assertEquals("Welcome, Alice Tan (Employee)", onEdt(() -> employee.welcomeLabel().getText()));
        assertEquals("Welcome, Hana Lim (HR staff)", onEdt(() -> hr.welcomeLabel().getText()));
    }

    @Test
    void changePasswordOpensTheChangePasswordScreenForTheSameLogin() throws Exception {
        HomePanel home = onEdt(() -> new HomePanel(StubService.forHome(ALICE).service(), navigator, EMPLOYEE));

        onEdt(() -> home.changePasswordButton().doClick());

        assertEquals(EMPLOYEE, navigator.changePasswordFor);
    }

    @Test
    void logOutEndsTheLoginAndShowsTheLoginScreen() throws Exception {
        StubService stub = StubService.forHome(ALICE);
        HomePanel home = onEdt(() -> new HomePanel(stub.service(), navigator, EMPLOYEE));

        onEdt(() -> home.logOutButton().doClick());
        awaitTrue("login screen", () -> navigator.loginShown == 1);

        assertEquals(1, stub.count("logout"));
        assertEquals(0, stub.callsOnEventThread());
    }

    @Test
    void arrivingOnTheHomeScreenLoadsEachTabsDataOffTheEventThreadAndNothingElse() throws Exception {
        StubService stub = StubService.forHome(ALICE);
        onEdt(() -> new HomePanel(stub.service(), navigator, EMPLOYEE));

        awaitTrue("everything asked for", () -> stub.count("getMyProfile") == 1 && stub.count("getMyFamily") == 1
                && stub.count("getLeaveTypes") == 1 && stub.count("getMyLeaveApplications") == 1);

        assertEquals(0, stub.callsOnEventThread());
        assertEquals(List.of(), stub.unexpectedCalls());
        assertTrue(stub.count("getMyLeaveBalances") >= 1);
    }
}
