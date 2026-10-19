# Decisions

Running log of project decisions. Newest entries go at the bottom of each section.
Status: **Decided** (owner confirmed), **Assistant call** (made by Claude, owner can override), **Open**.

## Technology

| # | Decision | Status | Why |
|---|---|---|---|
| 1 | Java 25 (LTS), Maven build | Decided | LTS release; inherited from team README and CLAUDE.md. PR #3. |
| 2 | Swing GUI only, no JavaFX | Decided | CLAUDE.md rule 6. |
| 3 | Database: MySQL 8.4 LTS | Decided | Recommended in team research draft. Owner confirmed. |
| 4 | MySQL installed with `winget install Oracle.MySQL` (8.4.9) | Assistant call | Official Oracle package. Installs binaries only, no Windows service. |
| 5 | Password hashing: PBKDF2-HMAC-SHA256, random salt per password, in the JDK | Assistant call | No extra dependency, easy to explain in an individual section. Hidden behind a `PasswordHasher` interface so it can be swapped (for example to BCrypt). |
| 6 | Tests: test-first per feature (JUnit 5) | Decided | Gives evidence for the testing section. |

## Scope and ownership

| # | Decision | Status | Why |
|---|---|---|---|
| 7 | Owner builds every slice for now; tasks are assigned to teammates later | Decided | Unblocks the shared contract. |
| 8 | Build order: shared contract, then DB and server, then client | Assistant call | Every other layer depends on `HRMService` and the data classes. |
| 9 | Contract classes in `common/` may be written now | Decided | CLAUDE.md rule 1 forbids changes only when not asked. Owner asked. |

## Leave rules

The assignment brief and the draft give no leave types or day counts. These numbers are assumptions.

| # | Decision | Status | Why |
|---|---|---|---|
| 10 | Several leave types | Decided | Owner chose this over a single type. |
| 11 | Types and yearly allowance: Annual 14, Medical 14, Emergency 3, Unpaid uncapped | Assistant call | Typical small-company defaults. Unpaid never reduces a balance. Easy to change in one table. |
| 12 | ~~Balance stored per employee, per type, per year~~ **Replaced by #19** | Superseded | A stored balance can drift from the leave history. |
| 13 | ~~Approval deducts a stored balance~~ **Replaced by #19** | Superseded | Not needed once the balance is computed. |

## Environment

| # | Decision | Status | Why |
|---|---|---|---|
| 14 | OS: Windows 11, PowerShell | Certain | Detected. |
| 15 | MySQL data directory outside the repo | Assistant call | Keeps data and secrets out of git (CLAUDE.md rule 5). |
| 16 | Secrets only via environment variables (`HRM_DB_PASSWORD`, etc.) | Decided | CLAUDE.md and README. |
| 17 | MySQL run by hand, not as a Windows service; data in `C:\tools\mysql-data`; listens on `127.0.0.1:3306` only | Decided | Owner approved the proposal. No admin rights needed. |
| 18 | `root` initialized with an empty password until the owner sets one; database `hrm` created | Assistant call | Local-only. Passwords are typed by the owner, never stored in files or chat. |

## Phase 0: design rules (all Assistant calls unless noted)

The brief only states the features. Everything below is our own design; the owner can override any line.

| # | Decision | Why |
|---|---|---|
| 19 | Leave balance is **computed**: yearly allowance minus approved days minus pending days for that type and year. Nothing is stored. | One source of truth, no drift. Replaces #12 and #13. |
| 20 | The apply-leave check runs in one DB transaction that first locks the employee row (`SELECT ... FOR UPDATE`). | Two simultaneous applications cannot both use the last days. |
| 21 | Days are **calendar days, inclusive** (start 1st, end 3rd = 3 days). Weekends and public holidays are not special. | Simplest rule to explain and test. |
| 22 | Leave that crosses 31 Dec / 1 Jan is rejected; the employee applies as two requests. | Keeps yearly balance and report simple. |
| 23 | An employee cannot have two overlapping PENDING or APPROVED applications. | Prevents double-counting the same days. |
| 24 | Statuses: PENDING, APPROVED, REJECTED. No cancel and no edit after applying. | Brief has no cancel. Smaller scope. |
| 25 | Only role EMPLOYEE applies for leave. HR accounts only manage and decide. | Avoids HR approving their own leave. |
| 26 | Roles: HR and EMPLOYEE. HR registers, decides, reports. Employee manages own data only. | Minimum needed for role-based access. |
| 27 | Login id is the IC/passport number. | Already unique and required by the brief. |
| 28 | HR registers an employee with a generated temporary password, shown once to HR. Employee must change it at first login (`changePassword` is therefore in scope). | Temporary passwords must not stay permanent. |
| 29 | First HR account is created by a small seed program that prompts for the password. | Otherwise nobody can log in to create anyone. |
| 30 | HR sets first name, last name, IC/passport. Employee edits phone, email, address, and family details. Employee cannot edit name or IC. | Keeps identity data under HR control. |
| 31 | Family member: name, relationship, date of birth (optional). | Brief says only "family details". |
| 32 | Session: random 128-bit token, kept in server memory, 30 minutes idle expiry, lost on server restart. | Simplest secure option; the client logs in again. |
| 33 | Errors are serializable checked exceptions in `common`: base `HrmException` with `InvalidCredentialsException`, `PermissionDeniedException`, `ValidationException`, `NotFoundException`, `ServiceUnavailableException`. | Client shows clear messages; no SQL details leak. |
| 34 | The client auto-retries read calls only. Write calls are never retried. | A lost reply could otherwise create a duplicate application. |
| 35 | Yearly report = one employee, one year; leave history is applications whose start date is in that year. Shown on screen; file export is optional. | Matches the brief; export is not required. |
| 36 | The password hash never appears in any class sent to the client. | Hash would be exposed to every client. |

### Table sketch (MySQL, to build in Phase 3)

| Table | Columns | Rules |
|---|---|---|
| `employee` | id, first_name, last_name, ic_passport, role, phone, email, address, password_hash, password_salt, hash_iterations, must_change_password, created_at | `ic_passport` unique; role is HR or EMPLOYEE |
| `family_member` | id, employee_id, name, relationship, date_of_birth | FK to employee, delete cascades |
| `leave_type` | id, name, annual_allowance | `name` unique; allowance NULL = uncapped (Unpaid) |
| `leave_application` | id, employee_id, leave_type_id, start_date, end_date, days, reason, status, applied_at, decided_by, decided_at | FKs; CHECK end_date >= start_date; index on (employee_id, start_date) |

Seed rows for `leave_type`: Annual 14, Medical 14, Emergency 3, Unpaid NULL (see #11).

## Open

- Owner still has to set the `root` password and `HRM_DB_PASSWORD`; then the `hrm` user is created.
- Whether leave types and allowances match what the lecturer or group expects.
