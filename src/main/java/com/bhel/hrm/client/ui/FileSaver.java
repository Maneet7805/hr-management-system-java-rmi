package com.bhel.hrm.client.ui;

import java.awt.Component;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/** Asks the person where to save a file. An interface so tests need no dialog. */
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
