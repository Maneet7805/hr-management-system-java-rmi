package com.bhel.hrm.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateInputTest {

    @Test
    void aWellFormedDateIsParsedAndSpacesAroundItAreIgnored() {
        assertEquals(LocalDate.of(2026, 3, 15), DateInput.required("2026-03-15", "Start date"));
        assertEquals(LocalDate.of(2026, 3, 15), DateInput.required("  2026-03-15  ", "Start date"));
    }

    @Test
    void aRequiredDateThatIsMissingAsksForIt() {
        assertEquals("Enter the start date.",
                assertThrows(IllegalArgumentException.class, () -> DateInput.required("", "Start date")).getMessage());
        assertEquals("Enter the end date.",
                assertThrows(IllegalArgumentException.class, () -> DateInput.required("   ", "End date")).getMessage());
        assertThrows(IllegalArgumentException.class, () -> DateInput.required(null, "Start date"));
    }

    @Test
    void textThatIsNotADateExplainsTheFormatWithAnExample() {
        String message = assertThrows(IllegalArgumentException.class,
                () -> DateInput.required("15/03/2026", "Start date")).getMessage();

        assertEquals("Start date must be a real date in the form yyyy-MM-dd, for example 2026-03-15.", message);
    }

    @Test
    void aDateThatDoesNotExistIsRefusedWithTheSameHelp() {
        assertThrows(IllegalArgumentException.class, () -> DateInput.required("2026-02-30", "Start date"));
        assertThrows(IllegalArgumentException.class, () -> DateInput.required("2026-13-01", "Start date"));
        assertThrows(IllegalArgumentException.class, () -> DateInput.required("2026-3-5", "Start date"));
    }

    @Test
    void anOptionalDateMayBeLeftEmpty() {
        assertNull(DateInput.optional("", "Date of birth"));
        assertNull(DateInput.optional("   ", "Date of birth"));
        assertNull(DateInput.optional(null, "Date of birth"));
        assertEquals(LocalDate.of(1990, 5, 17), DateInput.optional("1990-05-17", "Date of birth"));
    }

    @Test
    void anOptionalDateThatIsWrittenWrongIsStillRefused() {
        assertThrows(IllegalArgumentException.class, () -> DateInput.optional("yesterday", "Date of birth"));
    }
}
