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

echo "== 3. 准备宿主机日志目录 =="
# 优先读 .env 中的 HOST_LOG_DIR（勿依赖交互式 sudo，Jenkins 无 TTY）
if [[ -z "${HOST_LOG_DIR:-}" ]] && [[ -f deploy/.env.docker.pi ]]; then
  # shellcheck disable=SC1091
  HOST_LOG_DIR="$(grep -E '^HOST_LOG_DIR=' deploy/.env.docker.pi | tail -1 | cut -d= -f2- || true)"
fi
HOST_LOG_DIR="${HOST_LOG_DIR:-/var/log/easyaccount-agent}"
export HOST_LOG_DIR

ensure_host_log_dir() {
  local dir="$1"
  if [[ -d "$dir" ]]; then
    echo "日志目录已存在: $dir"
    return 0
  fi
  if mkdir -p "$dir" 2>/dev/null; then
    echo "已创建日志目录: $dir"
    return 0
  fi
  # 无写权限时用 docker（通常 Jenkins 可免密 docker，不可免密 sudo）
  local parent
  parent="$(dirname "$dir")"
  local base
  base="$(basename "$dir")"
  if ! command -v docker >/dev/null 2>&1; then
    echo "无法创建 $dir：无写权限且无 docker" >&2
    return 1
  fi
  echo "通过 docker 以 root 创建: $dir"
  docker run --rm \
    -v "${parent}:/host-parent" \
    docker.m.daocloud.io/library/busybox:1.36 \
    sh -c "mkdir -p \"/host-parent/${base}\" && chmod 755 \"/host-parent/${base}\""
}

ensure_host_log_dir "${HOST_LOG_DIR}"

# 旧版 compose 相对路径错误时，Docker 会以 root 在仓库根把「文件」建成目录；Jenkins 无权限 rm
remove_root_owned_path() {
  local name="$1"
  if [[ ! -e "./${name}" ]]; then
    return 0
  fi
  echo "清理仓库根误创建的 ./${name}（正确路径为 deploy/${name}）"
  if rm -rf "./${name}" 2>/dev/null; then
    return 0
  fi
  docker run --rm \
    -v "${PWD}:/work" \
    -w /work \
    docker.m.daocloud.io/library/busybox:1.36 \
    rm -rf "/work/${name}"
}

remove_root_owned_path prometheus
remove_root_owned_path grafana

echo "== 4. Docker 构建并启动（业务 + Prometheus + Grafana） =="
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
echo "应用日志:  ${HOST_LOG_DIR}/easyaccount-agent.log"
echo "GC 日志:   ${HOST_LOG_DIR}/gc.log"
echo "堆转储:    ${HOST_LOG_DIR}/java_pid*.hprof"
echo "SSE 聊天:  POST http://127.0.0.1:8088/api/chat"
echo "Prometheus: http://127.0.0.1:9090"
echo "Grafana:    http://127.0.0.1:3000  (默认 admin/admin，见 .env)"
echo "公网: http://118.25.46.207:6088 (frp 6088，勿暴露 9090/3000)"
