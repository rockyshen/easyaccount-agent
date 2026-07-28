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

echo "== 3. Docker 构建并启动（业务 + Prometheus + Grafana） =="
COMPOSE_FILES=(
  -f docker-compose.yml
  -f deploy/docker-compose.pi.yml
  -f deploy/docker-compose.monitor.yml
)
ENV_FILE=(--env-file deploy/.env.docker.pi)
docker compose "${COMPOSE_FILES[@]}" "${ENV_FILE[@]}" up -d --build --force-recreate

docker image prune -f || true
docker compose "${COMPOSE_FILES[@]}" "${ENV_FILE[@]}" ps

echo "本机 API:  http://127.0.0.1:8088"
echo "健康检查:  http://127.0.0.1:8088/health"
echo "指标抓取:  http://127.0.0.1:8088/actuator/prometheus"
echo "Prometheus: http://127.0.0.1:9090"
echo "Grafana:    http://127.0.0.1:3000  (默认 admin/admin，见 .env)"
echo "WS: ws://127.0.0.1:8088/ws?token=xxx"
echo "公网: http://118.25.46.207:6088 (frp 6088，勿暴露 9090/3000)"
