# EasyAccounts iOS (Swift) — SSE 对话对接

> 面向：Swift 客户端 Agent / iOS 开发  
> 后端：`easyaccount-agent`  
> 文档日期：2026-07-29  
> 状态：**WebSocket 已下线，聊天统一为 `POST /api/chat` SSE**；断点续传见 `docs/ios-swift-sse-resume-handoff.md`

账户 / 分类 / 概览 REST 仍见 `docs/ios-swift-handoff.md`。本文覆盖鉴权 + 基础流式对话；**后台断线续传、cancel、streamId** 以续传文档为准。

---

## 1. 环境

| 环境 | Base URL |
|------|----------|
| 本机 / Pi | `http://127.0.0.1:8088` |
| 公网 | `http://118.25.46.207:6088` |

- 时区：`Asia/Shanghai`
- 金额字段（若在 Tool 结果文本里出现）为字符串两位小数
- 无统一 `{code,data,msg}` 信封：HTTP 错误多为 `{ "message": "..." }`

---

## 2. 鉴权（与 REST 相同）

```http
Authorization: Bearer <token>
Content-Type: application/json
```

1. `POST /api/auth/login` 或 `register` → 存 Keychain：`token`、`expiresAt`、`user.id`、`user.name`
2. 启动：`GET /api/auth/me`；`401` → 清 Keychain → 登录页
3. **单端登录**：再次登录会使旧 token 全部失效
4. SSE **不要**用 query `?token=`；必须用 `Authorization` Header（`URLSession` 流式请求可带头）

---

## 3. 流式对话 SSE

### 3.1 请求

`POST /api/chat`  
`Accept: text/event-stream`（建议）  
`Content-Type: application/json`

```json
{ "content": "今天午饭花了 35 元，记到微信" }
```

### 3.2 成功时的响应

- HTTP `200`
- `Content-Type: text/event-stream`
- 服务端按 SSE 推送多条事件，直到连接结束

超时：服务端约 **300s**；客户端建议同样设置长超时，并允许用户取消 `URLSessionTask`。

### 3.3 事件协议

每条 SSE：

```text
event: <name>
data: <json>

```

| event | data JSON | 说明 |
|-------|-----------|------|
| `started` | `{ "type":"started", "content":"ok", "streamId", "eventId" }` | 流开始；请持久化 `streamId` |
| `message_delta` | `{ "type":"message_delta", "content":"<增量>", "streamId", "eventId" }` | 模型增量；按序拼接 |
| `message_end` | `{ "type":"message_end", "content":"<完整回复>", "streamId", "eventId" }` | 本轮结束；`content` 为全部 delta 拼接 |
| `error` | `{ "type":"error", "message":"<原因>", "streamId", "eventId" }` | 本轮失败（HTTP 可能仍为 200） |

解析建议：以 `event` 名为准；`data` 里的 `type` 与 event 名一致，可作校验。帧上可能带 `id: <eventId>`。旧客户端忽略未知字段仍可用。

### 3.4 非流式 HTTP 错误（开始推流前）

| HTTP | body | 场景 |
|------|------|------|
| `400` | `{ "message":"消息不能为空" }` | content 空 |
| `401` | `{ "message":"未登录或会话已失效" }` | token 无效 |
| `409` | `{ "message":"上一条消息仍在处理中", "streamId"?, ... }` | 同用户并发第二轮；可改走续传 |

收到 `409` 应禁用发送或先 `GET` 续传旧流；详见续传文档。

### 3.5 会话记忆

- 服务端 `threadId = u-{userId}`，**一用户一条持久链**（MySQL checkpoint）
- 客户端无需传 sessionId
- 重启 App 后续聊仍可能带上服务端历史；本阶段无「清空记忆」产品 API

---

## 4. Swift 接入要点

### 4.1 为什么不用 `EventSource`

