# Shared helpers for setup-client.sh and run-client.sh (macOS and Linux). Not meant to be run on its own.

# The java to use: the one in JAVA_HOME if it is set (Maven uses that one too), otherwise the one on the PATH.
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

# The project is built for Java 25; an older java would stop with a hard-to-read UnsupportedClassVersionError.
require_tools() {
  command -v mvn > /dev/null || fail "Maven was not found. Install it (for example: brew install maven)."
  version=$("$JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.specification\.version = //p')
  if ! [ "${version:-0}" -ge 25 ] 2> /dev/null; then
    fail "Java 25 or newer is needed, but $JAVA is version ${version:-unknown}. Set JAVA_HOME to a JDK 25."
  fi
}
