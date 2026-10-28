#!/bin/sh
# Makes the client on this computer trust the HR server: imports the server's public certificate into
# config/client.truststore, protected by this computer's own HRM_TRUSTSTORE_PASSWORD. Run from the project root.
#
# Usage:  scripts/import-server-cert.sh [path-to-server.cer]     (default: config/server.cer)
#
# keytool shows the certificate's SHA256 fingerprint and asks before trusting it. Compare it with the fingerprint
# the server computer printed when it made its keys, and answer yes only if they are the same: a certificate
# swapped on its way here would let another computer pretend to be the server.
set -e
: "${HRM_TRUSTSTORE_PASSWORD:?Set HRM_TRUSTSTORE_PASSWORD first (any password you choose for this computer)}"
certificate="${1:-config/server.cer}"
if [ ! -f "$certificate" ]; then
  echo "$certificate was not found. Copy server.cer from the server computer first." >&2
  exit 1
fi

# Build the new truststore beside the old one, so a refused certificate leaves the old one untouched.
new_truststore=config/client.truststore.new
rm -f "$new_truststore"
keytool -importcert -alias hrmserver -file "$certificate" -keystore "$new_truststore" -storetype PKCS12 \
  -storepass:env HRM_TRUSTSTORE_PASSWORD
# keytool exits normally even when the answer is no; it just does not create the file.
if [ ! -f "$new_truststore" ]; then
  echo "The certificate was not trusted. config/client.truststore is unchanged." >&2
  exit 1
fi
mv -f "$new_truststore" config/client.truststore
echo "config/client.truststore now trusts this server certificate."
