# HR Management System (Java RMI)

[![CI](https://github.com/Maneet7805/hr-management-system-java-rmi/actions/workflows/ci.yml/badge.svg)](https://github.com/Maneet7805/hr-management-system-java-rmi/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25%20LTS-orange)

A client-server Human Resource Management system for a start-up, built with Java RMI. HR staff register employees and generate yearly reports; employees manage their own profile and family details and handle leave. All data is stored centrally and all client-server traffic is encrypted.

> Built as a group project for the Distributed Computer Systems module (Year 3, Semester 1). The scenario company is "BHEL".

## Project status

Work in progress. Updated as pull requests are merged.

| Area | Status |
|---|---|
| Shared contract (`HRMService`, data classes, exceptions) | Done |
| RMI over TLS connection | Done |
| Database schema and data-access layer (MySQL) | Done |
| Service logic (login, employee registration, leave, yearly report) | Done |
| Swing client (HR and employee screens) | Done: login, password change, connection handling, employee screens (leave, details, family) and HR screens (employees, leave requests, yearly report) |
| Test manual, use case diagram, demo walkthrough | Planned |

## Features

| Actor | Capabilities |
|---|---|
| HR staff | Register employees (first name, last name, IC/passport number), approve or reject leave, generate a yearly report per employee (profile, family details, leave history) |
| Employee | Log in, view/update own profile and family details, check leave balance, apply for leave, check leave application status |
| System | Central database, TLS-secured RMI, role-based access, thread-safe server, client retry on server failure |

## Architecture

```
Swing GUI client  --(RMI over TLS)-->  RMI server  --(JDBC)-->  Database
 (HR / Employee)                      (service layer)
```

- **Remote interface:** `HRMService` is the contract between client and server.
- **Data classes:** `Employee`, `FamilyMember`, `LeaveApplication`, `YearlyReport` implement `Serializable`.
- **Layers:** client GUI, remote interface, service logic, data access.

## Requirements

- JDK 25 (LTS) or newer (any OS). Older Java will not build this project.
- Apache Maven 3.9+ (NetBeans, IntelliJ and Eclipse can open the `pom.xml` directly)
- MySQL 8.4 (the team's chosen database, see DECISION.md)
- Git

## Getting started

```bash
git clone https://github.com/Maneet7805/hr-management-system-java-rmi.git
cd hr-management-system-java-rmi
```

### Quick start (Windows)

Needs JDK 25, Maven, MySQL 8.4 with the `hrm` database and a MySQL user `hrm_app`, and its password saved as `HRM_DB_PASSWORD` in your Windows user settings.

1. **Once:** double-click `setup.bat`. It chooses a password for the development keys and saves it in your Windows user settings (never in a file of the project), makes the keys and the settings files, creates the database tables, and offers to create the first HR account. It never overwrites a settings file you already have.
2. **Every time:** double-click `run.bat`. It starts MySQL if it is not running, builds the program, starts the server in the background, opens the window, and stops the server again when you close the window.

If MySQL is installed somewhere else, set `HRM_MYSQLD` (full path of `mysqld.exe`) and `HRM_MYSQL_DATA` (its data folder). If something goes wrong, the message says what to do; the server's own messages are in `target/server.log`.

The manual steps below do the same things one by one, for other systems or for running from an IDE.
### Manual setup

1. Install the tools listed under [Requirements](#requirements).
2. Create the TLS keystore and truststore with `scripts/generate-keys.sh` (macOS/Linux) or `scripts/generate-keys.bat` (Windows).
3. For each file in `config/`, copy `*.example.properties` to the same name without `.example` and fill in your values. The real files are git-ignored.
4. Set `HRM_DB_PASSWORD`, `HRM_KEYSTORE_PASSWORD` and `HRM_TRUSTSTORE_PASSWORD` as environment variables.
5. Create the database tables (see the last paragraph of [Running the tests](#running-the-tests)).

To create the first HR account, start the server and open the client, follow [Manual first run](#manual-first-run-for-example-from-an-ide).

### Running the tests

```bash
mvn test
```

The database tests use a real MySQL database called `hrm_test` and a MySQL user `hrm_app` that may use it. Set the user's password in the environment variable `HRM_DB_PASSWORD` first. Without MySQL or that variable, those tests **fail** on purpose, so a green run always means the database was tested. To skip them deliberately (the quotes are needed in PowerShell):

```bash
mvn test "-Dhrm.skipDbTests=true"
```

To create the tables in the development database `hrm`, run `com.bhel.hrm.server.db.DatabaseSetupMain` (it also needs `HRM_DB_PASSWORD`).

### Manual first run (for example from an IDE)

Set these environment variables in the run configuration, never in a file: `HRM_DB_PASSWORD`, `HRM_KEYSTORE_PASSWORD`, and for the client `HRM_TRUSTSTORE_PASSWORD`.

1. `scripts/generate-keys.bat` makes the development keystore and truststore.
2. Copy `config/server.example.properties` to `config/server.properties` and `config/client.example.properties` to `config/client.properties`.
3. Run `DatabaseSetupMain` to create the tables.
4. Run `com.bhel.hrm.server.SeedHrMain` to create the first HR account. It asks for the name, IC/passport number and password (typed without echo). With no console, pass the three details as arguments and set `HRM_SEED_PASSWORD`.
5. Run `com.bhel.hrm.server.HrmServerMain` to start the server, then `com.bhel.hrm.client.SmokeTestMain` to check the connection.
6. Run `com.bhel.hrm.client.HrmClientMain` to open the window and log in. If the server is not running yet, the login screen says so and you can try again; the client also retries by itself for a few seconds.

Passwords are stored only as salted PBKDF2 hashes. An employee registered by HR gets a temporary password and must change it at first login.

## Project layout

```
pom.xml                          build file (Maven, Java 25)
config/                          *.example.properties templates; real config and keystores are git-ignored
scripts/                         generate-keys.sh / .bat (local TLS keys); setup.ps1, run.ps1, common.ps1 (used by setup.bat and run.bat)
setup.bat, run.bat               one-time setup and everyday start (Windows)
src/main/java/com/bhel/hrm/
  common/                        HRMService remote interface + model/ data classes (shared contract)
  net/                           TLS and RMI helpers shared by client and server
  server/                        RMI server and HRMServiceImpl (business rules)
    service/                       rules per area: auth, employees, leave, reports
    dao/                           data-access interfaces
    dao/jdbc/                      JDBC implementations
    db/                            connection setup
    security/                      sessions, password hashing, deserialization filter
  client/                        client entry point and server connection
    ui/                            Swing screens
src/main/resources/              SQL schema and other resources
src/test/java/                   JUnit tests, mirrors the main packages
docs/                            test manual and diagrams
```

Secrets are never stored in files: set `HRM_DB_PASSWORD`, `HRM_KEYSTORE_PASSWORD` and `HRM_TRUSTSTORE_PASSWORD` as environment variables.

## Known gaps

Open work is tracked in [GitHub issues](https://github.com/Maneet7805/hr-management-system-java-rmi/issues): client timeouts, HR password reset and employee edit/deactivate, running on two computers, macOS/Linux support, fault tolerance and the Payroll System.

## Documentation

- [CONTRIBUTING.md](CONTRIBUTING.md): how to work on a branch and open a pull request
- [DECISION.md](DECISION.md): running log of project decisions and why they were made
- [docs/test-manual.md](docs/test-manual.md): manual and automated test cases
- [docs/diagrams/](docs/diagrams): use case and architecture diagrams

## Team workflow

Read [CONTRIBUTING.md](CONTRIBUTING.md) before making changes. In short: never push to `main`, work on a branch, open a pull request, get one teammate to review it.

`main` is protected: changes need a pull request, and the `test` check in [CI](https://github.com/Maneet7805/hr-management-system-java-rmi/actions/workflows/ci.yml) must pass before merging. Use the issue templates in [`.github/ISSUE_TEMPLATE`](.github/ISSUE_TEMPLATE) for tasks and bugs.

## License

Released under the [MIT License](LICENSE).
