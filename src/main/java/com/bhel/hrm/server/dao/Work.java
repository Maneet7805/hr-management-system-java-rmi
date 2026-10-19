package com.bhel.hrm.server.dao;

/**
 * A piece of work to run against the database, given the DAOs to use.
 *
 * <p>{@code E} lets the work throw its own checked exception (a business rule being broken, for
 * example) and have it reach the caller unchanged, after the transaction has been rolled back.
 * Work that throws no checked exception needs no {@code try/catch} at the call site.
 */
@FunctionalInterface
public interface Work<T, E extends Exception> {

    T run(DataAccess dataAccess) throws E;
}
