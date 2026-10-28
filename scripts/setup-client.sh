#!/bin/sh
# One-time setup for a computer that only runs the client (macOS or Linux) and connects to the HR server on another
# computer. Nothing for the server is needed here: no MySQL, no database password, no server keys.
#
# Usage:  scripts/setup-client.sh <server-address> [path-to-server.cer]     (default: config/server.cer)
# Needs HRM_TRUSTSTORE_PASSWORD: any password you choose for this computer (at least 6 characters).
set -e
cd "$(dirname "$0")/.."
. scripts/common.sh

server="$1"
[ -n "$server" ] || fail "Give the server computer's IP address or name, for example: scripts/setup-client.sh 192.168.1.20"
case "$server" in
  *[!A-Za-z0-9.:-]*) fail "'$server' is not a host name or IP address" ;;
esac
[ -n "$HRM_TRUSTSTORE_PASSWORD" ] || fail "Choose a password for this computer first: export HRM_TRUSTSTORE_PASSWORD=..."
require_tools

echo "==> Trusting the server certificate"
echo "Compare the SHA256 fingerprint below with the one the server computer showed. Type yes only if they match."
sh scripts/import-server-cert.sh "${2:-config/server.cer}"

# Point the client at the server computer; every other line of client.properties is kept.
if [ ! -f config/client.properties ]; then
  cp config/client.example.properties config/client.properties
  echo "Created config/client.properties"
fi
if grep -q '^server\.host=' config/client.properties; then
  sed -i.bak "s/^server\.host=.*/server.host=$server/" config/client.properties
  rm -f config/client.properties.bak
else
  echo "server.host=$server" >> config/client.properties
fi
echo "Set server.host=$server in config/client.properties"

echo "==> Compiling the program"
mvn -q compile
echo "Setup is finished. Start the program with scripts/run-client.sh (scripts/run-client.sh --check tests the connection)."
