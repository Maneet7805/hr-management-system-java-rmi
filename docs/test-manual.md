# Test manual

Manual and automated test cases for the RMI system (assignment task 10). Fill in as features are built.

| ID | Requirement | Steps | Expected result | Actual result | Pass/Fail |
|---|---|---|---|---|---|
| TC-NET-01 | Client and server run on two computers (setup: [two-computer-setup.md](two-computer-setup.md)) | From the client computer, test ports 1099 and 1100 on the server's IP (`Test-NetConnection` or `nc -vz`) | Both ports reachable | | |
| TC-NET-02 | Secure connection across the network | `run-client.bat -CheckOnly` or `sh scripts/run-client.sh --check` on the client computer | `Server reachable over TLS at <server IP>:1099` | | |
| TC-NET-03 | Traffic is encrypted | `openssl s_client -connect <server IP>:1099`, or capture with Wireshark while logging in and registering an employee | TLSv1.3 negotiated; no readable names, IC numbers or passwords in the capture | | |
| TC-NET-04 | One central database for every client | HR registers an employee on the client computer; the employee logs in on the server computer and applies for leave; HR approves it on the client computer | The employee sees the approved status | | |
| TC-NET-05 | Only the real server is trusted | On the client computer, import a `server.cer` made by different keys, then connect | Refused: "the server's certificate is not trusted or does not match the server name" | | |
| TC-NET-06 | The address must match the certificate | Set `server.host` to an address that is not in the certificate (for example the server computer's name when only its IP was given) | Refused with the same certificate message | | |
| TC-NET-07 | A refused certificate changes nothing | Run the client setup and answer `no` at the fingerprint question | "The certificate was not trusted"; the previous truststore still works | | |
| TC-NET-08 | Blocked network gives a clear message | Remove the firewall rule on the server computer, then connect | "Cannot reach the HR server ..." (may take a while: no client timeouts yet, issue #20) | | |
| TC-NET-09 | Client recovers after a server restart | Stop the server and start it again, then use the client window | The next action works without restarting the client | | |
| TC-NET-10 | The server warns about a wrong address | Set `rmi.hostname` to an address not in the certificate and start the server | Start-up line begins with `WARNING` and names the address | | |
| TC-NET-11 | New keys are never put under a running server | With `run.bat` open, run `setup.bat -ServerAddress <ip> -NewKeys` | Refused: "something is listening on port 1099"; the key files are unchanged | | |
| TC-AUTO-NET | Address and certificate rules, automated | `mvn verify` runs `ServerAddressTlsTest`, `KeyScriptsTest`, `PowerShellScriptsTest` (needs `pwsh`), `CertificateNamesTest` and `ServerSettingsTest` on one machine (127.0.0.1 stands in for the network address) | All pass | | |
