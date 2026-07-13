#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ ! -f deploy/.env.docker.pi ]]; then
  echo "缺少 deploy/.env.docker.pi"
  exit 1
fi

echo "== 1. Maven 编译 =="
if [[ -d /usr/lib/jvm/java-17-openjdk-arm64 ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi
mvn clean package -DskipTests

echo "== 2. 校验 jar =="
bash scripts/verify-jar.sh

echo "== 3. Docker 构建并启动 =="
COMPOSE_FILES=(-f docker-compose.yml -f deploy/docker-compose.pi.yml)
ENV_FILE=(--env-file deploy/.env.docker.pi)
docker compose "${COMPOSE_FILES[@]}" "${ENV_FILE[@]}" up -d --build --force-recreate

docker image prune -f || true
docker compose "${COMPOSE_FILES[@]}" ps

echo "本机: http://127.0.0.1:8088"
echo "WS: ws://127.0.0.1:8088/ws?userId=xxx"
echo "公网: http://118.25.46.207:6088 (frp 6088)"
