package com.bhel.hrm.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import com.bhel.hrm.common.model.FamilyMember;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RequestFiltersTest {

    /** Reads an object the way the server does: through the filter. */
    private static Object readThroughFilter(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            in.setObjectInputFilter(RequestFilters.contractFilter());
            return in.readObject();
        }
    }

    @Test
    void acceptsAContractDataClassWithADate() throws Exception {
        FamilyMember member = new FamilyMember(1L, "Bob Tan", "Spouse", LocalDate.of(1990, 5, 17));

        assertEquals(member, readThroughFilter(SerializationSupport.toBytes(member)));
    }

    @Test
    void acceptsAListOfContractClasses() throws Exception {
        List<FamilyMember> members = List.of(new FamilyMember(1L, "Bob Tan", "Spouse", null));

        assertEquals(members, readThroughFilter(SerializationSupport.toBytes(members)));
    }

    @Test
    void acceptsPlainTextAndNumbers() throws Exception {
        assertEquals("hello", readThroughFilter(SerializationSupport.toBytes("hello")));
        assertEquals(42L, readThroughFilter(SerializationSupport.toBytes(42L)));
    }

    @Test
    void rejectsAClassOutsideTheAllowedPackages() {
        byte[] bytes = SerializationSupport.toBytes(new AtomicInteger(7));

        assertThrows(InvalidClassException.class, () -> readThroughFilter(bytes));
    }

    @Test
    void rejectsAnOversizeArray() {
        byte[] bytes = SerializationSupport.toBytes(new byte[200_000]);

        assertThrows(InvalidClassException.class, () -> readThroughFilter(bytes));
    }
}
