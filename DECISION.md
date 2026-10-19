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

## Phase 3 and repository setup (all Assistant calls unless noted)

| # | Decision | Why |
|---|---|---|
| 37 | The service reaches data only through `Database.read(...)` and `Database.transaction(...)`; each hands out DAOs bound to one connection. | Explicit and easy to explain; no hidden per-thread connection. Needed for the row lock in #20. |
| 38 | One connection per unit of work, no connection pool. | Enough for this project's size; keeps the code short. |
| 39 | MySQL user `hrm_app` has full rights on `hrm` and `hrm_test` only. Its password was generated at random and lives only in the user environment variable `HRM_DB_PASSWORD` (owner approved, option A). | Least privilege that still lets the program create its own tables. Nothing secret in the repo. |
| 40 | Database tests fail if MySQL or the password is missing. Skipping needs `mvn test "-Dhrm.skipDbTests=true"` (the quotes matter in PowerShell). GitHub Actions starts its own MySQL. | A green run must mean the database was really tested. Decided with the owner: no teammates are on the code yet. |
| 41 | Phone, email and address length limits (30, 100, 255) are checked in the Phase 5 service, not in the shared contract. | The contract is frozen; the columns are not. |
| 42 | Repository is public (owner). `main` is protected: a pull request and the passing `test` check are required, 0 approvals, admins not forced. Secret scanning, push protection, Dependabot alerts and security updates are on. | Owner works alone for now; this still blocks accidental direct pushes and secret leaks. |
| 43 | CI is GitHub Actions with Temurin Java 25 and a throwaway MySQL 8.4 container. Dependabot checks Maven and Actions weekly, grouped. | PR #6. |
| 44 | New commits in this repo use the GitHub no-reply email (set locally; the global git setting is unchanged). | Keeps the owner's Gmail address out of new public commits. Older commits still show it. |

## Phase 4: authentication (all Assistant calls; the owner delegated the remaining choices on 2026-10-19)

