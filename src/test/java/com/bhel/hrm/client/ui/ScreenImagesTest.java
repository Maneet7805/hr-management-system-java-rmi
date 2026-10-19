package com.bhel.hrm.client.ui;

import static com.bhel.hrm.client.ui.SwingTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.client.ScriptedService;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
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
import java.util.HashSet;
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
    void homeScreen() throws Exception {
        HomePanel panel = onEdt(() -> new HomePanel(new ScriptedService().service(),
                new SwingTestSupport.RecordingNavigator(), EMPLOYEE));

        assertSomethingWasDrawn(draw("home", panel, 640, 320));
    }
}
