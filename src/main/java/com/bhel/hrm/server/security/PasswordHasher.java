package com.bhel.hrm.server.security;

import com.bhel.hrm.server.dao.StoredCredentials;

/** Turns a password into something safe to store, and checks a password against it later. */
public interface PasswordHasher {

    /** A new salted hash of the password. Hashing the same password twice gives different results. */
    StoredCredentials hash(String password);

    /** Whether the password matches what was stored. */
    boolean verify(String password, StoredCredentials stored);
}
