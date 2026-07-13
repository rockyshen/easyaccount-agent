#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${1:-$ROOT/target/easyaccount-agent-0.0.1-SNAPSHOT.jar}"

if [[ -z "${JAVA_HOME:-}" && -d /usr/lib/jvm/java-17-openjdk-arm64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

JAR_CMD=$(command -v jar || true)
test -f "$JAR" || { echo "ERROR: 缺少 $JAR" >&2; exit 1; }

magic=$(head -c 2 "$JAR" | od -An -tx1 | tr -d ' \n')
test "$magic" = "504b" || { echo "ERROR: 无效 jar" >&2; exit 1; }

if [[ -n "$JAR_CMD" ]]; then
  ENTRIES=$(jar tf "$JAR" 2>/dev/null || true)
else
  ENTRIES=$(unzip -l "$JAR" 2>/dev/null || true)
fi

echo "$ENTRIES" | grep -Fq 'com/rockyshen/easyaccountagent/controller/WebSocketHandler.class' || {
  echo "ERROR: jar 缺少 WebSocketHandler" >&2
  exit 1
}

echo "OK  $JAR"
