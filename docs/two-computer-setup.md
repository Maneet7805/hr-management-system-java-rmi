# Running on two computers

The **server computer** runs MySQL and the HR server. The **client computer** runs only the window and connects to the server over the network. All traffic between them is RMI over TLS 1.3 or 1.2 on two TCP ports (1099 for the registry, 1100 for the service).

> **Status.** The setup below is scripted and covered by automated tests that run on one machine, with `127.0.0.1` standing in for the server's network address (`ServerAddressTlsTest`, `KeyScriptsTest`). It has **not yet been demonstrated on two real computers**. Record that run in [test-manual.md](test-manual.md) (cases TC-NET-01 to TC-NET-10) before claiming it works.

## How the client decides to trust the server

The client accepts the server only if both checks pass:

1. **Trusted certificate.** The server's certificate is in the client's truststore.
2. **Matching address.** The address the client connects to is listed in that certificate.

The key scripts always list `localhost` and `127.0.0.1`, plus every address you give them. IP addresses are listed as IP entries, because Java never accepts an IP address through a host name entry.

The server also writes one address into every reference it hands out (`rmi.hostname`), and clients follow it. That address must be in the certificate too. When the server starts, it prints a line saying which address clients use, or a `WARNING` if the certificate does not cover it.

## What goes where

| File or secret | Server computer | Client computer | Secret? |
|---|---|---|---|
| `config/server.keystore` | Made here, stays here | **Never** | Yes, protected by `HRM_KEYSTORE_PASSWORD` |
| `config/server.cer` | Made here | Copied here | No. It is public; the fingerprint check protects the copy |
| `config/client.truststore` | For the window on the server computer | Made here from `server.cer` | Protected by that computer's own `HRM_TRUSTSTORE_PASSWORD` |
| MySQL and `HRM_DB_PASSWORD` | Needed | Not needed | Yes |

No password ever has to be copied from one computer to the other.

## Before you start

- **Same network.** Put both computers on the same network. Campus and guest Wi-Fi often block traffic between devices; a phone hotspot or a home router is safer.
- **Tools.** Install JDK 25 and Maven on both computers.
- **Find the server's IP address.**
  - Windows: `ipconfig`, then read "IPv4 Address" under the Wi-Fi adapter.
  - macOS: `ipconfig getifaddr en0`.
  - The examples below use `192.168.1.20`.
- **If the IP changes, the certificate no longer matches.** An address handed out by the network can change when a computer reconnects. If it does, repeat step 1 with the new address, then step 2 and step 3.

## 1. Server computer

### Windows

1. **Make the keys.** Open a terminal in the project folder and run:

   ```bat
   setup.bat -ServerAddress 192.168.1.20
   ```

   - It adds the address to the certificate, sets `rmi.hostname=192.168.1.20` in `config\server.properties`, and prints the certificate's SHA256 fingerprint. Keep that fingerprint visible for step 3.
   - Running it again is safe. It only makes new keys if the passwords changed, a key file is missing or unreadable, or the address is not in the current certificate. Add `-NewKeys` to force new keys.
   - Double-clicking `setup.bat` cannot pass an address, so use the terminal for this step.

2. **Open the firewall.** In PowerShell **as administrator**, run:

   ```powershell
   New-NetFirewallRule -DisplayName "BHEL HRM server" -Direction Inbound -Protocol TCP -LocalPort 1099,1100 -Action Allow -Profile Private
   ```

   - Also make sure Windows treats the network as **Private**: Settings > Network & internet > Wi-Fi > (your network) > Network profile type.
   - To remove the rule afterwards: `Remove-NetFirewallRule -DisplayName "BHEL HRM server"`.

3. **Start the server.** Run `run.bat`.
   - It starts MySQL, the server, and a window on this computer. The server stops when that window is closed, so keep it open during the demo.
   - Check the first lines of `target\server.log`. They should end with `Clients on other computers connect with server.host=192.168.1.20.`

### macOS or Linux (manual)

