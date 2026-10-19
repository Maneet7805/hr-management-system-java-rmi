package com.bhel.hrm.client.ui;

import java.util.ArrayList;
import java.util.List;

/** A confirmation "dialog" that answers by itself and remembers what it was asked. */
final class StubConfirmer implements Confirmer {

    private final boolean answer;
    private final List<String> questions = new ArrayList<>();

    StubConfirmer(boolean answer) {
        this.answer = answer;
    }

    @Override
    public boolean confirm(String question) {
        questions.add(question);
        return answer;
    }

    List<String> questions() {
        return questions;
    }
}
