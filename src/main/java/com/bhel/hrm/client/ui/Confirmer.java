package com.bhel.hrm.client.ui;

import java.awt.Component;
import javax.swing.JOptionPane;

/** Asks "are you sure?" before something that cannot be undone. An interface so tests need no dialog. */
@FunctionalInterface
public interface Confirmer {

    boolean confirm(String question);

    /** A Yes/No dialog over {@code parent}. Call it on the event thread. */
    static Confirmer dialog(Component parent) {
        return question -> JOptionPane.showConfirmDialog(parent, question, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
