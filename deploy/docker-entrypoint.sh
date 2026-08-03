#!/bin/sh
set -eu

APP_NAME="${APP_NAME:-easyaccount-agent}"
LOG_HOME="${LOG_HOME:-/var/log/${APP_NAME}}"
JVM_MAX="${JVM_MAX:-384m}"

mkdir -p "${LOG_HOME}"

# 应用日志目录由 logback 使用；GC / 堆转储也落在同一目录
exec java \
  -Xms96m \
  -Xmx"${JVM_MAX}" \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=300 \
  -XX:MetaspaceSize=96m \
  -XX:MaxMetaspaceSize=256m \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath="${LOG_HOME}/" \
  -Xlog:gc*:file="${LOG_HOME}/gc.log":time,uptime,level,tags:filecount=5,filesize=20M \
  -DLOG_HOME="${LOG_HOME}" \
  -Djava.security.egd=file:/dev/./urandom \
  -jar /app/app.jar
