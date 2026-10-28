@echo off
rem Makes the client on this computer trust the HR server: imports the server's public certificate into
rem config\client.truststore, protected by this computer's own HRM_TRUSTSTORE_PASSWORD. Run from the project root.
rem
rem Usage:  scripts\import-server-cert.bat [path-to-server.cer]     (default: config\server.cer)
rem
rem keytool shows the certificate's SHA256 fingerprint and asks before trusting it. Compare it with the fingerprint
rem the server computer printed when it made its keys, and answer yes only if they are the same: a certificate
rem swapped on its way here would let another computer pretend to be the server.
setlocal
if "%HRM_TRUSTSTORE_PASSWORD%"=="" (echo Set HRM_TRUSTSTORE_PASSWORD first, any password you choose for this computer & exit /b 1)
set "CERTIFICATE=%~1"
if "%CERTIFICATE%"=="" set "CERTIFICATE=config\server.cer"
if not exist "%CERTIFICATE%" (echo %CERTIFICATE% was not found. Copy server.cer from the server computer first. & exit /b 1)

rem Build the new truststore beside the old one, so a refused certificate leaves the old one untouched.
if exist config\client.truststore.new del config\client.truststore.new
keytool -importcert -alias hrmserver -file "%CERTIFICATE%" -keystore config\client.truststore.new -storetype PKCS12 -storepass:env HRM_TRUSTSTORE_PASSWORD || exit /b 1
rem keytool exits normally even when the answer is no; it just does not create the file.
if not exist config\client.truststore.new (echo The certificate was not trusted. config\client.truststore is unchanged. & exit /b 1)
move /y config\client.truststore.new config\client.truststore >nul || exit /b 1
echo config\client.truststore now trusts this server certificate.
