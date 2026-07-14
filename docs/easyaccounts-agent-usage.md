# EasyAccounts 记账 Agent 使用说明

## 环境变量

```env
SPRING_AI_DASHSCOPE_API_KEY=sk-xxx
DB_HOST=<mysql-host>
DB_PORT=3307
DB_USER=<user>
DB_PASSWORD=<password>
SERVER_PORT=8088
EASYACCOUNT_AUTH_ENABLED=true
EASYACCOUNT_AUTH_TOKEN_TTL_DAYS=30
EASYACCOUNT_AUTH_SLIDING_RENEW_DAYS=7
```

Pi 部署：在 `deploy/.env.docker.pi`（不提交 Git）中配置，参考 `deploy/.env.docker.pi.example`。

## 部署前 DDL

依次或按需执行：

1. `scripts/alter_account_type.sql`（信用卡 `account_type`，若未执行过）
2. `scripts/alter_auth_and_user_isolation.sql`（`auth_token`、清空测试账本、`user_id`）

## API

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `POST /api/auth/login` | 登录，body: `{ "name", "password" }`（password 为 int） |
| `POST /api/auth/logout` | 登出，Header: `Authorization: Bearer {token}` |
| `GET /api/auth/me` | 校验免登录态 |
| `WS /ws?token=` | WebSocket 流式对话（需有效 token） |

`GET /chat` SSE **已下线**。

## 登录与免登录

1. 首次：`POST /api/auth/login` → 客户端持久化 `token`
2. 之后：启动时 `GET /api/auth/me`；有效则直接 `WS /ws?token=...`
3. **单端登录**：同一用户再次登录会踢掉旧 token
4. 被踢/过期 → 401，需重新登录

```bash
# 登录
curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"name":"rocky","password":123456}'

# 免登录检查
curl -s http://localhost:8088/api/auth/me -H "Authorization: Bearer $TOKEN"
```

## 架构

ReactAgent → Tools → LedgerFacade → *Service → MyBatis → MySQL (`yd_jz`)

- 流水写入必须走 `FlowService`
- `account` / `flow` 按 `user_id` 隔离；`action` / `type` 全局共享

## 账户类型

| account_type | 含义 | money | exempt_money |
|--------------|------|-------|--------------|
| 0 | 普通/储蓄 | 余额 | 豁免资产 |
| 1 | 信用卡 | 可用额度 | 信用额度 |
