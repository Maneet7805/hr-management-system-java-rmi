package com.bhel.hrm.server.dao;

/**
 * The only way the service layer reaches the data. Each call opens one connection, runs the work
 * with DAOs bound to it, and closes it again.
 *
 * <p>It is an interface so service tests can use a fake without MySQL.
 */
public interface Database {

    /** Runs work that only reads, or that needs no all-or-nothing guarantee. Each statement commits at once. */
    <T, E extends Exception> T read(Work<T, E> work) throws E;

    /**
     * Runs the work as one all-or-nothing unit: everything is committed if the work returns,
     * and everything is undone if it throws anything. Use it whenever two or more changes belong together,
     * or when a row lock is needed.
     */
    <T, E extends Exception> T transaction(Work<T, E> work) throws E;
}
