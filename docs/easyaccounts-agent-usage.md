# EasyAccounts 记账 Agent 使用说明

## 环境变量

```env
SPRING_AI_DASHSCOPE_API_KEY=sk-xxx
DB_HOST=<mysql-host>
DB_PORT=3307
DB_USER=<user>
DB_PASSWORD=<password>
SERVER_PORT=8088
```

Pi 部署：在 `deploy/.env.docker.pi`（不提交 Git）中配置，参考 `deploy/.env.docker.pi.example`。

## API

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `GET /chat?msg=` | 流式对话（SSE） |
| `WS /ws?userId=` | WebSocket 流式对话 |

## 示例

```bash
curl "http://localhost:8088/chat?msg=帮我查一下账户列表"
```

## 架构

ReactAgent → Tools → LedgerFacade → FlowService 等 → MyBatis → MySQL (`yd_jz`)

流水写入必须走 `FlowService`，保证账户余额一致。

## 账户类型

| account_type | 含义 | money | exempt_money |
|--------------|------|-------|--------------|
| 0 | 普通/储蓄 | 余额 | 豁免资产 |
| 1 | 信用卡 | 可用额度 | 信用额度 |

信用卡净资产贡献 = 可用额度 − 信用额度 = −已用额度。

部署前执行：`scripts/alter_account_type.sql`

### 工具补充

| 工具 | 说明 |
|------|------|
| `createAccount` | `accountType=0/1`；信用卡时 `initialMoney` 为信用额度 |
| `addExpense` | 信用卡刷卡：扣减可用额度 |
| `repayCreditCard` | 从普通账户还款到信用卡，恢复可用额度 |
