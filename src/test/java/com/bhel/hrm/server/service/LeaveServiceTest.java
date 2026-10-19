package com.bhel.hrm.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bhel.hrm.common.exception.InvalidCredentialsException;
import com.bhel.hrm.common.exception.NotFoundException;
import com.bhel.hrm.common.exception.PermissionDeniedException;
import com.bhel.hrm.common.exception.ValidationException;
import com.bhel.hrm.common.model.LeaveApplication;
import com.bhel.hrm.common.model.LeaveBalance;
import com.bhel.hrm.common.model.LeaveStatus;
import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.common.model.RegistrationResult;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class LeaveServiceTest extends ServiceTestBase {

    private static final long ANNUAL = 1L;      // 14 days a year
    private static final long MEDICAL = 2L;     // 14 days a year
    private static final long EMERGENCY = 3L;   // 3 days a year
    private static final long UNPAID = 4L;      // no limit

    private String hr;
    private String alice;

    @BeforeEach
    void people() throws Exception {
        hr = hrToken();
        alice = employeeToken(hr, "Alice", "Tan", "AB123456");
    }

    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(2026, month, dayOfMonth);
    }

    private LeaveApplication apply(String token, long type, LocalDate start, LocalDate end) throws Exception {
        return leave.applyForLeave(token, type, start, end, "A reason");
    }

    private LeaveBalance balanceOf(String token, long type, int year) throws Exception {
        return leave.getMyLeaveBalances(token, year).stream()
                .filter(balance -> balance.leaveType().id() == type).findFirst().orElseThrow();
    }

    // ---- leave types --------------------------------------------------------------------------

    @Test
    void anEmployeeSeesTheFourLeaveTypes() throws Exception {
        List<LeaveType> types = leave.getLeaveTypes(alice);

        assertEquals(List.of("Annual", "Medical", "Emergency", "Unpaid"), types.stream().map(LeaveType::name).toList());
    }

    @Test
    void hrStaffCannotUseTheEmployeeLeaveMethods() {
        assertEquals("Only employees can do this.",
                assertThrows(PermissionDeniedException.class, () -> leave.getLeaveTypes(hr)).getMessage());
        assertThrows(PermissionDeniedException.class, () -> leave.getMyLeaveBalances(hr, 2026));
        assertThrows(PermissionDeniedException.class, () -> apply(hr, ANNUAL, day(3, 10), day(3, 12)));
        assertThrows(PermissionDeniedException.class, () -> leave.getMyLeaveApplications(hr));
    }

    @Test
    void leaveMethodsNeedALiveSession() {
        assertThrows(InvalidCredentialsException.class, () -> leave.getLeaveTypes("no-such-token"));
        assertThrows(InvalidCredentialsException.class, () -> apply(null, ANNUAL, day(3, 10), day(3, 12)));
    }

    @Test
    void someoneOnATemporaryPasswordCannotApplyForLeave() throws Exception {
        RegistrationResult registered = employees.registerEmployee(hr, "Bob", "Lee", "CD123456");
        String temporary = auth.login("CD123456", registered.temporaryPassword()).token();

        assertThrows(PermissionDeniedException.class, () -> apply(temporary, ANNUAL, day(3, 10), day(3, 12)));
    }

    // ---- balances -----------------------------------------------------------------------------

    @Test
    void aNewEmployeeHasTheFullAllowanceOfEveryType() throws Exception {
        List<LeaveBalance> balances = leave.getMyLeaveBalances(alice, 2026);

        assertEquals(4, balances.size());
        assertEquals(14, balanceOf(alice, ANNUAL, 2026).remaining());
        assertEquals(14, balanceOf(alice, MEDICAL, 2026).remaining());
        assertEquals(3, balanceOf(alice, EMERGENCY, 2026).remaining());
        assertNull(balanceOf(alice, UNPAID, 2026).remaining(), "an uncapped type has no 'remaining'");
    }

    @Test
    void pendingDaysAlreadyReduceTheBalance() throws Exception {
        apply(alice, ANNUAL, day(3, 10), day(3, 12));

        LeaveBalance balance = balanceOf(alice, ANNUAL, 2026);

        assertEquals(0, balance.approvedDays());
        assertEquals(3, balance.pendingDays());
        assertEquals(11, balance.remaining());
    }

    @Test
    void approvedDaysReduceTheBalanceAndRejectedDaysGiveItBack() throws Exception {
        LeaveApplication approved = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        LeaveApplication rejected = apply(alice, ANNUAL, day(5, 4), day(5, 8));
        leave.decideLeave(hr, approved.id(), true);
        leave.decideLeave(hr, rejected.id(), false);

        LeaveBalance balance = balanceOf(alice, ANNUAL, 2026);

        assertEquals(3, balance.approvedDays());
        assertEquals(0, balance.pendingDays());
        assertEquals(11, balance.remaining());
    }

    @Test
    void eachYearHasItsOwnBalance() throws Exception {
        apply(alice, ANNUAL, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 5));

        assertEquals(14, balanceOf(alice, ANNUAL, 2026).remaining());
        assertEquals(9, balanceOf(alice, ANNUAL, 2027).remaining());
    }

    @Test
    void aYearOutsideTheSensibleRangeIsRefused() {
        assertEquals("Year must be between 2000 and 2100.", assertThrows(ValidationException.class,
                () -> leave.getMyLeaveBalances(alice, 1999)).getMessage());
        assertThrows(ValidationException.class, () -> leave.getMyLeaveBalances(alice, 2101));
    }

    // ---- applying -----------------------------------------------------------------------------

    @Test
    void aValidApplicationIsSavedAsPendingWithTheCurrentTime() throws Exception {
        LeaveApplication saved = leave.applyForLeave(alice, ANNUAL, day(3, 10), day(3, 12), "  Family trip ");

        assertTrue(saved.id() > 0);
        assertEquals("Alice Tan", saved.employeeName());
        assertEquals("Annual", saved.leaveType().name());
        assertEquals(3, saved.days());
        assertEquals("Family trip", saved.reason());
        assertEquals(LeaveStatus.PENDING, saved.status());
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 0), saved.appliedAt());
        assertNull(saved.decidedAt());
    }

    @Test
    void aBlankReasonIsStoredAsNoReason() throws Exception {
        assertNull(leave.applyForLeave(alice, ANNUAL, day(3, 10), day(3, 10), "   ").reason());
        assertNull(leave.applyForLeave(alice, ANNUAL, day(3, 11), day(3, 11), null).reason());
    }

    @Test
    void anEndDateBeforeTheStartDateIsRefusedWithTheReason() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> apply(alice, ANNUAL, day(3, 12), day(3, 10)));

        assertTrue(error.getMessage().contains("End date must not be before"), error.getMessage());
    }

    @Test
    void leaveAcrossNewYearIsRefused() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> apply(alice, ANNUAL, day(12, 30), LocalDate.of(2027, 1, 2)));

        assertTrue(error.getMessage().contains("one calendar year"), error.getMessage());
    }

    @Test
    void aReasonLongerThan200CharactersIsRefused() {
        assertEquals("Reason must be at most 200 characters.", assertThrows(ValidationException.class,
                () -> leave.applyForLeave(alice, ANNUAL, day(3, 10), day(3, 12), "x".repeat(201))).getMessage());
    }

    @Test
    void missingDatesAreRefused() {
        assertEquals("Start date and end date are required.", assertThrows(ValidationException.class,
                () -> apply(alice, ANNUAL, null, day(3, 12))).getMessage());
        assertThrows(ValidationException.class, () -> apply(alice, ANNUAL, day(3, 10), null));
    }

    @Test
    void anUnknownLeaveTypeIsNotFound() {
        assertEquals("Leave type not found.", assertThrows(NotFoundException.class,
                () -> apply(alice, 999L, day(3, 10), day(3, 12))).getMessage());
    }

    @Test
    void pastDatesAreAllowedBecauseMedicalCertificatesOftenComeLater() throws Exception {
        LeaveApplication saved = apply(alice, MEDICAL, LocalDate.of(2020, 3, 10), LocalDate.of(2020, 3, 11));

        assertEquals(2, saved.days());
    }

    @Test
    void aYearOutsideTheSensibleRangeIsRefusedWhenApplying() {
        assertEquals("Year must be between 2000 and 2100.", assertThrows(ValidationException.class,
                () -> apply(alice, ANNUAL, LocalDate.of(1999, 5, 1), LocalDate.of(1999, 5, 2))).getMessage());
    }

    // ---- overlap ------------------------------------------------------------------------------

    @Test
    void leaveOverlappingPendingLeaveIsRefused() throws Exception {
        apply(alice, ANNUAL, day(3, 10), day(3, 12));

        assertEquals("You already have leave on some of those dates.", assertThrows(ValidationException.class,
                () -> apply(alice, MEDICAL, day(3, 12), day(3, 14))).getMessage());
    }

    @Test
    void leaveOverlappingApprovedLeaveIsRefusedEvenForAnotherType() throws Exception {
        LeaveApplication approved = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        leave.decideLeave(hr, approved.id(), true);

        assertThrows(ValidationException.class, () -> apply(alice, UNPAID, day(3, 9), day(3, 10)));
    }

    @Test
    void theDaysJustBeforeAndAfterExistingLeaveAreFree() throws Exception {
        apply(alice, ANNUAL, day(3, 10), day(3, 12));

        apply(alice, ANNUAL, day(3, 8), day(3, 9));
        apply(alice, ANNUAL, day(3, 13), day(3, 14));
    }

    @Test
    void rejectedLeaveDoesNotBlockTheSameDates() throws Exception {
        LeaveApplication first = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        leave.decideLeave(hr, first.id(), false);

        assertEquals(LeaveStatus.PENDING, apply(alice, ANNUAL, day(3, 10), day(3, 12)).status());
    }

    @Test
    void anotherEmployeesLeaveNeverBlocksMe() throws Exception {
        String bob = employeeToken(hr, "Bob", "Lee", "CD123456");
        apply(bob, ANNUAL, day(3, 10), day(3, 12));

        assertEquals(LeaveStatus.PENDING, apply(alice, ANNUAL, day(3, 10), day(3, 12)).status());
    }

    // ---- balance rule -------------------------------------------------------------------------

    @Test
    void askingForMoreDaysThanAreLeftIsRefusedWithTheNumbers() {
        ValidationException error = assertThrows(ValidationException.class,
                () -> apply(alice, ANNUAL, day(1, 1), day(1, 15)));

        assertEquals("Not enough Annual leave left: 14 day(s) remaining, 15 requested.", error.getMessage());
    }

    @Test
    void exactlyTheRemainingDaysAreAcceptedAndNotOneMore() throws Exception {
        apply(alice, ANNUAL, day(1, 1), day(1, 14));

        assertEquals(0, balanceOf(alice, ANNUAL, 2026).remaining());
        assertThrows(ValidationException.class, () -> apply(alice, ANNUAL, day(2, 1), day(2, 1)));
    }

    @Test
    void approvedAndPendingDaysBothCountAgainstTheBalance() throws Exception {
        LeaveApplication big = apply(alice, ANNUAL, day(1, 1), day(1, 10));
        leave.decideLeave(hr, big.id(), true);
        apply(alice, ANNUAL, day(2, 1), day(2, 3));   // 13 used, 1 left

        assertThrows(ValidationException.class, () -> apply(alice, ANNUAL, day(3, 1), day(3, 2)));
        assertEquals(LeaveStatus.PENDING, apply(alice, ANNUAL, day(3, 1), day(3, 1)).status());
    }

    @Test
    void anUncappedTypeNeverRunsOut() throws Exception {
        LeaveApplication longLeave = apply(alice, UNPAID, day(1, 1), day(4, 10));

        assertEquals(100, longLeave.days());
        assertNull(balanceOf(alice, UNPAID, 2026).remaining());
    }

    @Test
    void eachLeaveTypeHasItsOwnBalance() throws Exception {
        apply(alice, ANNUAL, day(1, 1), day(1, 14));

        assertEquals(LeaveStatus.PENDING, apply(alice, MEDICAL, day(2, 1), day(2, 14)).status());
    }

    // ---- races --------------------------------------------------------------------------------

    /** The apply-leave rule (#20): the check and the save must not interleave between two requests. */
    @Test
    @Timeout(60)
    void tenRequestsAtOnceForTheLastThreeEmergencyDaysGetExactlyThree() throws Exception {
        long aliceId = employees.getMyProfile(alice).id();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CyclicBarrier startTogether = new CyclicBarrier(10);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                LocalDate singleDay = day(6, 1 + i);   // ten different days, so only the balance can refuse
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    try {
                        leave.applyForLeave(alice, EMERGENCY, singleDay, singleDay, "race");
                        return true;
                    } catch (ValidationException refused) {
                        assertTrue(refused.getMessage().startsWith("Not enough Emergency leave left"),
                                refused.getMessage());
                        return false;
                    }
                }));
            }
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(40, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            Map<Long, Integer> pending = database.read(
                    da -> da.leave().daysUsed(aliceId, 2026, LeaveStatus.PENDING));
            assertEquals(3, accepted);
            assertEquals(3, pending.get(EMERGENCY));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    void fiveRequestsAtOnceForTheSameDatesGetExactlyOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CyclicBarrier startTogether = new CyclicBarrier(5);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    try {
                        apply(alice, ANNUAL, day(3, 10), day(3, 12));
                        return true;
                    } catch (ValidationException refused) {
                        assertEquals("You already have leave on some of those dates.", refused.getMessage());
                        return false;
                    }
                }));
            }
            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(40, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            assertEquals(1, accepted);
            assertEquals(1, leave.getMyLeaveApplications(alice).size());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- my applications ----------------------------------------------------------------------

    @Test
    void myApplicationsAreMyOwnNewestStartFirst() throws Exception {
        String bob = employeeToken(hr, "Bob", "Lee", "CD123456");
        LeaveApplication march = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        LeaveApplication june = apply(alice, ANNUAL, day(6, 1), day(6, 2));
        apply(bob, ANNUAL, day(4, 1), day(4, 1));

        assertEquals(List.of(june, march), leave.getMyLeaveApplications(alice));
    }

    // ---- HR: pending list and decisions -------------------------------------------------------

    @Test
    void hrSeesPendingApplicationsOfEveryoneOldestFirstAndDecidedOnesDisappear() throws Exception {
        String bob = employeeToken(hr, "Bob", "Lee", "CD123456");
        LeaveApplication first = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        clock.advance(Duration.ofMinutes(10));
        LeaveApplication second = apply(bob, MEDICAL, day(4, 1), day(4, 2));
        clock.advance(Duration.ofMinutes(10));
        LeaveApplication third = apply(alice, ANNUAL, day(5, 1), day(5, 1));
        leave.decideLeave(hr, third.id(), true);

        List<LeaveApplication> pending = leave.listPendingLeave(hr);

        assertEquals(List.of(first.id(), second.id()), pending.stream().map(LeaveApplication::id).toList());
        assertEquals(List.of("Alice Tan", "Bob Lee"), pending.stream().map(LeaveApplication::employeeName).toList());
    }

    @Test
    void anEmployeeCannotSeeThePendingListOrDecide() throws Exception {
        LeaveApplication pending = apply(alice, ANNUAL, day(3, 10), day(3, 12));

        assertEquals("Only HR staff can do this.", assertThrows(PermissionDeniedException.class,
                () -> leave.listPendingLeave(alice)).getMessage());
        assertThrows(PermissionDeniedException.class, () -> leave.decideLeave(alice, pending.id(), true));
        assertEquals(LeaveStatus.PENDING, leave.getMyLeaveApplications(alice).get(0).status());
    }

    @Test
    void hrApprovesAnApplicationAndTheEmployeeSeesIt() throws Exception {
        LeaveApplication pending = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        clock.advance(Duration.ofMinutes(20));

        LeaveApplication approved = leave.decideLeave(hr, pending.id(), true);

        assertEquals(LeaveStatus.APPROVED, approved.status());
        assertEquals(LocalDateTime.of(2026, 3, 1, 9, 20), approved.decidedAt());
        assertEquals(approved, leave.getMyLeaveApplications(alice).get(0));
    }

    @Test
    void hrRejectsAnApplication() throws Exception {
        LeaveApplication pending = apply(alice, ANNUAL, day(3, 10), day(3, 12));

        assertEquals(LeaveStatus.REJECTED, leave.decideLeave(hr, pending.id(), false).status());
    }

    @Test
    void anApplicationCannotBeDecidedTwice() throws Exception {
        LeaveApplication pending = apply(alice, ANNUAL, day(3, 10), day(3, 12));
        leave.decideLeave(hr, pending.id(), true);

        ValidationException error = assertThrows(ValidationException.class,
                () -> leave.decideLeave(hr, pending.id(), false));

        assertEquals("This application has already been decided.", error.getMessage());
        assertEquals(LeaveStatus.APPROVED, leave.getMyLeaveApplications(alice).get(0).status());
    }

    @Test
    void decidingAnApplicationThatDoesNotExistIsNotFound() {
        assertEquals("Leave application not found.", assertThrows(NotFoundException.class,
                () -> leave.decideLeave(hr, 999_999L, true)).getMessage());
    }

    @Test
    void decidingNeedsALiveSession() {
        assertThrows(InvalidCredentialsException.class, () -> leave.decideLeave("no-such-token", 1L, true));
    }
}
