package com.bhel.hrm.client;

import com.bhel.hrm.common.exception.HrmException;
import com.bhel.hrm.common.exception.InvalidCredentialsException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns anything that can go wrong into the one sentence shown to the user. Messages written by the
 * server are already meant for people and are shown as they are. Network and program errors get fixed
 * sentences, so no exception name, class name or stack trace ever reaches the screen.
 */
public final class ErrorMessages {

    /**
     * The server's wording for "no such session". The client recognises an expired session by it, so a
     * test checks it against the text the server really sends.
     */
    public static final String SESSION_EXPIRED = "Your session has expired. Please log in again.";

    private static final String CONNECTION_LOST = "The connection to the HR server was lost. "
            + "If you were saving something, check whether it was saved before trying again.";
    private static final String PROGRAM_ERROR = "Something went wrong in the program. Please try again.";
    private static final String REFUSED = "The request was refused.";

    private static final Logger LOG = Logger.getLogger(ErrorMessages.class.getName());

    private ErrorMessages() {
    }

    public static String forUser(Throwable failure) {
        if (failure instanceof HrmException) {
            String message = failure.getMessage();
            return message == null || message.isBlank() ? REFUSED : message;
        }
        if (failure instanceof RemoteException) {
            return CONNECTION_LOST;
        }
        // A bug, not something the user caused: keep the detail in the log, not on the screen.
        LOG.log(Level.SEVERE, "Unexpected error in the client", failure);
        return PROGRAM_ERROR;
    }

    /** True when the server says the login is over (idle too long, or the server restarted). */
    public static boolean isSessionExpired(Throwable failure) {
        return failure instanceof InvalidCredentialsException && SESSION_EXPIRED.equals(failure.getMessage());
    }
}
