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
| `POST /api/auth/login` | 本地 user 表登录（password 为字符串） |
| `GET /api/auth/me` | 校验会话（免登录） |
| `POST /api/auth/logout` | 登出 |
| `GET/POST/PUT/DELETE /api/accounts` | 账户管理（需 Bearer） |
| `GET /api/actions` · `GET /api/types?actionId=` | 分类只读（需 Bearer） |
| `GET /api/dashboard` | 概览分析（需 Bearer） |
| `WS /ws?token=` | WebSocket 对话（需 token） |

详见证 `docs/easyaccounts-agent-usage.md`。

## 部署

见 `deploy/README.md`。
