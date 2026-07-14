# EasyAccounts 记账 Agent

基于 Spring AI Alibaba ReactAgent + MyBatis，连接 `yd_jz` 数据库。

## 本地运行

```bash
export SPRING_AI_DASHSCOPE_API_KEY=sk-xxx
export DB_HOST=127.0.0.1 DB_PORT=3307 DB_USER=root DB_PASSWORD=xxx
# 先执行 scripts/alter_auth_and_user_isolation.sql
mvn spring-boot:run
```

## API

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `POST /api/auth/login` | 本地 user 表登录 |
| `GET /api/auth/me` | 校验会话（免登录） |
| `POST /api/auth/logout` | 登出 |
| `WS /ws?token=` | WebSocket 对话（需 token） |

详见证 `docs/easyaccounts-agent-usage.md`。

## 部署

见 `deploy/README.md`。
