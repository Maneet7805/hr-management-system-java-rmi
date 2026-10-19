package com.bhel.hrm.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettingsFileMessageTest {

    private static final Path FILE = Path.of("config", "client.properties");

    @Test
    void aMissingFileSaysWhichFileAndHowToCreateIt() {
        String message = SettingsSupport.describeUnreadable(FILE, new NoSuchFileException(FILE.toString()));

        assertTrue(message.contains(FILE.toString()), message);
        assertTrue(message.contains("was not found"), message);
        assertTrue(message.contains("client.example.properties"), "it names the template to copy: " + message);
    }

    @Test
    void anyOtherReadProblemNamesTheFileAndTheReason() {
        String message = SettingsSupport.describeUnreadable(FILE, new AccessDeniedException(FILE.toString()));

        assertTrue(message.startsWith("Cannot read the settings file " + FILE), message);
        assertFalse(message.contains("was not found"));
    }

    @Test
    void theTemplateNameFollowsTheFileName() {
        String message = SettingsSupport.describeUnreadable(Path.of("config", "server.properties"),
                new NoSuchFileException("x"));

        assertTrue(message.contains("server.example.properties"), message);
    }

    @Test
    void aGenericIoErrorStillGivesASentence() {
        assertEquals("Cannot read the settings file " + FILE + ": disk problem",
                SettingsSupport.describeUnreadable(FILE, new IOException("disk problem")));
    }
}
