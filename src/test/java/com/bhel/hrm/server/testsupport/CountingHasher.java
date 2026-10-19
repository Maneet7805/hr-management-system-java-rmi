package com.bhel.hrm.server.testsupport;

import com.bhel.hrm.server.dao.StoredCredentials;
import com.bhel.hrm.server.security.PasswordHasher;
import com.bhel.hrm.server.security.Pbkdf2PasswordHasher;
import java.util.concurrent.atomic.AtomicInteger;

/** A real hasher with few rounds that also counts how often it was asked to check a password. */
public final class CountingHasher implements PasswordHasher {

    private final PasswordHasher real = new Pbkdf2PasswordHasher(1000);
    private final AtomicInteger verifications = new AtomicInteger();

    @Override
    public StoredCredentials hash(String password) {
        return real.hash(password);
    }

    @Override
    public boolean verify(String password, StoredCredentials stored) {
        verifications.incrementAndGet();
        return real.verify(password, stored);
    }

    public int verifications() {
        return verifications.get();
    }
}
