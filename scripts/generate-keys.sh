#!/bin/sh
# Creates a LOCAL development keystore (server) and truststore (client) in config/.
# Run from the project root. Needs HRM_KEYSTORE_PASSWORD set (min 6 characters).
# The generated files are git-ignored. Never commit them.
set -e
: "${HRM_KEYSTORE_PASSWORD:?Set HRM_KEYSTORE_PASSWORD first}"
keytool -genkeypair -alias hrmserver -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=localhost" -keystore config/server.keystore \
  -storepass "$HRM_KEYSTORE_PASSWORD" -keypass "$HRM_KEYSTORE_PASSWORD"
keytool -exportcert -alias hrmserver -keystore config/server.keystore \
  -storepass "$HRM_KEYSTORE_PASSWORD" -file config/server.cer
keytool -importcert -noprompt -alias hrmserver -file config/server.cer \
  -keystore config/client.truststore -storepass "$HRM_KEYSTORE_PASSWORD"
rm config/server.cer
echo "Created config/server.keystore and config/client.truststore"
