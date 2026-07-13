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
