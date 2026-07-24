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

对话记忆表 `GRAPH_THREAD` / `GRAPH_CHECKPOINT` 由应用启动时 `MysqlSaver`（`CREATE_IF_NOT_EXISTS`）自动创建，一般无需手工 DDL。若运维希望与应用解耦，可参考 `scripts/graph_checkpoint_ddl.sql`。

## API

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `POST /api/auth/register` | 注册并自动登录；用户名已存在返回 409 |
| `POST /api/auth/login` | 登录，body: `{ "name", "password" }`（password 为字符串，支持大小写与符号） |
| `POST /api/auth/logout` | 登出，Header: `Authorization: Bearer {token}` |
| `GET /api/auth/me` | 校验免登录态 |
| `WS /ws?token=` | WebSocket 流式对话（需有效 token） |

`GET /chat` SSE **已下线**。

## 注册与登录

1. 无账号：`POST /api/auth/register` → 存 token → 连 WS
2. 有账号：`POST /api/auth/login` → 存 token
3. 之后启动：`GET /api/auth/me`；有效则直接连 WS
4. **单端登录**：再次登录会踢掉旧 token
5. 建议执行 `scripts/alter_user_name_unique.sql` 给 `user.name` 加唯一索引

```bash
# 注册（成功即返回 token）
curl -s -X POST http://localhost:8088/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"rocky","password":"P@ssw0rd!"}'

# 登录
curl -s -X POST http://localhost:8088/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"name":"rocky","password":"P@ssw0rd!"}'

# 免登录检查
curl -s http://localhost:8088/api/auth/me -H "Authorization: Bearer $TOKEN"
```

## 架构

ReactAgent → Tools → LedgerFacade → *Service → MyBatis → MySQL (`yd_jz`)

- 流水写入必须走 `FlowService`
- `account` / `flow` 按 `user_id` 隔离；`action` / `type` 全局共享

## 对话记忆（跨重启）

- Agent 使用 `MysqlSaver`，checkpoint 落在本库 `yd_jz`（`GRAPH_THREAD` / `GRAPH_CHECKPOINT`）
- WebSocket 会话键：`threadId = u-{userId}`，**一用户一条持久会话链**；用户间互不串上下文
- 服务重启 / Redeploy 后，同用户续聊仍可带上历史 Agent 状态
- 每次模型调用会注入服务器当前日期（`Asia/Shanghai`），避免「今天」被模型误判为训练截止年份
- 本阶段不做多会话房间、前端可见历史列表（另开迭代）

### 清空某用户记忆

暂无产品 API，可按用户执行 SQL（`thread_name` 为 `u-{userId}`），示例见 `scripts/graph_checkpoint_cleanup.sql`：

```sql
-- 释放会话（推荐）
UPDATE GRAPH_THREAD
SET is_released = TRUE
WHERE thread_name = 'u-1' AND is_released = FALSE;

-- 或物理删除（CASCADE 删除对应 checkpoint）
DELETE FROM GRAPH_THREAD WHERE thread_name = 'u-1';
```

长对话会增大 checkpoint 体积；定期 TTL 清理示例亦在上述脚本中。

## 账户类型

| account_type | 含义 | money | exempt_money |
|--------------|------|-------|--------------|
| 0 | 普通/储蓄 | 余额 | 豁免资产 |
| 1 | 信用卡 | 可用额度 | 信用额度 |
