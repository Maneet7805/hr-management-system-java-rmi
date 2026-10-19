# Phase 4: Authentication design

Status: **Built on branch `feature/authentication`** (2026-10-19). The owner delegated the open choices to the assistant, so the design went ahead as written (no lockout, no forgot-password, 600,000 rounds, server-side lockdown for temporary passwords).

Built as designed. Results worth knowing:
- One password hash takes about 243 ms on the owner's PC.
- The end-to-end test (`AuthEndToEndTest`) uses real MySQL, the real TLS server and the real hasher, and also checks that the database holds a 32-byte hash, a 16-byte salt and 600,000 iterations, never the password.
- `SeedHrMain` and `HrmServerMain` have no unit tests (thin wiring); both were run for real against the test database.
- Four deliberate breaks (wrong password accepted, no dummy hash for unknown IDs, no temporary-password lockdown, other sessions surviving a password change) each make a test fail.

## Intent

Let people log in, stay logged in safely, and change their password. After this phase every other method can ask one question, "who is this and may they do this?", and get a reliable answer. Phase 5 builds the HR and employee features on top of it.

Why now: every Phase 5 method needs a session check, and the password rules are the part of the system a marker is most likely to probe.

## Goals

- Passwords are stored only as a salted, slow hash (decision #5); the plain text is never stored, logged or sent back.
- `login`, `logout` and `changePassword` work through the existing `HRMService` contract.
- One place checks the session and the role, so no Phase 5 method can forget to.
- A user with a temporary password can do nothing except change it (decision #28).
- A first HR account can be created without anyone being logged in (decision #29).
- Wrong ID and wrong password look identical to the caller, and take about the same time.

## Non-goals

- No registration of employees or temporary-password generation (Phase 5).
- No account lockout and no "forgot password" (see Decisions 5 and 6).
- No change to `HRMService` or the data classes.

## Design

### Password hashing (`server.security`)

- `PasswordHasher` interface: `hash(password)` returns `StoredCredentials`; `verify(password, stored)` returns a boolean.
- `Pbkdf2PasswordHasher`: PBKDF2-HMAC-SHA256, a fresh 16-byte random salt per password, 256-bit result, **600,000 iterations** (OWASP's current figure for this algorithm). The iteration count is stored with each hash, so it can be raised later and old hashes still verify. Comparison is constant-time. The characters are cleared from memory after use.
- `PasswordPolicy`: a new password must be 8 to 128 characters, not blank, and not equal to the person's login ID. No composition rules (digits, symbols), because length matters more. The 128 limit stops someone sending a huge "password" to waste server time.

### Sessions (`server.security`)

- `SessionManager` keeps the logged-in users in memory: token (16 random bytes, URL-safe text) mapped to employee id, role, name and the must-change flag.
- A session ends after 30 minutes without use (decision #32). Every successful use restarts the 30 minutes. All times come from an injected `Clock`, so tests do not wait.
- Safe for many threads at once (`ConcurrentHashMap`). Expired sessions are removed when new ones are created.
- After a password change, the user's other sessions are ended and the current one continues.

### The service (`server.service.AuthService`)

| Method | Behaviour |
|---|---|
| `login(id, password)` | Finds the employee by IC/passport (trimmed, ignoring case), verifies the password, returns the contract's `Session`. Unknown ID and wrong password both throw `InvalidCredentialsException("Wrong ID or password")`, and an unknown ID still does one full hash check against a dummy value, so the response time does not reveal which IDs exist. |
| `logout(token)` | Ends the session. An unknown or expired token is not an error. |
| `changePassword(token, old, new)` | Checks the old password, applies the policy, rejects a new password equal to the old one, stores the new hash and clears the must-change flag in one transaction. |
| `requireSession(token)` | For other services: returns who is calling. Unknown or expired token: `InvalidCredentialsException("Your session has expired. Please log in again.")`. A session still on a temporary password: `PermissionDeniedException`. |
| `requireRole(token, role)` | As above, then checks the role; wrong role gives `PermissionDeniedException("Only HR staff can do this.")` (or the employee equivalent). |

Database failures become `ServiceUnavailableException("The service is temporarily unavailable. Try again later.")`. The real cause goes to the server log (never a password or token), not to the client.

### Wiring

- `HRMServiceImpl` now takes the `Database` and `AuthService`. `ping()` also checks the database is reachable (a cheap primary-key lookup); `login`, `logout` and `changePassword` delegate to `AuthService`; the other methods still answer "Not implemented yet".
- `HrmServerMain` builds the pieces. Starting the server now also needs `HRM_DB_PASSWORD`.
- `SeedHrMain` (command line) creates an HR account. It reads the password from the console without showing it, or from `HRM_SEED_PASSWORD` when there is no console (IDE terminals). The logic sits in `HrSeeder` so it can be tested; `main` only does the typing.

## Decisions (assistant calls, owner can override)

1. **600,000 PBKDF2 rounds.** Each login costs roughly a third of a second of CPU. That is right for a handful of users and is the figure the report can cite. Tests use 1,000 rounds so they stay fast; one test checks the real default.
2. **Temporary-password users are locked down on the server**, not just in the GUI. Without this, anyone with a temporary password could keep using the system without ever choosing their own.
3. **Same message and same timing for "no such user" and "wrong password."** It stops someone listing which IC numbers exist.
4. **Session tokens are 128 random bits**, as in decision #32, and exist only in server memory. A server restart logs everyone out.
5. **No account lockout.** After a number of wrong passwords, lockout would stop guessing, but it would also lock out a lecturer who mistypes during a demo, and lets anyone lock out a known ID on purpose. Slow hashing already limits guessing speed. The report can list lockout and rate limiting as future work.
6. **No "forgot password."** HR cannot reset one in this phase either. If a password is lost, the fix is a new temporary password, which belongs with the Phase 5 employee features.
7. **One HR account per run of the seed tool**; run it again for more.

## Tests (written first)

Most run without MySQL, using an in-memory stand-in for the database that lives in the test code.

1. **Hasher:** verify accepts the right password and rejects a wrong one; the same password gives different salts and hashes; the stored iteration count is used when verifying; the default is 600,000; hash and salt sizes.
2. **Policy:** too short, too long, blank, equal to the login ID (any case) are rejected; a long passphrase is accepted.
3. **Sessions:** create and find; unknown token; expiry after 30 minutes idle; use extends the life; remove; remove-others keeps the current one; tokens are unique and long enough; 50 threads creating sessions at once get 50 distinct, findable tokens.
4. **AuthService:** login succeeds and returns the right name, role and flag; wrong password and unknown ID give the identical message, and the unknown ID still triggers a hash check; ID with spaces is trimmed; overlong or blank input is rejected; logout ends the session and an unknown token is harmless; password change works (old fails, new works, flag cleared, other sessions ended); wrong old password; weak new password; same password again; a temporary-password session is blocked everywhere except `changePassword` and `logout`; `requireRole` for each role; an expired session; a database failure gives the safe message with no SQL.
5. **HrSeeder:** creates an HR account that can log in; duplicate IC gives a clear message; weak password refused.
6. **Service wiring:** `ping` succeeds, and fails clearly when the database is down; `login`/`logout`/`changePassword` work through `HRMServiceImpl`; other methods still answer "Not implemented yet".
7. **Everything real (one test):** real MySQL and the real TLS server. Seed an HR account, log in through a TLS client, change the password, log in again with the new one, and see the old one refused.
8. The Phase 2 TLS tests are updated for the new `HRMServiceImpl` constructor and still pass.

## Done when

- `mvn test` is green, including the real MySQL test; CI is green.
- Running `SeedHrMain` against your development database creates an HR account (you can see the row in `employee`; the password column holds a hash, not text).
- No password, hash or token appears in a log line, an exception message or a `toString`.

## New dependencies

None. PBKDF2 and the random generator are in the JDK.
