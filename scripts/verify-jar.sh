#!/usr/bin/env bash
set -euo pipefail

JAR="${1:-target/easyaccount-agent-0.0.1-SNAPSHOT.jar}"
if [[ ! -f "$JAR" ]]; then
  echo "缺少 jar: $JAR" >&2
  exit 1
fi

if command -v jar >/dev/null 2>&1; then
  jar tf "$JAR" | grep -Fq 'com/rockyshen/easyaccountagent/controller/WebSocketHandler.class'
else
  unzip -l "$JAR" | grep -Fq 'WebSocketHandler.class'
fi

echo "OK  $JAR"
