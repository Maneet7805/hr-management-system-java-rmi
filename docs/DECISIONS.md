# Design Decisions

Every choice that the code **and** the report must agree on. Fill in each row as a group, then lock the table. If a decision changes later, update this file in the same PR as the code and tell the group.

**Lock date:** _to be agreed (suggested: end of Week 9)_

| # | Decision | Status | Choice | Decided by / date |
|---|---|---|---|---|
| 1 | Client type | Decided | Swing GUI (not JavaFX) | |
| 2 | Database | Open | SQLite or MySQL | |
| 3 | Secure communication | Open | TLS via `SslRMIClientSocketFactory` / `SslRMIServerSocketFactory` | |
| 4 | Password hashing | Open | BCrypt or PBKDF2 | |
| 5 | Class structure | Open | e.g. `User` base class, `Employee`, `HRStaff` | |
| 6 | Employee first login | Open | How does a newly registered employee get credentials? | |
| 7 | Leave approval | Open | HR approves or rejects (requirement FR6 is inferred in the brief) | |
| 8 | Leave rules | Open | Leave types, days per year, carry-over, can balance go negative? | |
| 9 | Profile fields | Open | Which fields exist; which are read-only (e.g. IC/passport number)? | |
| 10 | Fault tolerance | Open | Client retry/reconnect, transaction rollback, server rebinds on restart | |

## Report impact

Sections that argue for a choice (Research, Multi-threading/Serialization/OOP) must match this table. When a row changes, update the report in the same week.
