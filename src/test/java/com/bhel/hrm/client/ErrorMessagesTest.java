package com.bhel.hrm.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.server.security.SessionManager;
import com.bhel.hrm.server.service.AuthService;
import com.bhel.hrm.server.testsupport.CountingHasher;
import com.bhel.hrm.server.testsupport.FakeDatabase;
import com.bhel.hrm.server.testsupport.MutableClock;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.UnmarshalException;
import org.junit.jupiter.api.Test;

class ErrorMessagesTest {

    @Test
    void messagesWrittenByTheServerAreShownAsTheyAre() {
        assertEquals("Not enough Annual leave left: 3 day(s) remaining, 5 requested.",
                ErrorMessages.forUser(new ValidationException("Not enough Annual leave left: 3 day(s) remaining, 5 requested.")));
        assertEquals("Leave type not found.", ErrorMessages.forUser(new NotFoundException("Leave type not found.")));
        assertEquals("Cannot reach the HR server at localhost:1099. Check that it is running and try again.",
                ErrorMessages.forUser(new ServiceUnavailableException(
                        "Cannot reach the HR server at localhost:1099. Check that it is running and try again.")));
    }

    @Test
    void everyKindOfNetworkFailureGetsTheSameCalmSentenceWithoutJargon() {
        String lost = ErrorMessages.forUser(new ConnectException("Connection refused: connect"));

        assertEquals(lost, ErrorMessages.forUser(new NoSuchObjectException("no such object in table")));
        assertEquals(lost, ErrorMessages.forUser(new UnmarshalException("error unmarshalling return")));
        assertTrue(lost.contains("connection to the HR server was lost"), lost);
        assertTrue(lost.contains("check whether it was saved"), lost);
        assertFalse(lost.contains("Exception"));
        assertFalse(lost.contains("unmarshal"));
    }

    @Test
    void anUnexpectedProgramErrorGetsAGenericSentenceAndLeaksNothing() {
        String message = ErrorMessages.forUser(new IllegalStateException("secret internal detail"));

        assertEquals("Something went wrong in the program. Please try again.", message);
    }

    @Test
    void anExceptionWithoutAMessageStillGivesASentence() {
        assertEquals("Something went wrong in the program. Please try again.",
                ErrorMessages.forUser(new NullPointerException()));
        assertEquals("The request was refused.", ErrorMessages.forUser(new ValidationException(null)));
    }

    @Test
    void anExpiredSessionIsRecognisedButAWrongPasswordIsNot() {
        assertTrue(ErrorMessages.isSessionExpired(new InvalidCredentialsException(ErrorMessages.SESSION_EXPIRED)));
        assertFalse(ErrorMessages.isSessionExpired(new InvalidCredentialsException("Your current password is wrong.")));
        assertFalse(ErrorMessages.isSessionExpired(new InvalidCredentialsException("Wrong ID or password.")));
        assertFalse(ErrorMessages.isSessionExpired(new ValidationException(ErrorMessages.SESSION_EXPIRED)));
    }

    /** The client recognises an expired session by the server's wording, so the two must never drift apart. */
    @Test
    void theExpiredSessionTextEqualsTheTextTheServerActuallySends() {
        MutableClock clock = new MutableClock();
        AuthService auth = new AuthService(new FakeDatabase(), new CountingHasher(), new SessionManager(clock));

        InvalidCredentialsException fromServer = assertThrows(InvalidCredentialsException.class,
                () -> auth.requireSession("no-such-token"));

        assertEquals(fromServer.getMessage(), ErrorMessages.SESSION_EXPIRED);
    }
}
