#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_GLOB="$ROOT/build/libs/*-all.jar"

have_jar() {
  compgen -G "$JAR_GLOB" > /dev/null
}

if ! have_jar; then
  echo "[build] Creating uber jar..."
  "$ROOT/gradlew" uberJar
fi

# If build failed, bail with message
if ! have_jar; then
  echo "Error: uber jar not produced (check Gradle errors above)" >&2
  exit 1
fi

# Pick the most recent *-all.jar
JAR="$(ls -1t $JAR_GLOB | head -1)"

exec java -jar "$JAR" "$@"