package com.bhel.hrm.client.ui;

import com.bhel.hrm.client.ErrorMessages;
import com.bhel.hrm.common.model.Session;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/** Small helpers so every screen looks and behaves alike. */
final class Ui {

    /** Dark red on white: contrast above 7:1, readable for most colour-vision differences too. */
    static final Color ERROR_COLOR = new Color(0xB00020);

    private Ui() {
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * 1.6f));
        return label;
    }

    /** Secondary text. Dark grey, about 6:1 on the usual light backgrounds; the theme's "disabled" grey is too faint to read. */
    static final Color HINT_COLOR = new Color(0x505050);

    static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(HINT_COLOR);
        return label;
    }

    /** A message line that wraps long sentences and can be read by screen readers; looks like a label. */
    static JTextArea statusLine() {
        JTextArea area = new JTextArea(2, 28);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(UIManager.getFont("Label.font"));
        area.getAccessibleContext().setAccessibleName("Status message");
        return area;
    }

    static void showError(JTextArea status, String message) {
        status.setForeground(ERROR_COLOR);
        status.setText(message);
    }

    static void showInfo(JTextArea status, String message) {
        status.setForeground(UIManager.getColor("Label.foreground"));
        status.setText(message);
    }

    /**
     * What every screen does when a server call fails: an expired session sends the person back to the login
     * screen with an explanation; anything else is shown as a sentence on the screen's own status line.
     */
    static void handleFailure(Throwable failure, Navigator navigator, JTextArea status) {
        if (ErrorMessages.isSessionExpired(failure)) {
            navigator.showLogin(ErrorMessages.SESSION_EXPIRED);
        } else {
            showError(status, ErrorMessages.forUser(failure));
        }
    }

    /** In a vertical box layout, components with different alignments end up centred or ragged. */
    static <T extends javax.swing.JComponent> T leftAligned(T component) {
        component.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        return component;
    }

    /**
     * A scroll pane around a table with the column headings showing. Swing normally adds the headings only once
     * the window is on screen; adding them here means the screen is the same however and wherever it is built.
     */
    static javax.swing.JScrollPane scrollableTable(javax.swing.JTable table, int width, int height) {
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new java.awt.Dimension(width, height));
        return scroll;
    }

    static GridBagConstraints cell(int column, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    /** Asks "are you sure?" before something that cannot be undone. An interface so the dialog code stays out of the screens. */
    @FunctionalInterface
    public interface Confirmer {

        boolean confirm(String question);

        /** A Yes/No dialog over {@code parent}. Call it on the event thread. */
        static Confirmer dialog(Component parent) {
            return question -> JOptionPane.showConfirmDialog(parent, question, "Please confirm",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
        }
    }

    /** Puts text on the clipboard. An interface so the clipboard code stays out of the screens. */
    @FunctionalInterface
    public interface ClipboardWriter {

        void copy(String text);

        static ClipboardWriter system() {
            return text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    }

    /** Asks the person where to save a file. An interface so the file-chooser code stays out of the screens. */
    @FunctionalInterface
    public interface FileSaver {

        /** @return the chosen file, or empty if the person cancelled */
        Optional<Path> chooseFile(String suggestedName);

        /** A standard "Save as" dialog over {@code parent}; asks before replacing an existing file. Call on the event thread. */
        static FileSaver dialog(Component parent) {
            return suggestedName -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Save report");
                chooser.setSelectedFile(new File(suggestedName));
                if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                    return Optional.empty();
                }
                File file = chooser.getSelectedFile();
                if (file.exists() && JOptionPane.showConfirmDialog(parent,
                        file.getName() + " already exists. Replace it?", "Please confirm",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                    return Optional.empty();
                }
                return Optional.of(file.toPath());
            };
        }
    }

    /** How a screen asks the window to show another screen. Kept apart from the window so screens can be tested alone. */
    public interface Navigator {

        /** The login screen. {@code message} (may be {@code null}) is shown on it, for example "session expired". */
        void showLogin(String message);

        /** For someone who must choose their own password before doing anything else. */
        void showChangePassword(Session session);

        void showHome(Session session);
    }
}
