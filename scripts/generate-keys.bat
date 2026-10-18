@echo off
rem Creates a LOCAL development keystore (server) and truststore (client) in config\.
rem Run from the project root. Needs HRM_KEYSTORE_PASSWORD set (min 6 characters).
rem The generated files are git-ignored. Never commit them.
if "%HRM_KEYSTORE_PASSWORD%"=="" (echo Set HRM_KEYSTORE_PASSWORD first & exit /b 1)
keytool -genkeypair -alias hrmserver -keyalg RSA -keysize 2048 -validity 365 -dname "CN=localhost" -keystore config\server.keystore -storepass %HRM_KEYSTORE_PASSWORD% -keypass %HRM_KEYSTORE_PASSWORD% || exit /b 1
keytool -exportcert -alias hrmserver -keystore config\server.keystore -storepass %HRM_KEYSTORE_PASSWORD% -file config\server.cer || exit /b 1
keytool -importcert -noprompt -alias hrmserver -file config\server.cer -keystore config\client.truststore -storepass %HRM_KEYSTORE_PASSWORD% || exit /b 1
del config\server.cer
echo Created config\server.keystore and config\client.truststore
