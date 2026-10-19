package com.bhel.hrm.client.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A "save as" dialog that answers by itself with a fixed file (or "cancelled") and remembers what it was offered. */
final class StubFileSaver implements FileSaver {

    private final Path answer;
    private final List<String> suggestedNames = new ArrayList<>();

    /** @param answer the file the person "chooses", or {@code null} for cancel */
    StubFileSaver(Path answer) {
        this.answer = answer;
    }

    @Override
    public Optional<Path> chooseFile(String suggestedName) {
        suggestedNames.add(suggestedName);
        return Optional.ofNullable(answer);
    }

    List<String> suggestedNames() {
        return suggestedNames;
    }
}
