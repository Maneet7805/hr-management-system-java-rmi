# Phase 2: RMI over TLS walking skeleton

Status: **Approved by owner and built on branch `feature/rmi-tls-skeleton`** (2026-10-19). Not yet committed or merged.

Built as designed, with these differences:
- Added `net.SettingsSupport` (shared port/text/environment parsing) and `net.RmiNames` (the registry name), which the design did not list.
- `rmi.hostname` is now in `config/server.example.properties` set to `localhost`. Without it RMI puts the machine's own address into the stub, and the host-name check then fails.
- The deserialization filter is unit-tested and attached to the exported service, but no test sends a forbidden class through a real RMI call. The typed stub cannot send one.
- `HrmServerMain` and `SmokeTestMain.main` have no unit tests; they are thin wiring. Both were run for real in a scratch folder instead.

## Intent

Prove the riskiest part of the system first: a Swing-ready client talks to the server over RMI, and everything on the wire is encrypted. The skeleton answers `ping()` and nothing else. Business logic, the database and the GUI come later, on top of a connection that already works.

Why now: TLS and RMI networking fail in confusing ways (certificates, hostnames, ports, firewalls). Finding those problems before there is business logic keeps them cheap to fix. It also gives the report real evidence for the "secure communication" requirement.

## Goals

- Server starts an RMI registry and exports `HRMService`, both over TLS 1.3 / 1.2 only.
- A client with the right truststore can `ping()`.
- Everything that goes wrong gives the user a plain message, not a stack trace.
- The server accepts only the contract's own classes from the network (deserialization filter).
- Tests build their own throwaway certificates; no key or password is committed.

## Non-goals

- No login, database, leave logic or GUI.
- No client authentication certificates (mutual TLS). Users log in with a password in Phase 4.
- No retry or reconnect logic (Phase 6).

## Components

New shared package `com.bhel.hrm.net`, so `common` stays frozen:

| Class | Job |
|---|---|
| `TlsContexts` | Builds an `SSLContext` from a keystore (server) or truststore (client), TLS 1.3 / 1.2 only |
| `TlsClientSocketFactory` | RMI client socket factory that sends the TLS handshake and **checks the server name against the certificate** |
| `ClientTls` | Holds the one `SSLContext` a client process uses; the factory reads it each time it opens a socket |

Server (`com.bhel.hrm.server`):

| Class | Job |
|---|---|
| `ServerSettings` | Reads `config/server.properties` plus environment variables; clear error when something is missing |
| `HRMServiceImpl` | `ping()` returns normally; every other method throws `ServiceUnavailableException("Not implemented yet")` |
| `RmiServer` | `start(settings)` creates the TLS registry, exports the service, binds it; `close()` unexports both |
| `security.RequestFilters` | The deserialization filter |
| `HrmServerMain` | `main` that wires the above and stays running |

Client (`com.bhel.hrm.client`):

| Class | Job |
|---|---|
| `ClientSettings` | Reads `config/client.properties` plus environment variables |
| `ServerConnection` | `connect(settings)` returns an `HRMService`; turns every failure into a `ServiceUnavailableException` with a readable message |
| `SmokeTestMain` | Tiny command-line tool: connect, ping, print the result. Handy for the test manual and demos |

## Decisions (assistant calls, owner can override)

1. **Custom TLS socket factories instead of the JDK's `SslRMIClientSocketFactory`.** The JDK client factory uses JVM-wide system properties and does not check that the certificate matches the host name. A custom one is about 40 lines, lets tests use different certificates in one JVM, and closes the "anyone with any trusted certificate can impersonate the server" gap.
2. **Host-name checking is on.** The dev certificate is for `localhost`. For a demo on two machines, the key script needs the server's real name. That is a Phase 8 change to `scripts/generate-keys.*`.
3. **Deserialization filter allows only** `com.bhel.hrm.common.**`, `java.lang.*`, `java.util.*`, `java.time.*`, with size and depth limits, and rejects everything else. `java.util.*` is wider than strictly needed; it is the price of keeping the filter short enough to explain.
4. **Fixed object port** (`rmi.objectPort`, default 1100) next to the registry port (1099), so a firewall needs two known ports. Added to `config/server.example.properties`, along with an optional `rmi.hostname`.
5. **Unimplemented methods fail with `ServiceUnavailableException`**, not `UnsupportedOperationException`, so clients already handle them. They are replaced in Phases 4 and 5.

## Tests (written first, JUnit 5)

Each test makes its own certificates with `keytool` in a temp folder.

1. Correct truststore: `ping()` succeeds.
2. Wrong truststore (a different server's certificate): fails with a message about the certificate not being trusted.
3. Host-name check: connecting to `127.0.0.1` when the certificate says `localhost` fails; `localhost` works.
4. A plain, non-TLS RMI client cannot call the server, and a raw socket gets no RMI reply.
5. Nothing listening: `ServerConnection` reports "cannot reach the server" in one readable line.
6. A contract data class (`FamilyMember`) passes the filter on a real call and reaches the "Not implemented yet" answer.
7. The filter accepts contract classes and rejects a class outside the list (for example `AtomicInteger`) and oversize arrays.
8. `ServerSettings` and `ClientSettings`: read the file, take passwords from environment variables, apply the defaults, and fail clearly when a password is missing.

## Done when

- `mvn test` passes the tests above, and `mvn compile` exits 0.
- No keystore, truststore or password is tracked by git (`git status` clean of them).
- Manual run: the owner generates keys, starts `HrmServerMain`, runs `SmokeTestMain`, and sees "Server reachable over TLS".

## What the owner needs to do for the manual run (later, not for the tests)

Set `HRM_KEYSTORE_PASSWORD` and `HRM_TRUSTSTORE_PASSWORD` to the same value (the existing key script uses one password for both), run `scripts\generate-keys.bat`, and copy the two example config files to `server.properties` and `client.properties`.

## New dependencies

None. Everything is in the JDK.
