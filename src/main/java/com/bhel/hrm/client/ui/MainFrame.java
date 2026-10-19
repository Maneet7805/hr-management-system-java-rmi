package com.bhel.hrm.client.ui;

import com.bhel.hrm.common.HRMService;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

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
}
