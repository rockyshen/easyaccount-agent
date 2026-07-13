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
                    for i in $(seq 1 12); do
                      body=$(curl -sf --connect-timeout 5 --max-time 10 \
                        "http://127.0.0.1:8088/health" 2>/dev/null || true)
                      if [[ -n "$body" ]] && echo "$body" | grep -Fq 'easyaccount-agent'; then
                        echo "Smoke passed on attempt $i: $body"
                        exit 0
                      fi
                      code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 --max-time 10 \
                        "http://127.0.0.1:8088/health" 2>/dev/null || echo "000")
                      echo "Attempt $i failed (HTTP $code, body=${body:-empty}), retrying..."
                      if [[ "$i" -eq 12 ]]; then
                        echo "Smoke failed"
                        exit 1
                      fi
                      sleep 15
                    done
                '''
            }
        }
    }

    post {
        failure {
            echo 'Deploy failed. Check docker compose logs easyaccount-agent'
        }
    }
}
