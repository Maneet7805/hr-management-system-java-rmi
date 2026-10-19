package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EmployeeTest {

    private static Employee employee(String first, String last, String ic, String email) {
        return new Employee(1L, first, last, ic, Role.EMPLOYEE, "012-3456789", email, "1 Jalan Satu", false);
    }

    @Test
    void validEmployeeSurvivesSerialization() {
        Employee original = employee("Alice", "Tan", "900101-14-5678", "alice@bhel.example");

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void blankFirstNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> employee("  ", "Tan", "900101-14-5678", null));
    }

    @Test
    void nullLastNameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> employee("Alice", null, "900101-14-5678", null));
    }

    @Test
    void nameLongerThan50CharactersIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> employee("A".repeat(51), "Tan", "900101-14-5678", null));
    }

    @Test
    void icNumberShorterThan6CharactersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> employee("Alice", "Tan", "A1234", null));
    }

    @Test
    void icNumberLongerThan20CharactersIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> employee("Alice", "Tan", "1".repeat(21), null));
    }

    @Test
    void icNumberContainingSpacesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> employee("Alice", "Tan", "900101 14 5678", null));
    }

    @Test
    void passportNumberWithLettersAndDigitsIsAccepted() {
        assertDoesNotThrow(() -> employee("Alice", "Tan", "A1234567", null));
    }

    @Test
    void emailWithoutAtSignIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> employee("Alice", "Tan", "900101-14-5678", "alice.example"));
    }

    @Test
    void missingOrEmptyEmailIsAccepted() {
        assertDoesNotThrow(() -> employee("Alice", "Tan", "900101-14-5678", null));
        assertDoesNotThrow(() -> employee("Alice", "Tan", "900101-14-5678", ""));
    }

    @Test
    void forgedStreamWithBlankNameIsRejectedWhenRead() {
        byte[] bytes = SerializationSupport.toBytes(employee("Alice", "Tan", "900101-14-5678", null));
        byte[] name = "Alice".getBytes(StandardCharsets.UTF_8);
        int at = indexOf(bytes, name);
        for (int i = 0; i < name.length; i++) {
            bytes[at + i] = ' ';
        }

        assertThrows(IOException.class, () -> SerializationSupport.fromBytes(bytes));
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i <= data.length - pattern.length; i++) {
            int j = 0;
            while (j < pattern.length && data[i + j] == pattern[j]) {
                j++;
            }
            if (j == pattern.length) {
                return i;
            }
        }
        throw new IllegalStateException("pattern not found in stream");
    }
}
