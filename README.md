# EasyAccounts 记账 Agent

基于 Spring AI Alibaba ReactAgent + MyBatis，连接 `yd_jz` 数据库。

## 本地运行

```bash
export SPRING_AI_DASHSCOPE_API_KEY=sk-xxx
export DB_HOST=127.0.0.1 DB_PORT=3307 DB_USER=root DB_PASSWORD=xxx
mvn spring-boot:run
```

## API

| 接口 | 说明 |
|------|------|
| `GET /health` | 健康检查 |
| `GET /chat?msg=` | SSE 流式对话 |
| `WS /ws?userId=` | WebSocket 流式对话 |

## 部署

见 `deploy/README.md`。