| # | Decision | Why |
|---|---|---|
| 45 | Passwords are hashed with PBKDF2-HMAC-SHA256: a random 16-byte salt per password, a 32-byte result, **600,000 iterations**, stored with each hash. About 243 ms per hash on the owner's PC. | OWASP's current figure for this algorithm. Storing the count lets it be raised later. Refines #5. (An earlier chat message said 210,000; that was wrong.) |
| 46 | A new password must be 8 to 128 characters, not blank, and not equal to the login ID. No composition rules. | Length protects better than symbol rules; the 128 cap stops oversized input wasting hashing time. |
| 47 | Sessions are a random 128-bit token held only in server memory, ending after 30 minutes without use. A password change ends the person's other sessions. A server restart logs everyone out. | Refines #32. |
| 48 | A person still on a temporary password can only change it or log out. The server enforces this, not the GUI. | Otherwise a temporary password could be used forever (#28). |
| 49 | An unknown ID and a wrong password give the same message ("Wrong ID or password.") and the same work: an unknown ID still does one full hash check. | Stops anyone listing which IC numbers exist, by message or by timing. |
| 50 | No account lockout and no "forgot password" in this phase. | Lockout would lock out a lecturer who mistypes in a demo and lets anyone lock out a known ID on purpose; slow hashing already limits guessing. A lost password is fixed by HR issuing a new temporary one (Phase 5). Both go in the report as future work. |
| 51 | The first HR account is created by `SeedHrMain`: hidden console prompt, or the environment variable `HRM_SEED_PASSWORD` where there is no console. | Implements #29. The password never appears on a command line. |
| 52 | Starting the server now needs `HRM_DB_PASSWORD` as well as `HRM_KEYSTORE_PASSWORD`. `ping()` also checks the database. | "The server is up" should mean it can work. |

## Phase 5: features (all Assistant calls; the owner delegated the open choices on 2026-10-19)

| # | Decision | Why |
|---|---|---|
| 53 | Service tests run against the real MySQL test database, not a hand-written fake. | The leave rules depend on SQL behaviour (overlap, sums, locks); a fake would copy that logic and could drift. The Phase 4 fake stays for the earlier tests. |
| 54 | Temporary passwords are 12 characters from a 57-character alphabet without look-alikes (no 0/O, 1/l/I), about 70 bits. Shown once to HR, hashed in the database, never logged. | Easy to read out or copy by hand, and far too strong to guess before it is changed (#28, #48). |
| 55 | HR staff can keep a profile and family like anyone else; leave methods are for employees only. | Follows #25. |
| 56 | Leave can have past dates (a medical certificate often comes after the fact). The only date rules are the contract's (end not before start, one calendar year). | Not restricting what the brief does not restrict. |
| 57 | Someone else's family member id is reported as "Family member not found." | Does not reveal that the id exists. |

### Phase 5b additions (leave and reports)

| # | Decision | Why |
|---|---|---|
| 58 | Applying for leave runs these checks in this order, inside one transaction that first locks the employee row: leave type exists, no overlap with PENDING or APPROVED leave, enough days left (allowance minus approved minus pending for the start year). The refusal names the numbers: "Not enough Annual leave left: 14 day(s) remaining, 15 requested." | Implements #19, #20 and #23. The messages tell the user what to change. |
| 59 | The row lock is proven necessary: with it removed, the test with ten simultaneous requests for the last three Emergency days and the test with five simultaneous requests for the same dates both fail. | Evidence for the report's section on concurrency. |
| 60 | Times (applied, decided) are the server's local time, cut to whole seconds, and carry no time zone. | The database stores whole seconds, so what is returned equals what is stored. A server in another zone would show its own local time. |
| 61 | The yearly report is built in one transaction, so profile, family, applications and balances describe the same moment. | A report must not mix data from before and after a concurrent change. |

## Phase 7a: Swing client foundation (all Assistant calls; the owner delegated the open choices on 2026-10-19)

| # | Decision | Why |
|---|---|---|
| 62 | One window with swappable screens (`ScreenHost`), system look and feel, no extra library. Every visit to the login screen builds a fresh one. | Simpler to navigate and to test; nothing typed earlier is left for the next person. |
| 63 | The client connects through `ResilientService`. Reads are tried up to 3 times (pauses 0.25 s then 0.5 s), looking the server up again each time. Writes are never repeated once the call was on its way; a write is repeated only when the connection could not be made at all. | The fault tolerance the brief asks for, without the risk of a duplicate leave application (#34). Shown to work against a real server that is stopped and restarted. |
| 64 | Screens talk to the server only through `Tasks` (a `SwingWorker`): work off the event thread, callbacks on it. Tests check this for `Tasks` and for each screen. | CLAUDE.md rule 4: the window must never freeze. |
| 65 | Server messages are shown as written. Network and program errors become fixed calm sentences ("The connection to the HR server was lost. If you were saving something, check whether it was saved before trying again."). Nothing technical reaches the screen. An expired session is recognised by the server's own wording, and a test keeps the two texts equal. | Usability, and honesty about writes that may or may not have been saved. |
| 66 | Dates are typed as text in the form `yyyy-MM-dd`, with the format shown beside the field. | Swing has no standard calendar widget, and a third-party one would be a new dependency. (Used from slice 7b.) |
| 67 | Hint text is dark grey (#505050, about 6:1 contrast). The theme's own "disabled" grey measured about 2.3:1. | Found by drawing the screens to images and looking at them. |
| 68 | Screens are tested without a display: driven by code (type, click) and drawn to PNG files in `target/screens` (not committed). | Fast, works in CI, and the layout can still be inspected. |

## Phase 7b: employee screens (all Assistant calls; the owner delegated the open choices on 2026-10-19)

| # | Decision | Why |
|---|---|---|
| 69 | After login the window shows tabs. Employees get Leave (first, it is what they come for), My details and Family. HR staff get My details and Family now; the HR tabs arrive in slice 7c. | HR staff keep a profile and family like everyone but do not apply for leave (#25, #55). |
| 70 | A person who is not on a temporary password can change their password from the home screen and press Cancel to go back. Someone on a temporary password sees only Log out. | A forced change must not be skippable (#48); a voluntary one should not trap anyone. |
| 71 | Only removing a family member asks "are you sure?". Everything else can be corrected afterwards. | Confirmation dialogs for every action train people to click through them. |
| 72 | When the server refuses something, the form keeps what was typed so it can be corrected. Fields are cleared only after success, and typed passwords are always cleared. | Retyping a whole leave request because of one wrong date is a usability failure. |
| 73 | Tables are read-only; adding or changing happens in the form beside them. Column headings are installed explicitly. | Editing inside a table is easy to get wrong; the headings were missing from the drawn screens until this was made explicit. |
| 74 | A leave request that is accepted says what was sent: "Application sent: 3 days of Annual leave, waiting for HR's decision." Pending days already show in the balance. | The person sees at once that it worked and what happens next. |
| 75 | Dates are typed as yyyy-MM-dd and checked on the screen first; a wrong date is explained with an example and nothing is sent (decision #66). | Same wording everywhere, and no round trip for a typing mistake. |

## Phase 7c: HR screens (all Assistant calls; the owner delegated the open choices on 2026-10-19)

| # | Decision | Why |
|---|---|---|
| 76 | HR staff see Employees, Leave requests and Yearly report first, then My details and Family. Employees never see the HR tabs. The server enforces this independently (#26); the tabs only keep the window honest. | HR staff do not apply for leave (#25, #55). |
| 77 | A new employee's temporary password is shown once, in its own box with Copy and Done. It is never written into a message. It disappears when HR presses Done, starts another registration, or switches to another tab. Whether to copy it to the clipboard is HR's choice. | A temporary password lying on screen or in a message is a leak waiting to happen (#54). |
| 78 | Approving and rejecting leave both ask first, naming the person, days, type and dates. If the server says the request was already decided or no longer exists, the message is shown and the list is refreshed. | A decision cannot be undone (#24), and two HR users may act at once (#59). |
| 79 | The yearly report is plain text: the same text is shown on screen and saved to a UTF-8 file, with a suggested name such as `yearly-report-AB123456-2026.txt`. The old report is cleared as soon as a new one is requested, and cancelling the save dialog is not an error. Replacing an existing file asks first. | Simple, readable anywhere (a text editor, Word), and a report on screen can never be mistaken for another employee's. |
| 80 | Report layout: sections for the employee, family, leave balances and the year's applications, columns aligned with spaces; an empty section says "(none recorded)" or "(none)". | Easy to read and easy to test. |
| 81 | Clipboard and file chooser sit behind small interfaces (`ClipboardWriter`, `FileSaver`), as the confirmation dialog does. | Tests need no display, and the real dialogs stay thin. |

## Open

- The MySQL `root` account still has an empty password (it only listens on 127.0.0.1). Owner should set one.
- Whether leave types and allowances match what the lecturer or group expects.
