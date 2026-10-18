# HR Management System (Java RMI)

A client-server Human Resource Management system for a start-up, built with Java RMI. HR staff register employees and generate yearly reports; employees manage their own profile and family details and handle leave. All data is stored centrally and all client-server traffic is encrypted.

> Built as a group project for the Distributed Computer Systems module (Year 3, Semester 1). The scenario company is "BHEL".

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

- JDK 17 or newer (any OS)
- A relational database (SQLite or MySQL, to be decided by the team)
- Git

## Getting started

```bash
git clone https://github.com/Maneet7805/hr-management-system-java-rmi.git
cd hr-management-system-java-rmi
```

Build and run instructions will be added here once the first version of the server and client is working.

## Project layout (planned)

```
src/
  common/   shared remote interface and data classes
  server/   RMI server, service logic, data access
  client/   Swing GUI
tests/      unit tests and manual test cases
```

## Team workflow

Read [CONTRIBUTING.md](CONTRIBUTING.md) before making changes. In short: never push to `main`, work on a branch, open a pull request, get one teammate to review it.

## License

Released under the [MIT License](LICENSE).
