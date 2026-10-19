# Phase 5: HR and employee features

Status: **Approved by delegation** (the owner left the open choices to the assistant, 2026-10-19). Built in two parts: **5a** (employees and family) and **5b** (leave and reports).

**5a is built** on branch `feature/employee-features`: `EmployeeService`, `TemporaryPasswords`, `ServiceSupport`, the wiring in `HRMServiceImpl`, and tests (25 service tests against real MySQL, 5 for temporary passwords, an end-to-end test over TLS). 5b is next.

Differences from the plan above: the temporary-password alphabet has 57 characters, not 56 (24 uppercase, 25 lowercase, 8 digits; the earlier count was an arithmetic slip). Two earlier tests that relied on `getMyProfile` and `updateFamilyMember` being "not implemented" now check what those methods really do.

## Intent

Turn the remaining "Not implemented yet" methods of `HRMService` into the real features from the brief. Every method asks `AuthService` who is calling, applies the rules from `DECISION.md`, and uses the DAOs from Phase 3. No change to `HRMService` or the data classes.

## Goals

- HR registers employees (first name, last name, IC/passport) and gets a one-time temporary password (#28).
- Employees and HR staff keep their own contact details and family details.
- Employees see leave types and balances, apply for leave, and follow the status of their applications.
- HR sees pending applications, approves or rejects them, and generates the yearly report of an employee.
- Every rule is enforced on the server, whatever the client sends.

## Non-goals

- No GUI (Phase 7), no retry or load handling (Phase 6).
- No cancelling or editing an application (#24), no "forgot password" (#50).
- No employee deletion, search or export.

## Parts of the service

| Class | Methods it serves |
|---|---|
| `EmployeeService` (5a) | `registerEmployee`, `listEmployees`, `getMyProfile`, `updateMyContact`, `getMyFamily`, `addFamilyMember`, `updateFamilyMember`, `removeFamilyMember` |
| `LeaveService` (5b) | `getLeaveTypes`, `getMyLeaveBalances`, `applyForLeave`, `getMyLeaveApplications`, `listPendingLeave`, `decideLeave` |
| `ReportService` (5b) | `generateYearlyReport` |

`HRMServiceImpl` only delegates. `TemporaryPasswords` (5a) makes the one-time passwords.

## Rules

Who may call what:

| Method | Who |
|---|---|
| `registerEmployee`, `listEmployees`, `listPendingLeave`, `decideLeave`, `generateYearlyReport` | HR only |
| `getLeaveTypes`, `getMyLeaveBalances`, `applyForLeave`, `getMyLeaveApplications` | employees only (#25) |
| profile and family methods | any logged-in person, HR included |

Anyone on a temporary password is refused everywhere except `changePassword` and `logout` (#48).

**Register an employee (5a).** Names and IC/passport are trimmed and checked with the contract's own rules. A duplicate IC/passport (any letter case) is refused with "An employee with that IC/passport number already exists." The new account gets a random 12-character password from an alphabet without look-alike characters (no 0/O, 1/l/I), about 70 bits, and must change it at first login. The password is returned once in `RegistrationResult`, hashed in the database, and never logged.

**Contact details (5a).** Phone, email and address are trimmed; blank becomes "no value". Limits: phone 30, email 100 (must contain `@`), address 255 characters (#41). Names and IC/passport cannot be changed here (#30).

**Family (5a).** A person can only see, change or remove their own members. Another person's member id gives "Family member not found." (no hint that it exists).

**Apply for leave (5b).** In one transaction the employee row is locked first (#20), then, in this order:
1. the leave type must exist;
2. the dates follow the contract rules (end not before start, one calendar year);
3. no PENDING or APPROVED leave overlaps the dates (#23);
4. for a capped type, the days left (allowance minus approved minus pending, for the start year) must cover the days requested (#19);
5. the application is saved as PENDING with the current time.

Retroactive dates are allowed (a medical certificate often comes after the fact); the only date check is that the year is sensible (2000 to 2100).

**Decide leave (5b).** HR approves or rejects a PENDING application. If it is not found: "Leave application not found." If someone already decided it: "This application has already been decided." Pending days already count against the balance, so approving needs no second check.

**Yearly report (5b).** One employee and one year: the profile, the family, the applications that start in that year, and the balance for every leave type.

Errors are the contract's exceptions with plain messages. A database failure always becomes "The service is temporarily unavailable. Try again later." (no SQL, #33).

## Decisions (assistant calls, owner can override)

1. **Service tests use the real MySQL** (`hrm_test`), not a hand-written fake. The leave rules depend on SQL behaviour (overlap, sums, locks); a fake would copy that logic and could drift from it. The tests stay fast enough, and CI runs them with its own MySQL. The Phase 4 fake stays for the earlier tests.
2. **HR staff can keep a profile and family** like anyone else, but cannot apply for leave (#25).
3. **No restriction on past dates** for leave (see above).
4. **Temporary passwords are 12 characters** from a 57-character alphabet.
5. **`now` comes from an injected `Clock`**, so tests can fix the time.

## Tests (written first)

5a: registration (success, one-time password works, different each time, HR only, no session, duplicate, invalid names, trimming), listing, profile, contact update (limits, blank handling, only contact fields change), family (add, list, update, remove, validation, not-found, cannot touch another person's member, HR may keep a family), temporary-password users blocked, database down gives the safe message, `TemporaryPasswords` (length, alphabet, uniqueness, meets the password policy).

5b: leave types; balances (empty, approved, pending, uncapped type, other years); applying (success, unknown type, bad dates, overlap with pending and approved, rejected leave does not block, not enough days, exactly enough days, uncapped type, HR refused); a race test where many threads apply for the last days and exactly the allowed number succeed (#20); my applications; pending list; approve and reject; second decision refused; unknown id; employee refused; the yearly report with profile, family, applications and balances; an end-to-end test over TLS with real everything.

## Done when

- `mvn test` and CI are green; every method of `HRMService` is implemented (no "Not implemented yet" left) once 5b is merged.
- Key rules are shown to fail when deliberately broken (permission checks, overlap, balance, lock).

## New dependencies

None.
