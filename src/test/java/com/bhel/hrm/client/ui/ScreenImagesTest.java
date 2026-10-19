package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.awaitTrue;
import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ScriptedService;
import com.bhel.hrm.client.StubService;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.model.Employee;
import com.bhel.hrm.common.model.FamilyMember;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.common.model.Session;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;

/**
 * Draws each screen to a PNG in {@code target/screens} (not committed) so the layout can be looked at without
 * opening a window. The test only checks that something real was drawn.
 */
class ScreenImagesTest {

    private static final Path OUTPUT = Path.of("target", "screens");
    private static final Session TEMPORARY = new Session("t", 8L, "Bob Lee", Role.EMPLOYEE, true);
    private static final Session EMPLOYEE = new Session("t", 7L, "Alice Tan", Role.EMPLOYEE, false);

    private static final Employee ALICE = new Employee(7L, "Alice", "Tan", "900101-14-5678", Role.EMPLOYEE,
            "012-3456789", "alice@bhel.example", "1 Jalan Satu, Taman Setia, 47500 Subang Jaya", false);
    private static final LeaveType ANNUAL = new LeaveType(1L, "Annual", 14);
    private static final LeaveType MEDICAL = new LeaveType(2L, "Medical", 14);
    private static final LeaveType EMERGENCY = new LeaveType(3L, "Emergency", 3);
    private static final LeaveType UNPAID = new LeaveType(4L, "Unpaid", null);

