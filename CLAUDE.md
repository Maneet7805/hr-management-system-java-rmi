# CLAUDE.md

Guidance for Claude (or any AI coding assistant) working in this repository.

## Project

Java RMI client-server HR management system for a university group assignment (Distributed Computer Systems). Scenario: a start-up replaces spreadsheets and paper HR records.

Functional scope:

- HR staff register employees (first name, last name, IC/passport number), approve or reject leave, and generate a yearly report per employee (profile, family details, leave history).
- Employees log in, update their profile and family details, check leave balance, apply for leave, and check leave application status.
- Central database; all client-server traffic encrypted; role-based access.

## Architecture

```
Swing GUI client -> RMI over TLS -> RMI server (service layer) -> JDBC -> Database
```

- `src/common/`: `HRMService` remote interface (extends `java.rmi.Remote`) and serializable data classes (`Employee`, `FamilyMember`, `LeaveApplication`, `YearlyReport`).
- `src/server/`: RMI server, service logic, data access.
- `src/client/`: Swing GUI. Screens: login, employee, HR.

## Rules for AI-assisted changes

1. **Do not change `HRMService` or the data classes in `src/common/`** without being asked. Every teammate's code depends on them.
2. If a task needs a technology choice the team has not made (database, hashing library, etc.), say so instead of picking one silently.
3. Server: any shared state must be thread-safe. Use `PreparedStatement` for all SQL. Never store or log plain-text passwords.
4. Client: never call RMI methods on the Swing event thread; use `SwingWorker` and show a clear message when the server is unreachable.
5. Never add secrets, keystores, or real personal data to the repo.
6. Use Java 17 features at most; Swing only (no JavaFX).
7. Keep code readable by students: small methods, clear names, comments that explain why.
8. This is graded coursework with individual marks. Explain what you wrote so the author can describe it themselves. Do not generate report text for sections marked individual.

## Build and test

Build, run and test commands will be filled in here once the first working version exists. Until then, compile with `javac` from the source root.

## Git conventions

See `CONTRIBUTING.md`. Branch per task, PR into `main`, one review, squash merge. Never push to `main` directly.

## Report context

The deliverable is a single report plus the source code. The report needs a Gantt chart, requirements, research, a use case diagram, protocols, individual implementation and testing sections, conclusion, future enhancements (cloud and virtualization), references (APA, no blogs or forums), and a workload matrix.
