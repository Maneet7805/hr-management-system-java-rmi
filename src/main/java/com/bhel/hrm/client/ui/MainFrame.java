package com.bhel.hrm.client.ui;

import com.bhel.hrm.client.ui.Ui.Navigator;
import com.bhel.hrm.common.HRMService;
import com.bhel.hrm.common.model.Session;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

/** The application window. All the behaviour is in {@link ScreenHost}; this class only gives it a frame. */
public final class MainFrame extends JFrame {

    public MainFrame(HRMService service) {
        super("BHEL HR Management System");
        ScreenHost host = new ScreenHost(service);
        setContentPane(host);
        setMinimumSize(new Dimension(640, 480));
        setSize(new Dimension(900, 640));
        setLocationRelativeTo(null);
        // Closing the window logs out first, so the server does not keep an idle session around.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                host.endSession(MainFrame.this::dispose);
            }
        });
    }

    /**
     * Holds whichever screen is showing and switches between them. It is a plain panel, not a window, so it
     * can be tested without a display; {@link MainFrame} only puts it in a window.
     *
     * <p>Every visit to the login screen builds a new one, so nothing typed earlier is left for the next person.
     */
    public static final class ScreenHost extends JPanel implements Navigator {

        private final HRMService service;
        private JComponent current;
        private Session loggedIn;

        public ScreenHost(HRMService service) {
            super(new BorderLayout());
            this.service = service;
            showLogin(null);
        }

        public JComponent currentScreen() {
            return current;
        }

        @Override
        public void showLogin(String message) {
            loggedIn = null;
            LoginPanel login = new LoginPanel(service, this);
            if (message != null) {
                login.showMessage(message);
            }
            show(login);
        }

        @Override
        public void showChangePassword(Session session) {
            loggedIn = session;
            show(new ChangePasswordPanel(service, this, session));
        }

        @Override
        public void showHome(Session session) {
            loggedIn = session;
            show(new HomePanel(service, this, session));
        }

        /**
         * Ends the login on the server, if there is one, then runs {@code afterwards} on the event thread.
         * Used when the window is closed. It runs {@code afterwards} even if the server cannot be reached.
         */
        public void endSession(Runnable afterwards) {
            Session session = loggedIn;
            if (session == null) {
                afterwards.run();
                return;
            }
            Tasks.<Void>run(() -> {
                service.logout(session.token());
                return null;
            }, ignored -> afterwards.run(), failure -> afterwards.run());
        }

        private void show(JComponent screen) {
            if (current != null) {
                remove(current);
            }
            current = screen;
            add(screen, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }
}