    private static StubService sampleServer() {
        int year = LocalDate.now().getYear();
        return StubService.forHome(ALICE)
                .returns("getMyFamily", List.of(
                        new FamilyMember(1L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17)),
                        new FamilyMember(2L, "Cara Tan", "Child", LocalDate.of(2018, 9, 2)),
                        new FamilyMember(3L, "Mei Lin Tan", "Parent", null)))
                .returns("getLeaveTypes", List.of(ANNUAL, MEDICAL, EMERGENCY, UNPAID))
                .returns("getMyLeaveBalances", List.of(
                        new LeaveBalance(ANNUAL, year, 3, 2),
                        new LeaveBalance(MEDICAL, year, 0, 0),
                        new LeaveBalance(EMERGENCY, year, 1, 0),
                        new LeaveBalance(UNPAID, year, 5, 0)))
                .returns("getMyLeaveApplications", List.of(
                        new LeaveApplication(3L, 7L, "Alice Tan", ANNUAL, LocalDate.of(year, 6, 1), LocalDate.of(year, 6, 2), 2,
                                "School holiday", LeaveStatus.PENDING, LocalDateTime.of(year, 5, 20, 9, 15), null),
                        new LeaveApplication(2L, 7L, "Alice Tan", ANNUAL, LocalDate.of(year, 3, 10), LocalDate.of(year, 3, 12), 3,
                                "Family trip", LeaveStatus.APPROVED, LocalDateTime.of(year, 3, 1, 9, 0),
                                LocalDateTime.of(year, 3, 2, 14, 30)),
                        new LeaveApplication(1L, 7L, "Alice Tan", MEDICAL, LocalDate.of(year, 2, 5), LocalDate.of(year, 2, 5), 1,
                                null, LeaveStatus.REJECTED, LocalDateTime.of(year, 2, 6, 8, 0),
                                LocalDateTime.of(year, 2, 6, 16, 45))));
    }

    private static Path draw(String name, JComponent screen, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        File file = OUTPUT.resolve(name + ".png").toFile();
        onEdt(() -> {
            screen.setSize(width, height);
            layoutAll(screen);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            screen.paint(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", file);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        return file.toPath();
    }

    /** Lays out the screen and everything inside it, as a real window would, without needing a display. */
    private static void layoutAll(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutAll(child);
            }
        }
    }

    private static void assertSomethingWasDrawn(Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        Set<Integer> colours = new HashSet<>();
        for (int x = 0; x < image.getWidth(); x += 3) {
            for (int y = 0; y < image.getHeight(); y += 3) {
                colours.add(image.getRGB(x, y));
            }
        }
        assertTrue(colours.size() > 5, png + " looks blank");
    }

    @Test
    void loginScreen() throws Exception {
        LoginPanel panel = onEdt(() -> new LoginPanel(new ScriptedService().service(), new SwingTestSupport.RecordingNavigator()));

        assertSomethingWasDrawn(draw("login", panel, 640, 420));
    }

    @Test
    void loginScreenWithAnErrorMessage() throws Exception {
        LoginPanel panel = onEdt(() -> new LoginPanel(new ScriptedService().service(), new SwingTestSupport.RecordingNavigator()));
        onEdt(() -> panel.showMessage("Cannot reach the HR server at localhost:1099. Check that it is running and try again."));

        assertSomethingWasDrawn(draw("login-error", panel, 640, 420));
    }

    @Test
    void changePasswordScreen() throws Exception {
        ChangePasswordPanel panel = onEdt(() -> new ChangePasswordPanel(
                new ScriptedService(new InvalidCredentialsException("x")).service(),
                new SwingTestSupport.RecordingNavigator(), TEMPORARY));

        assertSomethingWasDrawn(draw("change-password", panel, 640, 460));
    }

    @Test
    void myDetailsScreen() throws Exception {
        ProfilePanel panel = onEdt(() -> new ProfilePanel(sampleServer().service(),
                new SwingTestSupport.RecordingNavigator(), EMPLOYEE));
        awaitTrue("details loaded", () -> panel.saveButton().isEnabled());

        assertSomethingWasDrawn(draw("my-details", panel, 900, 420));
    }

    @Test
    void familyScreen() throws Exception {
        FamilyPanel panel = onEdt(() -> new FamilyPanel(sampleServer().service(),
                new SwingTestSupport.RecordingNavigator(), EMPLOYEE, question -> true));
        awaitTrue("family loaded", () -> panel.table().getRowCount() == 3);
        onEdt(() -> panel.table().setRowSelectionInterval(0, 0));

        assertSomethingWasDrawn(draw("family", panel, 900, 480));
    }

    @Test
    void leaveScreen() throws Exception {
        LeavePanel panel = onEdt(() -> new LeavePanel(sampleServer().service(),
                new SwingTestSupport.RecordingNavigator(), EMPLOYEE));
        awaitTrue("leave loaded", () -> panel.balancesTable().getRowCount() == 4
                && panel.applicationsTable().getRowCount() == 3 && panel.typeCombo().getItemCount() == 4);
        onEdt(() -> {
            panel.startField().setText("2026-08-03");
            panel.endField().setText("2026-08-05");
            panel.reasonField().setText("Visiting family");
        });

        assertSomethingWasDrawn(draw("leave", panel, 900, 640));
    }

    @Test
    void leaveScreenWithARefusal() throws Exception {
        LeavePanel panel = onEdt(() -> new LeavePanel(sampleServer().returns("applyForLeave",
                new com.bhel.hrm.common.exception.ValidationException(
                        "Not enough Annual leave left: 9 day(s) remaining, 15 requested.")).service(),
                new SwingTestSupport.RecordingNavigator(), EMPLOYEE));
        awaitTrue("leave loaded", () -> panel.balancesTable().getRowCount() == 4);
        onEdt(() -> {
            panel.startField().setText("2026-08-03");
            panel.endField().setText("2026-08-17");
            panel.applyButton().doClick();
        });
        awaitTrue("the refusal", () -> panel.statusText().getText().startsWith("Not enough"));

        assertSomethingWasDrawn(draw("leave-refused", panel, 900, 640));
    }

    @Test
    void homeScreenOfAnEmployee() throws Exception {
        HomePanel panel = onEdt(() -> new HomePanel(sampleServer().service(),
                new SwingTestSupport.RecordingNavigator(), EMPLOYEE));
        LeavePanel leave = (LeavePanel) onEdt(() -> panel.tabs().getComponentAt(0));
        awaitTrue("leave loaded", () -> leave.balancesTable().getRowCount() == 4
                && leave.applicationsTable().getRowCount() == 3);

        assertSomethingWasDrawn(draw("home-employee", panel, 960, 700));
    }
}
