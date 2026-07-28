pipeline {
    agent any

    triggers { githubPush() }

    options {
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
    }

    environment {
        PROJECT_DIR = '/opt/easyaccount-agent'
    }

    stages {
        stage('Pull & Deploy') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    git config --global --add safe.directory /opt/easyaccount-agent
                    cd "$PROJECT_DIR"
                    # 仓库默认分支为 master（若日后改名 main，同步改此处与 job-config）
                    git fetch origin master
                    git reset --hard origin/master
                    bash deploy/docker-up-pi.sh
                '''
            }
        }
        stage('Smoke') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    sleep 25

                    smoke_health() {
                      local i body code
                      for i in $(seq 1 12); do
                        body=$(curl -sf --connect-timeout 5 --max-time 10 \
                          "http://127.0.0.1:8088/health" 2>/dev/null || true)
                        if [[ -n "$body" ]] && echo "$body" | grep -Fq 'easyaccount-agent'; then
                          echo "Health smoke passed on attempt $i: $body"
                          return 0
                        fi
                        code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 \
                          "http://127.0.0.1:8088/health" 2>/dev/null || echo "000")
                        echo "Health attempt $i failed (HTTP $code, body=${body:-empty}), retrying..."
                        sleep 15
                      done
                      return 1
                    }

                    smoke_prometheus() {
                      local i body code
                      for i in $(seq 1 8); do
                        body=$(curl -sf --connect-timeout 5 --max-time 10 \
                          "http://127.0.0.1:8088/actuator/prometheus" 2>/dev/null || true)
                        if [[ -n "$body" ]] && echo "$body" | grep -Eq 'jvm_memory_used_bytes|process_uptime_seconds'; then
                          echo "Prometheus scrape smoke passed on attempt $i"
                          return 0
                        fi
                        code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 \
                          "http://127.0.0.1:8088/actuator/prometheus" 2>/dev/null || echo "000")
                        echo "Prometheus attempt $i failed (HTTP $code), retrying..."
                        sleep 10
                      done
                      return 1
                    }

                    smoke_monitor_stack() {
                      local code
                      code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 \
                        "http://127.0.0.1:9090/-/ready" 2>/dev/null || echo "000")
                      if [[ "$code" != "200" ]]; then
                        echo "WARN: Prometheus ready check HTTP $code（可稍后手工确认）"
                      else
                        echo "Prometheus ready OK"
                      fi
                      code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 \
                        "http://127.0.0.1:3000/api/health" 2>/dev/null || echo "000")
                      if [[ "$code" != "200" ]]; then
                        echo "WARN: Grafana health HTTP $code（可稍后手工确认）"
                      else
                        echo "Grafana health OK"
                      fi
                    }

                    if ! smoke_health; then
                      echo "Smoke failed: /health"
                      exit 1
                    fi
                    if ! smoke_prometheus; then
                      echo "Smoke failed: /actuator/prometheus"
                      exit 1
                    fi
                    smoke_monitor_stack
                '''
            }
        }
    }

    post {
        failure {
            echo 'Deploy failed. Check: docker compose -f docker-compose.yml -f deploy/docker-compose.pi.yml -f deploy/docker-compose.monitor.yml logs'
        }
    }
}