系统没有成熟的原生 EventSource，且标准 EventSource **不能**自定义 `Authorization`。  
用 **`URLSession.bytes(for:)`**（iOS 15+）或 `URLSession.shared.dataTask` + 增量解析 SSE。

### 4.2 推荐流程

1. 组装 `URLRequest`：`POST`、Bearer、JSON body、`timeoutInterval ≈ 300`
2. `let (bytes, response) = try await URLSession.shared.bytes(for: request)`
3. 若 `statusCode` 为 401/409/400：读完 body 解析 `message`，不要当 SSE
4. 若 `200`：按行解析 `event:` / `data:`，空行提交一条事件
5. UI：`message_delta` → 追加气泡；`message_end` → 定稿；`error` → 提示
6. 用户点停止：调用 `POST /api/chat/streams/{streamId}/cancel`（不要仅靠断连）；进后台只需断开连接以续传

### 4.3 最小解析骨架（示意）

```swift
struct ChatServerEvent: Decodable {
    let type: String
    let content: String?
    let message: String?
    let streamId: String?
    let eventId: Int64?
}

enum SseChatEvent {
    case started
    case delta(String)
    case end(String)
    case error(String)
}

func parseSseLines(_ lines: [String]) -> (String?, String?) {
    var event: String?
    var data: String?
    for line in lines {
        if line.hasPrefix("event:") {
            event = String(line.dropFirst(6)).trimmingCharacters(in: .whitespaces)
        } else if line.hasPrefix("data:") {
            let part = String(line.dropFirst(5)).trimmingCharacters(in: .whitespaces)
            data = (data.map { $0 + "\n" } ?? "") + part
        }
    }
    return (event, data)
}

func decodeEvent(name: String?, data: String?) throws -> SseChatEvent? {
    guard let data, let raw = data.data(using: .utf8) else { return nil }
    let obj = try JSONDecoder().decode(ChatServerEvent.self, from: raw)
    switch name ?? obj.type {
    case "started": return .started
    case "message_delta": return .delta(obj.content ?? "")
    case "message_end": return .end(obj.content ?? "")
    case "error": return .error(obj.message ?? "处理失败")
    default: return nil
    }
}
```

### 4.4 curl 自测

```bash
TOKEN=xxx
curl -N -X POST "http://127.0.0.1:8088/api/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"content":"查看我的账户"}'
```

---

## 5. UI / 产品建议

| 项 | 建议 |
|----|------|
| 发送中 | 禁用输入与发送；仅允许「停止」 |
| 增量展示 | 对 `message_delta` 做主线程追加，避免整段闪烁 |
| 失败 | `error` 事件或 HTTP 4xx 用 `message` 文案 |
| 401 | 清 Keychain，回登录 |
| 后台 | App 进后台会断 SSE，但**不要 cancel**；回前台用续传 GET（见续传文档） |
| 管理页 | 账户/分类/概览继续走 REST，**不要**用聊天 SSE 驱动表单 |

---

## 6. 与旧 WebSocket 对照

| 旧 WS | 新 SSE |
|-------|--------|
| `ws://host/ws?token=` | `POST /api/chat` + Bearer |
| 长连接 `connected` | 每轮请求独立；首包 `started` |
| 同连接多轮 | 每轮新 HTTP；同用户勿并发（409） |
| `message_delta` / `message_end` / `error` | 事件名与 JSON 字段基本沿用 |

旧客户端需迁移；后端 `/ws` 已移除。

---

## 7. 联调检查清单

- [ ] 登录拿到 token，`/api/auth/me` 200  
- [ ] `POST /api/chat` 能收到 `started` → 若干 `message_delta` → `message_end`  
- [ ] 空 content → 400  
- [ ] 错误 token → 401  
- [ ] 连发两轮未等结束 → 第二轮 409  
- [ ] 停止按钮走 cancel 后可再发一轮  
- [ ] 后台断线后续传见 `docs/ios-swift-sse-resume-handoff.md`  
- [ ] 公网仅测业务端口（如 6088），勿依赖 Grafana 端口  
