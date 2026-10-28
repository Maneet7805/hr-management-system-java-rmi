@echo off
rem Creates the server's TLS keys in config\. Run from the project root.
rem   server.keystore    the server's private key; never leaves the server computer
rem   server.cer         the server's public certificate; copy it to each client computer (import-server-cert.bat)
rem   client.truststore  lets a client on this same computer trust the server
rem
rem Usage:  scripts\generate-keys.bat [server-address ...]
rem The certificate is always valid for localhost and 127.0.0.1. Add the IP address and/or name other computers
rem use to reach this one, for example:  scripts\generate-keys.bat 192.168.1.20
rem
rem Needs HRM_KEYSTORE_PASSWORD and HRM_TRUSTSTORE_PASSWORD (at least 6 characters each, and different, so a
rem client computer never needs the password that protects the server's private key).
rem The generated files are git-ignored. Never commit them.
setlocal
if "%HRM_KEYSTORE_PASSWORD%"=="" (echo Set HRM_KEYSTORE_PASSWORD first & exit /b 1)
if "%HRM_TRUSTSTORE_PASSWORD%"=="" (echo Set HRM_TRUSTSTORE_PASSWORD first & exit /b 1)
if "%HRM_KEYSTORE_PASSWORD%"=="%HRM_TRUSTSTORE_PASSWORD%" (echo HRM_KEYSTORE_PASSWORD and HRM_TRUSTSTORE_PASSWORD must be different & exit /b 1)
for %%F in (config\server.keystore config\server.cer config\client.truststore) do (
    if exist %%F (echo %%F already exists. Delete the old key files first to make new ones. & exit /b 1)
)

rem Each address becomes an IP or a DNS entry of the certificate: Java never accepts an IP address through a DNS entry.
setlocal EnableDelayedExpansion
set "NAMES=dns:localhost,ip:127.0.0.1"
set "COMMON_NAME=localhost"
:next_address
if "%~1"=="" goto make_keys
echo(%~1| findstr /r /x "[A-Za-z0-9.:-]*" >nul || (echo "%~1" is not a host name or IP address & exit /b 1)
rem Only digits and dots is IPv4, a colon is IPv6, anything else is a name.
set "KIND=dns"
echo(%~1| findstr /r /x "[0-9.]*" >nul && set "KIND=ip"
echo(%~1| findstr ":" >nul && set "KIND=ip"
set "NAMES=!NAMES!,!KIND!:%~1"
if "!COMMON_NAME!"=="localhost" set "COMMON_NAME=%~1"
shift
goto next_address

:make_keys
rem Passwords are read from the environment (:env), so they never appear in the list of running processes.
keytool -genkeypair -alias hrmserver -keyalg RSA -keysize 2048 -validity 365 -dname "CN=%COMMON_NAME%" -ext "SAN=%NAMES%" -keystore config\server.keystore -storetype PKCS12 -storepass:env HRM_KEYSTORE_PASSWORD -keypass:env HRM_KEYSTORE_PASSWORD || exit /b 1
keytool -exportcert -alias hrmserver -keystore config\server.keystore -storepass:env HRM_KEYSTORE_PASSWORD -file config\server.cer || exit /b 1
keytool -importcert -noprompt -alias hrmserver -file config\server.cer -keystore config\client.truststore -storetype PKCS12 -storepass:env HRM_TRUSTSTORE_PASSWORD || exit /b 1

echo Created config\server.keystore, config\server.cer and config\client.truststore
echo The certificate is valid for: %NAMES%
echo Its fingerprint (each client computer checks this when it imports server.cer):
keytool -printcert -file config\server.cer | findstr SHA256
