# easyaccount-agent

| 环境 | 地址 |
|------|------|
| Pi 本机 | http://127.0.0.1:8088 · ws://127.0.0.1:8088/ws?token=xxx |
| 公网 | http://118.25.46.207:6088 · ws://118.25.46.207:6088/ws?token=xxx |
| Prometheus | http://127.0.0.1:9090（仅本机/局域网） |
| Grafana | http://127.0.0.1:3000（仅本机/局域网） |

先 `POST /api/auth/login` 获取 token；同一用户再次登录会使旧 token 失效（单端）。

部署前在库执行 `scripts/alter_auth_and_user_isolation.sql`（会清空测试 account/flow）。

对话记忆：启动后 `MysqlSaver` 会在 `yd_jz` 自动建表 `GRAPH_THREAD` / `GRAPH_CHECKPOINT`（`CREATE_IF_NOT_EXISTS`）。checkpoint 含完整 Agent 状态，随轮次增长，注意库容量；清理/备份参考 `scripts/graph_checkpoint_cleanup.sql`。若需手工建表，见 `scripts/graph_checkpoint_ddl.sql`。

## Pi 部署（Docker）

1. 克隆到 `/opt/easyaccount-agent`
2. 复制 `deploy/.env.docker.pi.example` → `deploy/.env.docker.pi`，填写 `SPRING_AI_DASHSCOPE_API_KEY`、`DB_*`，并修改 `GRAFANA_ADMIN_PASSWORD`
3. 执行 `bash deploy/docker-up-pi.sh`

该脚本会：

1. `mvn clean package -DskipTests`
2. 校验 jar
3. 使用三份 compose 启动：业务容器 + Prometheus + Grafana

```text
docker-compose.yml
deploy/docker-compose.pi.yml
deploy/docker-compose.monitor.yml
```

### 监控端点

| 路径 | 说明 |
|------|------|
| `GET /health` | 业务健康检查（Jenkins Smoke） |
| `GET /actuator/health` | Actuator 健康 |
| `GET /actuator/prometheus` | Micrometer Prometheus 抓取 |

Grafana 预置仪表盘：**EasyAccount Agent**（JVM、HTTP、WS 会话、对话耗时、Tool 调用、登录）。

**安全**：`9090` / `3000` / `/actuator/prometheus` 仅供树莓派本机或内网使用，不要通过 frp 映射到公网。

## Jenkins 自动部署

仓库默认分支为 **master**（非 main）。GitHub `push` 到 `master` 后，Jenkins Job 触发：

1. `git reset --hard origin/master`
2. `bash deploy/docker-up-pi.sh`
3. Smoke：`/health` + `/actuator/prometheus`；并探测 Prometheus/Grafana 就绪

```bash
sudo mkdir -p /var/lib/jenkins/jobs/easyaccount-agent
sudo cp /opt/easyaccount-agent/deploy/jenkins/job-config.xml \
  /var/lib/jenkins/jobs/easyaccount-agent/config.xml
sudo chown jenkins:jenkins /var/lib/jenkins/jobs/easyaccount-agent/config.xml
sudo systemctl restart jenkins
```

GitHub 仓库需配置 Webhook：`http://<jenkins>/github-webhook/`，事件选 `Just the push event`。

若日后将默认分支改名为 `main`，需同步修改：

- `Jenkinsfile` 中的 `origin/master`
- `deploy/jenkins/job-config.xml` 中的 `*/master`

## frp（公网 6088 → 本机 8088）

参考 `deploy/frpc-services.toml.example` 追加到 `/etc/frp/frpc.toml` 后 `sudo systemctl restart frpc`。

勿映射 Prometheus/Grafana 端口。
