package com.bhel.hrm.server.service;

import com.bhel.hrm.common.exception.ServiceUnavailableException;
import com.bhel.hrm.server.dao.DataAccessException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Turns a database failure into the one message a client is allowed to see. */
public final class ServiceErrors {

    private static final Logger LOG = Logger.getLogger(ServiceErrors.class.getName());

    private ServiceErrors() {
    }

    /**
     * The real cause goes to the server log only. The client gets a fixed sentence, so SQL text,
     * table names or connection details never reach it.
     */
    public static ServiceUnavailableException unavailable(DataAccessException cause) {
        LOG.log(Level.SEVERE, "Database problem", cause);
        return new ServiceUnavailableException("The service is temporarily unavailable. Try again later.");
    }
}
