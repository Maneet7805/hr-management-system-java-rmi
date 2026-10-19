package com.bhel.hrm.server.dao.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.Role;
import com.bhel.hrm.server.dao.DataAccessException;
import com.bhel.hrm.server.dao.NewEmployee;
import com.bhel.hrm.server.dao.NewLeaveApplication;
import com.bhel.hrm.server.dao.StoredCredentials;
import com.bhel.hrm.server.db.DatabaseTestBase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LeaveApplicationDaoTest extends DatabaseTestBase {

    private static final long ANNUAL = 1L;
    private static final long MEDICAL = 2L;
    private static final StoredCredentials CREDENTIALS =
            new StoredCredentials(new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, 1000);
    private static final LocalDateTime APPLIED_AT = LocalDateTime.of(2026, 3, 1, 9, 0);
    private static final LocalDateTime DECIDED_AT = LocalDateTime.of(2026, 3, 2, 14, 30);

    private static long employee(String icPassport, String first, String last, Role role) {
        return database.transaction(da -> da.employees()
                .insert(new NewEmployee(first, last, icPassport, role, false), CREDENTIALS).id());
    }

    private static long alice() {
        return employee("LV000001", "Alice", "Tan", Role.EMPLOYEE);
    }

    private static long hr() {
        return employee("LV900001", "Hana", "Lim", Role.HR);
    }

    private static LeaveApplication apply(long employeeId, long leaveTypeId, LocalDate start, LocalDate end,
            LocalDateTime appliedAt) {
        return database.transaction(da -> da.leave()
                .insert(new NewLeaveApplication(employeeId, leaveTypeId, start, end, "A reason", appliedAt)));
    }

    private static LeaveApplication apply(long employeeId, long leaveTypeId, LocalDate start, LocalDate end) {
        return apply(employeeId, leaveTypeId, start, end, APPLIED_AT);
    }

    private static LeaveApplication decide(long applicationId, LeaveStatus status, long decidedBy) {
        return database.transaction(da -> da.leave().decide(applicationId, status, decidedBy, DECIDED_AT))
                .orElseThrow();
    }

    private static boolean overlaps(long employeeId, LocalDate start, LocalDate end) {
        return database.read(da -> da.leave().overlaps(employeeId, start, end));
    }
    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(2026, month, dayOfMonth);
    }

    // ---- insert and find ----------------------------------------------------------------------

    @Test
    void insertReturnsTheFullApplication() {
        long alice = alice();

        LeaveApplication saved = apply(alice, ANNUAL, day(3, 10), day(3, 12));

        assertTrue(saved.id() > 0);
        assertEquals(alice, saved.employeeId());
        assertEquals("Alice Tan", saved.employeeName());
        assertEquals(new LeaveType(ANNUAL, "Annual", 14), saved.leaveType());
        assertEquals(day(3, 10), saved.startDate());
        assertEquals(day(3, 12), saved.endDate());
        assertEquals(3, saved.days());
        assertEquals("A reason", saved.reason());
        assertEquals(LeaveStatus.PENDING, saved.status());
        assertEquals(APPLIED_AT, saved.appliedAt());
        assertNull(saved.decidedAt());
    }

    @Test
    void aMissingReasonIsStoredAsNoReason() {
        long alice = alice();

        LeaveApplication saved = database.transaction(da -> da.leave()
                .insert(new NewLeaveApplication(alice, ANNUAL, day(3, 10), day(3, 10), null, APPLIED_AT)));

        assertNull(saved.reason());
        assertEquals(1, saved.days());
    }

    @Test
    void findByIdReturnsTheSameApplicationAndUnknownIdsAreEmpty() {
        LeaveApplication saved = apply(alice(), ANNUAL, day(3, 10), day(3, 12));

        assertEquals(Optional.of(saved), database.read(da -> da.leave().findById(saved.id())));
        assertEquals(Optional.empty(), database.read(da -> da.leave().findById(999_999L)));
    }

    @Test
    void anUnknownLeaveTypeIsRefusedWithoutSqlInTheMessage() {
        long alice = alice();

        DataAccessException error = assertThrows(DataAccessException.class,
                () -> apply(alice, 999L, day(3, 10), day(3, 12)));

        assertFalse(error.getMessage().toUpperCase().contains("INSERT"), error.getMessage());
    }

    @Test
    void findByEmployeeListsNewestStartFirstAndOnlyTheirOwn() {
        long alice = alice();
        long bob = employee("LV000002", "Bob", "Lee", Role.EMPLOYEE);
        LeaveApplication march = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        LeaveApplication june = apply(alice, ANNUAL, day(6, 1), day(6, 2));
        apply(bob, ANNUAL, day(4, 1), day(4, 1));

        List<LeaveApplication> mine = database.read(da -> da.leave().findByEmployee(alice));

        assertEquals(List.of(june, march), mine);
    }

    @Test
    void findByStatusReturnsOnlyThatStatusOldestAppliedFirst() {
        long alice = alice();
        long hr = hr();
        LeaveApplication later = apply(alice, ANNUAL, day(3, 10), day(3, 10), APPLIED_AT.plusDays(2));
        LeaveApplication earlier = apply(alice, ANNUAL, day(4, 10), day(4, 10), APPLIED_AT);
        LeaveApplication approved = apply(alice, ANNUAL, day(5, 10), day(5, 10), APPLIED_AT.plusDays(1));
        decide(approved.id(), LeaveStatus.APPROVED, hr);

        List<LeaveApplication> pending = database.read(da -> da.leave().findByStatus(LeaveStatus.PENDING));

        assertEquals(List.of(earlier, later), pending);
    }

    @Test
    void findByEmployeeAndYearUsesTheStartDateAndSortsByIt() {
        long alice = alice();
        LeaveApplication december = apply(alice, ANNUAL, LocalDate.of(2026, 12, 30), LocalDate.of(2026, 12, 31));
        LeaveApplication march = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        apply(alice, ANNUAL, LocalDate.of(2027, 1, 5), LocalDate.of(2027, 1, 6));

        List<LeaveApplication> in2026 = database.read(da -> da.leave().findByEmployeeAndYear(alice, 2026));

        assertEquals(List.of(march, december), in2026);
    }

    // ---- overlap ------------------------------------------------------------------------------

    @Test
    void overlapIsFoundWhenTheDatesShareAtLeastOneDay() {
        long alice = alice();
        apply(alice, ANNUAL, day(3, 10), day(3, 12));

        assertTrue(overlaps(alice, day(3, 11), day(3, 15)));
        assertTrue(overlaps(alice, day(3, 1), day(3, 10)), "touching on the first day");
        assertTrue(overlaps(alice, day(3, 12), day(3, 20)), "touching on the last day");
        assertTrue(overlaps(alice, day(3, 11), day(3, 11)), "inside");
        assertTrue(overlaps(alice, day(3, 1), day(3, 31)), "around");
    }

    @Test
    void noOverlapForTheDaysJustBeforeOrAfter() {
        long alice = alice();
        apply(alice, ANNUAL, day(3, 10), day(3, 12));

        assertFalse(overlaps(alice, day(3, 1), day(3, 9)));
        assertFalse(overlaps(alice, day(3, 13), day(3, 20)));
    }

    @Test
    void approvedLeaveCountsButRejectedLeaveDoesNot() {
        long alice = alice();
        long hr = hr();
        LeaveApplication approved = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        LeaveApplication rejected = apply(alice, ANNUAL, day(5, 10), day(5, 12));
        decide(approved.id(), LeaveStatus.APPROVED, hr);
        decide(rejected.id(), LeaveStatus.REJECTED, hr);

        assertTrue(overlaps(alice, day(3, 11), day(3, 11)));
        assertFalse(overlaps(alice, day(5, 11), day(5, 11)));
    }

    @Test
    void anotherEmployeesLeaveDoesNotCountAsOverlap() {
        long alice = alice();
        long bob = employee("LV000002", "Bob", "Lee", Role.EMPLOYEE);
        apply(bob, ANNUAL, day(3, 10), day(3, 12));

        assertFalse(overlaps(alice, day(3, 10), day(3, 12)));
    }

    // ---- days used ----------------------------------------------------------------------------

    @Test
    void daysUsedAddsUpPerLeaveTypeForOneEmployeeYearAndStatus() {
        long alice = alice();
        long bob = employee("LV000002", "Bob", "Lee", Role.EMPLOYEE);
        long hr = hr();
        decide(apply(alice, ANNUAL, day(3, 10), day(3, 12)).id(), LeaveStatus.APPROVED, hr);       // 3 annual
        decide(apply(alice, ANNUAL, day(4, 1), day(4, 2)).id(), LeaveStatus.APPROVED, hr);         // 2 annual
        decide(apply(alice, MEDICAL, day(5, 5), day(5, 5)).id(), LeaveStatus.APPROVED, hr);        // 1 medical
        apply(alice, ANNUAL, day(6, 1), day(6, 4));                                                // pending, not counted
        decide(apply(alice, ANNUAL, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 7)).id(),
                LeaveStatus.APPROVED, hr);                                                         // other year
        decide(apply(bob, ANNUAL, day(3, 10), day(3, 20)).id(), LeaveStatus.APPROVED, hr);         // other employee

        Map<Long, Integer> approved = database.read(da -> da.leave().daysUsed(alice, 2026, LeaveStatus.APPROVED));
        Map<Long, Integer> pending = database.read(da -> da.leave().daysUsed(alice, 2026, LeaveStatus.PENDING));

        assertEquals(Map.of(ANNUAL, 5, MEDICAL, 1), approved);
        assertEquals(Map.of(ANNUAL, 4), pending);
    }

    @Test
    void daysUsedIsEmptyWhenThereIsNoLeave() {
        long alice = alice();

        assertTrue(database.read(da -> da.leave().daysUsed(alice, 2026, LeaveStatus.APPROVED)).isEmpty());
    }

    // ---- deciding -----------------------------------------------------------------------------

    @Test
    void approvingSetsTheStatusTheTimeAndWhoDecided() throws SQLException {
        long hr = hr();
        LeaveApplication pending = apply(alice(), ANNUAL, day(3, 10), day(3, 12));

        LeaveApplication approved = decide(pending.id(), LeaveStatus.APPROVED, hr);

        assertEquals(LeaveStatus.APPROVED, approved.status());
        assertEquals(DECIDED_AT, approved.decidedAt());
        assertEquals(hr, decidedByInDatabase(pending.id()));
    }

    @Test
    void rejectingSetsTheStatus() {
        long hr = hr();
        LeaveApplication pending = apply(alice(), ANNUAL, day(3, 10), day(3, 12));

        assertEquals(LeaveStatus.REJECTED, decide(pending.id(), LeaveStatus.REJECTED, hr).status());
    }

    @Test
    void anApplicationAlreadyDecidedCannotBeDecidedAgain() {
        long hr = hr();
        LeaveApplication pending = apply(alice(), ANNUAL, day(3, 10), day(3, 12));
        decide(pending.id(), LeaveStatus.APPROVED, hr);

        Optional<LeaveApplication> second = database.transaction(
                da -> da.leave().decide(pending.id(), LeaveStatus.REJECTED, hr, DECIDED_AT.plusHours(1)));

        assertTrue(second.isEmpty());
        assertEquals(LeaveStatus.APPROVED, database.read(da -> da.leave().findById(pending.id())).orElseThrow().status());
    }

    @Test
    void decidingAnUnknownApplicationReturnsEmpty() {
        long hr = hr();

        assertTrue(database.transaction(da -> da.leave().decide(999_999L, LeaveStatus.APPROVED, hr, DECIDED_AT))
                .isEmpty());
    }

    @Test
    void pendingIsNotADecision() {
        long hr = hr();
        LeaveApplication pending = apply(alice(), ANNUAL, day(3, 10), day(3, 12));

        assertThrows(IllegalArgumentException.class,
                () -> database.transaction(da -> da.leave().decide(pending.id(), LeaveStatus.PENDING, hr, DECIDED_AT)));
    }

    /** Two HR users clicking at the same moment: exactly one of them must win. */
    @Test
    @Timeout(30)
    void whenTwoDecideAtTheSameTimeExactlyOneWins() throws Exception {
        long hr = hr();
        LeaveApplication pending = apply(alice(), ANNUAL, day(3, 10), day(3, 12));
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> approve = () -> decideAfterBarrier(startTogether, pending.id(), LeaveStatus.APPROVED, hr);
            Callable<Boolean> reject = () -> decideAfterBarrier(startTogether, pending.id(), LeaveStatus.REJECTED, hr);
            Future<Boolean> first = pool.submit(approve);
            Future<Boolean> second = pool.submit(reject);

            int winners = (first.get(20, TimeUnit.SECONDS) ? 1 : 0) + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }

    private static boolean decideAfterBarrier(CyclicBarrier barrier, long applicationId, LeaveStatus status, long hr)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return database.transaction(da -> da.leave().decide(applicationId, status, hr, DECIDED_AT)).isPresent();
    }

    private static long decidedByInDatabase(long applicationId) throws SQLException {
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT decided_by FROM leave_application WHERE id = ?")) {
            statement.setLong(1, applicationId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
