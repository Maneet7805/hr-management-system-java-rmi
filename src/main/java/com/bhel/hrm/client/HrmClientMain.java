package com.bhel.hrm.client;

import com.bhel.hrm.client.ui.MainFrame;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.net.SettingsSupport;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Starts the HR client. Usage: {@code HrmClientMain [path-to-client.properties]}.
 * Needs HRM_TRUSTSTORE_PASSWORD in the environment and the server running.
 */
public final class HrmClientMain {

    private HrmClientMain() {
    }

    public static void main(String[] args) {
        Path configFile = Path.of(args.length > 0 ? args[0] : "config/client.properties");
        ClientSettings settings;
        try {
            settings = ClientSettings.load(configFile, System.getenv());
        } catch (IOException e) {
            reportAndExit("Cannot start the client. " + SettingsSupport.describeUnreadable(configFile, e));
            return;
        } catch (IllegalArgumentException e) {
            reportAndExit("Cannot start the client: " + e.getMessage());
            return;
        }
        // The server is looked up on first use, so a server that is not running yet shows up as a normal
        // message on the login screen instead of stopping the program here.
        HRMService service = ResilientService.create(() -> ServerConnection.connect(settings));
        SwingUtilities.invokeLater(() -> {
            useSystemLookAndFeel();
            new MainFrame(service).setVisible(true);
        });
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            // The default look is fine; nothing to tell the user.
        }
    }

    private static void reportAndExit(String message) {
        System.err.println(message);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "BHEL HR Management System", JOptionPane.ERROR_MESSAGE);
        }
        System.exit(1);
    }
}
