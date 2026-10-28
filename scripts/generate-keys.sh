#!/bin/sh
# Creates the server's TLS keys in config/. Run from the project root.
#   server.keystore    the server's private key; never leaves the server computer
#   server.cer         the server's public certificate; copy it to each client computer (import-server-cert.sh)
#   client.truststore  lets a client on this same computer trust the server
#
# Usage:  scripts/generate-keys.sh [server-address ...]
# The certificate is always valid for localhost and 127.0.0.1. Add the IP address and/or name other computers
# use to reach this one, for example:  scripts/generate-keys.sh 192.168.1.20
#
# Needs HRM_KEYSTORE_PASSWORD and HRM_TRUSTSTORE_PASSWORD (at least 6 characters each, and different, so a
# client computer never needs the password that protects the server's private key).
# The generated files are git-ignored. Never commit them.
set -e
: "${HRM_KEYSTORE_PASSWORD:?Set HRM_KEYSTORE_PASSWORD first}"
: "${HRM_TRUSTSTORE_PASSWORD:?Set HRM_TRUSTSTORE_PASSWORD first}"
if [ "$HRM_KEYSTORE_PASSWORD" = "$HRM_TRUSTSTORE_PASSWORD" ]; then
  echo "HRM_KEYSTORE_PASSWORD and HRM_TRUSTSTORE_PASSWORD must be different" >&2
  exit 1
fi
for file in config/server.keystore config/server.cer config/client.truststore; do
  if [ -e "$file" ]; then
    echo "$file already exists. Delete the old key files first to make new ones." >&2
    exit 1
  fi
done

# Each address becomes an IP or a DNS entry of the certificate: Java never accepts an IP address through a DNS entry.
names="dns:localhost,ip:127.0.0.1"
common_name="localhost"
for address in "$@"; do
  case "$address" in
    '' | *[!A-Za-z0-9.:-]*)
      echo "'$address' is not a host name or IP address" >&2
      exit 1 ;;
  esac
  case "$address" in
    *:*) kind=ip ;;           # IPv6
    *[!0-9.]*) kind=dns ;;    # anything with a letter or hyphen is a name
    *) kind=ip ;;             # only digits and dots: IPv4
  esac
  names="$names,$kind:$address"
  if [ "$common_name" = "localhost" ]; then
    common_name="$address"
  fi
done

# Passwords are read from the environment (:env), so they never appear in the list of running processes.
keytool -genkeypair -alias hrmserver -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=$common_name" -ext "SAN=$names" -keystore config/server.keystore -storetype PKCS12 \
  -storepass:env HRM_KEYSTORE_PASSWORD -keypass:env HRM_KEYSTORE_PASSWORD
keytool -exportcert -alias hrmserver -keystore config/server.keystore \
  -storepass:env HRM_KEYSTORE_PASSWORD -file config/server.cer
keytool -importcert -noprompt -alias hrmserver -file config/server.cer \
  -keystore config/client.truststore -storetype PKCS12 -storepass:env HRM_TRUSTSTORE_PASSWORD

echo "Created config/server.keystore, config/server.cer and config/client.truststore"
echo "The certificate is valid for: $names"
echo "Its fingerprint (each client computer checks this when it imports server.cer):"
keytool -printcert -file config/server.cer | grep SHA256
