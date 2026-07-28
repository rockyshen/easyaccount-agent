# EasyAccounts 记账 Agent

基于 Spring AI Alibaba ReactAgent + MyBatis，连接 `yd_jz` 数据库。

## 本地运行

```bash
export SPRING_AI_DASHSCOPE_API_KEY=sk-xxx
export DB_HOST=127.0.0.1 DB_PORT=3307 DB_USER=root DB_PASSWORD=xxx
export LOG_HOME=./logs   # 默认写 /var/log/easyaccount-agent/
# 先执行 scripts/alter_auth_and_user_isolation.sql
mvn spring-boot:run
```

生产日志目录：`/var/log/easyaccount-agent/`（应用日志、GC、堆转储）。

## API

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `GET /actuator/prometheus` | Prometheus 指标（Micrometer） |
| `POST /api/auth/login` | 本地 user 表登录（password 为字符串） |
| `GET /api/auth/me` | 校验会话（免登录） |
| `POST /api/auth/logout` | 登出 |
| `GET/POST/PUT/DELETE /api/accounts` | 账户管理（需 Bearer） |
| `GET /api/actions` · `GET /api/types?actionId=` | 分类只读（需 Bearer） |
| `GET /api/dashboard` | 概览分析（需 Bearer） |
| `POST /api/chat` | SSE 流式对话（需 Bearer，`text/event-stream`） |

详见证 `docs/easyaccounts-agent-usage.md`；iOS SSE 见 `docs/ios-swift-sse-handoff.md`。

## 监控

Micrometer → `/actuator/prometheus` → Prometheus → Grafana。业务埋点包括：

- `easyaccount.sse.active`：进行中的 SSE 对话数
- `easyaccount.sse.chat`：对话耗时（outcome=success/error/busy）
- `easyaccount.sse.busy`：因忙拒绝次数
- `easyaccount.tool.calls`：Agent Tool 调用耗时
- `easyaccount.auth.login|register|logout`：鉴权事件

树莓派 Docker 部署会一并拉起 Prometheus / Grafana，见 `deploy/README.md`。

## 部署

见 `deploy/README.md`（含 Jenkins：`master` push 自动部署）。