MySQL must be set up as described in the [README](../README.md#manual-setup). Then run:

```sh
export HRM_KEYSTORE_PASSWORD='choose-one'  HRM_TRUSTSTORE_PASSWORD='choose-a-different-one'  HRM_DB_PASSWORD='...'
sh scripts/generate-keys.sh 192.168.1.20
cp config/server.example.properties config/server.properties      # then set rmi.hostname=192.168.1.20 in it
mvn -q compile dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime
java -cp "target/classes:target/lib/*" com.bhel.hrm.server.HrmServerMain
```

If macOS asks whether `java` may accept incoming network connections, choose **Allow**.

## 2. Copy the certificate

Copy **only** `config/server.cer` from the server computer into the client computer's `config` folder, for example with a USB stick or a shared drive.

## 3. Client computer

### Windows

1. **Set up the client.** Run `setup-client.bat -ServerAddress 192.168.1.20`, or double-click `setup-client.bat` and type the address when asked.
   - It shows the certificate's SHA256 fingerprint. Type `yes` **only** if it matches the one the server computer printed.
   - It makes this computer's own truststore password and saves it in the Windows user settings.
   - No MySQL, database password or server keys are needed.
2. **Test the connection.** Run `run-client.bat -CheckOnly`. It should print `Server reachable over TLS at 192.168.1.20:1099`.
3. **Open the window.** Run `run-client.bat` (or double-click it).

### macOS or Linux

```sh
export JAVA_HOME=/path/to/jdk-25          # e.g. /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
export HRM_TRUSTSTORE_PASSWORD='any-password-for-this-computer'   # at least 6 characters
sh scripts/setup-client.sh 192.168.1.20  # compare the fingerprint, then type yes
sh scripts/run-client.sh --check         # prints: Server reachable over TLS at 192.168.1.20:1099
sh scripts/run-client.sh                 # opens the window
```

Both variables are needed in every new terminal. To avoid retyping them, add the two `export` lines to `~/.zshrc`.

## Troubleshooting

| What you see | Likely cause | What to do |
|---|---|---|
| `Cannot reach the HR server at 192.168.1.20:1099` | Server not running, wrong address, firewall, or a network that blocks devices from each other | From the client computer, test both ports: Windows `Test-NetConnection 192.168.1.20 -Port 1099` (then `-Port 1100`); macOS `nc -vz 192.168.1.20 1099` (then `1100`). |
| `Secure connection failed: the server's certificate is not trusted or does not match the server name.` | The client has an old `server.cer` (the keys were remade), or the address it uses is not in the certificate (the IP changed, or a name was used instead of the IP) | Check the server's start-up line for a `WARNING`. Run step 1 with the current address, then repeat steps 2 and 3. |
| The login screen appears, but the first action fails with the certificate message | The server's `rmi.hostname` is not an address in its certificate | Read the server's start-up line, then fix it with step 1. |
| The window waits a long time before showing an error | A firewall is dropping packets without answering. Client timeouts are not implemented yet (issue #20) | Close the window, fix the firewall rule, and try again. |
| `Java 25 or newer is needed` (macOS) | `JAVA_HOME` is not set to a JDK 25 | Set `JAVA_HOME` as shown above. |

## Demonstrating and testing it

The full list, with space for results, is in [test-manual.md](test-manual.md) (TC-NET-01 to TC-NET-10). For the demo:

1. **Ports reachable.** Run the `Test-NetConnection` or `nc` commands from Troubleshooting for ports 1099 and 1100.
2. **Secure connection works.** Run `run-client.bat -CheckOnly` or `sh scripts/run-client.sh --check`.
3. **Traffic is encrypted.** Use either:
   - `openssl s_client -connect 192.168.1.20:1099` (built into macOS; on Windows it comes with Git Bash). It should show `Protocol: TLSv1.3`.
   - Wireshark on the client computer with the filter `tcp.port == 1099 || tcp.port == 1100`. Log in and register an employee: only TLS records appear, and "Follow TCP stream" shows no readable names or IC numbers.
4. **Two clients, one database.**
   1. HR registers an employee on the client computer.
   2. The employee logs in on the server computer's window and applies for leave.
   3. HR approves it on the client computer.
   4. The employee sees the new status.
5. **Refusals.** Show that each of these is refused:
   - A `server.cer` from different keys.
   - A `server.host` that is not in the certificate, such as the server computer's name when only its IP was given.
   - The firewall rule removed.
6. **Recovery.** Stop the server and start it again with `run.bat`. The client's next action reconnects on its own.

## Security notes

- **Encryption.** Both ports accept TLS 1.3 and 1.2 only. A plain RMI client or a plain socket gets no RMI reply.
- **Address checking stays on.** It protects against another computer pretending to be the server.
- **No server secret on client computers.** The truststore password on each client computer is its own.
- **Fingerprint check.** Comparing fingerprints catches a `server.cer` that was swapped while being copied.
- **Development certificate.** The certificate is self-signed and valid for one year.
- **No client certificates.** The server does not check client certificates (no mutual TLS); users prove who they are by logging in.
