#!/bin/sh
# Opens the window on a computer that only runs the client (macOS or Linux). The server runs on another computer.
#
# Usage:  scripts/run-client.sh [--check]     (--check only tests the secure connection; no window)
# Needs HRM_TRUSTSTORE_PASSWORD, the password chosen when setup-client.sh was run.
set -e
cd "$(dirname "$0")/.."
. scripts/common.sh

[ -n "$HRM_TRUSTSTORE_PASSWORD" ] || fail "Set HRM_TRUSTSTORE_PASSWORD (the password chosen for setup-client.sh)."
if [ ! -f config/client.truststore ] || [ ! -f config/client.properties ]; then
  fail "The client setup has not been done on this computer. Run scripts/setup-client.sh first."
fi
require_tools
mvn -q compile

main=com.bhel.hrm.client.HrmClientMain
if [ "$1" = "--check" ]; then
  main=com.bhel.hrm.client.SmokeTestMain
fi
server=$(sed -n 's/^server\.host=//p' config/client.properties)
echo "==> HR server at $server"
exec "$JAVA" -cp target/classes "$main"
