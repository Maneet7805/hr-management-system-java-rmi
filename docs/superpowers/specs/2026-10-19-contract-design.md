# Phase 1: Shared contract design

Status: **Approved by owner and built on branch `feat/contract`** (2026-10-19). Not yet committed or merged.

Built as designed, with these small differences:
- Data classes reject bad values with `IllegalArgumentException`, not `ValidationException`. A record constructor cannot throw a checked exception. The server (Phase 5) turns it into a `ValidationException` for the client.
- `ping()` also declares `HrmException`, so it can report "database unreachable".
- The interface has 19 methods (the chat summary said 17).
- Test groups 3 and 6 are `EmployeeTest` (forged stream) and the `toString` tests in `SessionTest` and `RegistrationResultTest`.

## Intent

Define the one thing every other part depends on: the remote interface `HRMService`, the serializable data classes the client and server exchange, and the exceptions the server throws. After this phase the contract is frozen. Later phases may add to it only by agreement, because the server, DAOs and every Swing screen are written against it.

Why now: the brief's features all cross the RMI boundary, so each one is a method here. Getting the shapes right once avoids rewriting both sides later.

## Goals

- Cover every feature in the brief plus the rules in `DECISION.md` (#19-#36), and nothing else.
- Every class sent over RMI is `Serializable`, immutable, and validates itself.
- No password hash ever crosses the wire (decision #36).
- The client can tell failures apart (wrong password, no permission, bad input, server trouble) from the exception type alone.

## Non-goals

- No server logic, SQL, TLS or GUI in this phase.
- No cancel-leave, edit-leave, payroll, search, audit log or file export.

## Package layout

```
com.bhel.hrm.common              HRMService
com.bhel.hrm.common.model        data classes and enums
com.bhel.hrm.common.exception    HrmException and subclasses
```

## Data classes (all Java records, `implements Serializable`)

Records are immutable and run their constructor when deserialized, so validation also protects the server from forged input. They need no getters or setters, which keeps them short to explain.

| Class | Fields | Validation in constructor |
|---|---|---|
| `Role` (enum) | HR, EMPLOYEE | - |
| `LeaveStatus` (enum) | PENDING, APPROVED, REJECTED | - |
| `Employee` | id, firstName, lastName, icPassport, role, phone, email, address, mustChangePassword | names not blank, max 50 chars; icPassport 6-20 letters, digits or hyphens; email contains `@` if present; **no hash field** |
| `FamilyMember` | id, name, relationship, dateOfBirth (nullable) | name and relationship not blank; date of birth not in the future |
| `LeaveType` | id, name, annualAllowance (null = uncapped) | name not blank; allowance not negative |
| `LeaveApplication` | id, employeeId, employeeName, leaveType, startDate, endDate, days, reason, status, appliedAt, decidedAt (nullable) | end not before start; start and end in the same year; `days` equals end minus start plus 1 |
| `LeaveBalance` | leaveType, year, approvedDays, pendingDays | days not negative; method `remaining()` returns null when uncapped |
| `YearlyReport` | employee, year, familyMembers, leaveApplications, balances | lists copied defensively so they cannot be changed |
| `Session` | token, employeeId, displayName, role, mustChangePassword | token not blank; `toString` hides the token |
| `RegistrationResult` | employee, temporaryPassword | `toString` hides the password |

## Exceptions (all in `common.exception`, serializable, checked)

`HrmException` is the base. Subclasses: `InvalidCredentialsException`, `PermissionDeniedException`, `ValidationException`, `NotFoundException`, `ServiceUnavailableException`. Each takes a plain-language message for the user. None carries SQL text.

## Remote interface `HRMService extends Remote`

Every method declares `throws RemoteException, HrmException`. Every method except `ping` and `login` takes the session token as its first argument.

| Group | Methods |
|---|---|
| Health | `ping()` |
| Account | `login(loginId, password)` returns `Session`; `logout(token)`; `changePassword(token, oldPassword, newPassword)` |
| HR | `registerEmployee(token, firstName, lastName, icPassport)` returns `RegistrationResult`; `listEmployees(token)`; `listPendingLeave(token)`; `decideLeave(token, applicationId, approve)` returns `LeaveApplication`; `generateYearlyReport(token, employeeId, year)` returns `YearlyReport` |
| Employee profile | `getMyProfile(token)`; `updateMyContact(token, phone, email, address)` returns `Employee`; `getMyFamily(token)`; `addFamilyMember(token, name, relationship, dateOfBirth)`; `updateFamilyMember(token, FamilyMember)`; `removeFamilyMember(token, familyMemberId)` |
| Employee leave | `getLeaveTypes(token)`; `getMyLeaveBalances(token, year)`; `applyForLeave(token, leaveTypeId, startDate, endDate, reason)` returns `LeaveApplication`; `getMyLeaveApplications(token)` |

`listEmployees` and `listPendingLeave` are not named in the brief. HR cannot choose an employee for a report or find applications to decide without them.

## Tests (written first, JUnit 5)

1. **Round trip:** every data class and every exception serializes and deserializes to an equal object.
2. **Validation:** each rule in the table above rejects a bad value (end before start, blank name, bad IC, wrong `days`, future birth date, cross-year leave).
3. **Deserialization safety:** a forged byte stream with a bad value is rejected when read.
4. **No secrets:** no field in any `common.model` class is named like password or hash, except `RegistrationResult.temporaryPassword`.
5. **Interface shape:** `HRMService` extends `Remote`; every method declares `RemoteException` and `HrmException`; every method except `ping` and `login` takes `String token` first.
6. **Hidden values:** `Session.toString` and `RegistrationResult.toString` do not contain the secret.

## Done when

- `mvn test` passes with the six test groups above, and `mvn compile` exits 0.
- No class in `common` imports anything outside the JDK.
- The owner has read this document and the interface.

## Calls made by the assistant (owner can override)

- Records instead of classic classes with getters.
- `password` is a plain `String`, not `char[]`. It is serialized either way, so `char[]` adds complexity without real protection here.
- `decideLeave` takes a boolean `approve` instead of two methods or a status argument.
- IC/passport format is 6-20 letters, digits or hyphens. Real IC formats differ by country; the brief gives no format.
- Name length limit of 50 and the date-of-birth rule are mine.

## Left to the builder

Exact error messages, test class names, and the order of record fields beyond what the tables show.
