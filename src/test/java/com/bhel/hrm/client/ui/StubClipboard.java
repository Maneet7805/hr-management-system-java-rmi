package com.bhel.hrm.client.ui;

import java.util.ArrayList;
import java.util.List;

/** A clipboard that only remembers what was put on it. */
final class StubClipboard implements ClipboardWriter {

    private final List<String> copied = new ArrayList<>();

    @Override
    public void copy(String text) {
        copied.add(text);
    }

    List<String> copied() {
        return copied;
    }
}
