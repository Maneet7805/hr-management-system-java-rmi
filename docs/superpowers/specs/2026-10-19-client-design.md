# Phase 7: Swing client

Status: **Approved by delegation** (the owner left the open choices to the assistant, 2026-10-19). Built in three slices, each its own pull request: **7a** foundation, **7b** employee screens, **7c** HR screens.

**7b is built** on branch `feature/client-employee`: the home screen with tabs, "My details", "Family" (with a confirmation before removing) and "Leave" (balances, apply, my applications), a voluntary change-password path, display and date-input helpers, and 59 new tests (371 before, 430 now), including an end-to-end test that drives all three screens against the real server and database. Drawing the screens with realistic data showed missing table headings and a misaligned form; both are fixed (decisions #73, #72). Seven deliberate breaks (family member removed without asking, wrong date ignored, empty reason sent as text, balances not refreshed, details form usable before loading, HR staff given the leave tab, expired session ignored) each make a test fail. HR screens (7c) are next.

**7a is built** on branch `feature/client-foundation`: `ResilientService`, `ErrorMessages`, `Tasks`, `ScreenHost`/`MainFrame`, login, change-password and a placeholder home screen, `HrmClientMain`, and 60 new tests (311 before, 371 now) (plus a real server stop-and-restart test and an end-to-end test of the screens against real MySQL). Results worth knowing:
- Drawing the screens to images showed the grey hint text was too faint (about 2.3:1); it is now about 6:1 (decision #67).
- Six deliberate breaks (server call on the event thread, writes retried, failed connection not forgotten, password confirmation skipped, typed password left on screen, expired session ignored) each make a test fail.
- `HrmClientMain` and `MainFrame` have no unit tests (thin wiring); they are checked by hand.

## Intent

Give HR staff and employees a window they can use, on top of the finished server. The brief allows a command line or a GUI; the team chose a GUI, and usability is one of the qualities the report must evaluate (together with maintainability and heterogeneity).

## Goals

- One window: log in, then the screens for your role (employee or HR).
- The window never freezes and never calls the server on the Swing event thread (CLAUDE.md rule 4).
- Every failure is a short sentence the user can act on, never a stack trace.
- The client survives a short server outage: reads are retried automatically, writes never are (decision #34).
- Almost everything is testable without a screen: logic lives in plain classes, screens are thin `JPanel`s that can be built, driven and drawn to an image with no display.

## Non-goals

- No themes, translations, printing or offline mode. Plain system look and feel.
- No automatic update of a screen when someone else changes data (the user presses Refresh).

## Slices

| Slice | Contents |
|---|---|
| **7a** foundation | `ResilientService`, `ErrorMessages`, `Tasks`, `MainFrame` with a `Navigator`, login screen, change-password screen, a simple home screen, `HrmClientMain` |
| **7b** employee | Profile and contact, family, leave (balances, apply, my applications) |
| **7c** HR | Employee list and registration (temporary password shown once), pending leave with approve and reject, yearly report with save to a text file |

## Design of the foundation (7a)

### Staying connected: `ResilientService`

A wrapper around `HRMService` that every screen uses instead of the raw stub. It connects on the first call, so a server that is down shows up as a normal message on the login screen.

| Kind of call | If the connection fails |
|---|---|
| **Reads** (`ping`, `login`, `get...`, `list...`, `generateYearlyReport`) | Try again up to 3 times in all, waiting 0.25 s then 0.5 s, and look the server up again each time (it may have restarted). |
| **Writes** (`register`, `apply`, `decide`, `changePassword`, family changes, `logout`) | If the connection could not be made at all, retry as for reads (nothing was sent). If the call was already on its way, **do not retry**: a lost reply could otherwise mean a duplicate application. The user is told the last action may or may not have been saved. |

Business errors from the server (wrong password, not enough days, ...) are returned at once and never retried. A test makes sure every method of `HRMService` is listed as a read or a write, so a method added later cannot be forgotten.

### Messages: `ErrorMessages`

One class turns any exception into the sentence shown to the user. Server messages are shown as they are (they are already written for people). Network failures get fixed sentences: "Cannot reach the HR server...", or "The connection was lost; your last action may or may not have been saved. Check before trying again." A session that has expired sends the user back to the login screen.

### Keeping the screen alive: `Tasks`

`Tasks.run(work, onSuccess, onFailure)` runs the work on a background thread (a `SwingWorker`) and calls the two callbacks back on the event thread. Screens use only this to talk to the server. A test proves the work does not run on the event thread and the callbacks do.

### Screens

`MainFrame` shows one screen at a time (card layout) and implements `Navigator` (`showLogin`, `showChangePassword`, `showHome`). Screens are `JPanel`s given the service and the navigator, so tests can create them with a fake service. Date fields take `yyyy-MM-dd` with a visible hint and a clear message when wrong.

Login screen: ID, password, Log in (the default button, so Enter works), a status line. While a call runs the fields and button are disabled. After a successful login, someone on a temporary password goes to the change-password screen and cannot reach anything else until they have chosen their own password (the server enforces this too, #48).

### Starting up: `HrmClientMain`

Reads `config/client.properties` and the truststore password from the environment (as `SmokeTestMain` does), builds the `ResilientService`, and opens the window on the event thread.

## Decisions (assistant calls, owner can override)

1. **System look and feel**, no extra library. Fewer surprises, nothing to install.
2. **One window with swappable screens**, not a new window per task. Simpler to navigate and to test.
3. **Retry only reads** (and failed connection attempts). The cost is that a write interrupted mid-call shows an honest "may or may not have been saved" message instead of retrying silently.
4. **Dates typed as text** (`yyyy-MM-dd`), not a calendar widget. Swing has no standard one and a third-party one would be a new dependency; the format is shown beside the field and checked before anything is sent.
5. **Screens are tested by driving them without a display** (click the buttons in code, check the result) and by drawing them to images so layout can be inspected. No test needs a real window.

## Tests (written first)

1. `ResilientService`: a read is retried after a connection failure and then succeeds; a read gives up after 3 attempts; a business error is not retried; a write is not retried after the call was sent; a write is retried when the connection could not be made; the server is looked up again after a failure; every `HRMService` method is classified; the wrapper keeps working with the real server stopped and started again.
2. `ErrorMessages`: each kind of failure gives the right sentence; the "session expired" text equals the server's own text (checked against the server).
3. `Tasks`: work runs off the event thread, callbacks on it; success and failure paths; an exception from the work reaches `onFailure`.
4. Login and change-password screens, driven with a fake service: empty fields are refused without a server call; a wrong password shows the message and re-enables the form; success goes to the next screen; a temporary password goes to change-password; mismatched confirmation is refused; the password fields are cleared after use.
5. One end-to-end test: real server and database, drive the login screen, log in as the seeded HR account, then log out.
6. Drawing the login and change-password screens to PNG files for a visual check (the files are not committed).

## Done when

- `mvn test` and CI are green.
- The login and change-password screens have been looked at as images.
- `HrmClientMain` starts and shows the login window against a running server (checked by hand).

## New dependencies

None. Swing is part of the JDK.
