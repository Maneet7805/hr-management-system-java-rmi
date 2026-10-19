package com.bhel.hrm.common.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bhel.hrm.common.SerializationSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class YearlyReportTest {

    private static YearlyReport report() {
        return new YearlyReport(Samples.employee(), 2026,
                List.of(Samples.spouse()),
                List.of(Samples.application()),
                List.of(new LeaveBalance(Samples.annual(), 2026, 0, 3)));
    }

    @Test
    void reportSurvivesSerializationInOneObject() {
        YearlyReport original = report();

        assertEquals(original, SerializationSupport.roundTrip(original));
    }

    @Test
    void changingTheCallersListAfterwardsDoesNotChangeTheReport() {
        List<FamilyMember> family = new ArrayList<>(List.of(Samples.spouse()));
        YearlyReport report = new YearlyReport(Samples.employee(), 2026, family, List.of(), List.of());

        family.clear();

        assertEquals(1, report.familyMembers().size());
    }

    @Test
    void theReportListsCannotBeModified() {
        assertThrows(UnsupportedOperationException.class, () -> report().leaveApplications().clear());
    }

    @Test
    void missingListIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new YearlyReport(Samples.employee(), 2026, null, List.of(), List.of()));
    }
}
