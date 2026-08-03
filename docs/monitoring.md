# 监控埋点说明

## 架构

```text
Spring Boot (Micrometer)
    │  GET /actuator/prometheus
    ▼
Prometheus (刮取 easyaccount-agent:8088)
    ▼
Grafana (预置仪表盘 EasyAccount Agent)
```

Jenkins 在 `master` push 后执行 `deploy/docker-up-pi.sh`，同时部署业务与监控容器。

## 自动指标（Actuator / Micrometer）

- JVM：堆/非堆、GC、线程
- HTTP：`http.server.requests`（含直方图）
- 进程与系统基础指标

## 业务埋点

| Micrometer 名 | Prometheus 名（示例） | 类型 | 标签 |
|---|---|---|---|
| `easyaccount.sse.active` | `easyaccount_sse_active` | Gauge | — |
| `easyaccount.sse.chat` | `easyaccount_sse_chat_seconds_*` | Timer | `outcome` |
| `easyaccount.sse.busy` | `easyaccount_sse_busy_total` | Counter | — |
| `easyaccount.tool.calls` | `easyaccount_tool_calls_seconds_*` | Timer | `tool`, `outcome` |
| `easyaccount.auth.login` | `easyaccount_auth_login_total` | Counter | `result` |
| `easyaccount.auth.register` | `easyaccount_auth_register_total` | Counter | `result` |
| `easyaccount.auth.logout` | `easyaccount_auth_logout_total` | Counter | — |

埋点代码：

- `metrics/AgentMetrics.java`
- `metrics/MeteredToolCallback.java`（包装 Tool，保持 `StateAwareToolCallback`）
- `controller/ChatSseController.java`
- `controller/AuthController.java`

## 本地验证

```bash
curl -s http://127.0.0.1:8088/actuator/prometheus | grep easyaccount_
```

## 树莓派资源

默认 Prometheus 保留 3 天 / 512MB，Prometheus 与 Grafana 各 `mem_limit: 256m`。可在 `deploy/.env.docker.pi` 调整。
