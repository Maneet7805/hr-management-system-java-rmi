package com.bhel.hrm.client.ui;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Puts text on the clipboard. An interface so tests need no display. */
@FunctionalInterface
public interface ClipboardWriter {

    void copy(String text);

    static ClipboardWriter system() {
        return text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }
}
