package com.bhel.hrm.common.exception;

/**
 * Base class for every error the server reports to a client.
 * It is a checked exception so the client must handle it. The message is written for the user,
 * and there is deliberately no "cause" constructor, so SQL or stack details never cross the network.
 */
public class HrmException extends Exception {

    private static final long serialVersionUID = 1L;

    public HrmException(String message) {
        super(message);
    }
}
