# EasyAccounts iOS (Swift) — SSE 对话断点续传对接

> 面向：Swift 客户端 Agent / iOS 开发  
> 后端：`easyaccount-agent`  
> 文档日期：2026-07-29  
> 状态：**已实现（中改）**  
> 关联：基础 SSE 见 `docs/ios-swift-sse-handoff.md`；账户 / 分类 / 概览 REST 见 `docs/ios-swift-handoff.md`

---

## 0. 你要解决的问题

用户在**打字机输出过程中**把 App 切到后台（URLSession SSE 断开），再回前台时：

1. 已展示的助手文字不丢  
2. 从断点继续收 `message_delta`，恢复打字机  
3. 最终仍收到一次 `message_end` 或 `error`

**不要**在进后台时调用 cancel；只有用户点「停止」才 cancel。

---

## 1. 环境与鉴权

| 环境 | Base URL |
|------|----------|
| 本机 | `http://127.0.0.1:8088` |
| 公网 | `http://118.25.46.207:6088` |

```http
Authorization: Bearer <token>
```

- JSON 请求：`Content-Type: application/json`
- SSE：`Accept: text/event-stream`
- **禁止** `?token=`；错误体多为 `{ "message": "..." }`（无统一 code 信封）

---

## 2. 接口一览

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat` | 开始新一轮（兼容扩展，响应带 `streamId`/`eventId`） |
| `GET` | `/api/chat/streams/{streamId}?afterEventId=` | 断点续传 SSE |
| `POST` | `/api/chat/streams/{streamId}/cancel` | 显式取消本轮 |
| `GET` | `/api/chat/streams/{streamId}/status` | 可选：查流状态 JSON |

---

## 3. 本地状态（建议持久化）

对**未完成**的助手气泡保存：

| 字段 | 说明 |
|------|------|
| `streamId` | 来自 `started`（及后续事件） |
| `lastEventId` | 已成功处理的最大 `eventId` |
| `assistantText` | 已拼接的增量文本 |
| `status` | 如 `streaming` / `completed` / `failed` |

收到 `started` 后立刻写入 `streamId`；每处理一条带 `eventId` 的事件就更新 `lastEventId`。

---

## 4. `POST /api/chat`（开流）

### 4.1 请求

```http
POST /api/chat
Authorization: Bearer <token>
Content-Type: application/json
Accept: text/event-stream
```

```json
{ "content": "今天午饭花了 35 元，记到微信" }
```

客户端**不必**传 `streamId`。

### 4.2 SSE 事件（均含 `streamId` + `eventId`）

帧上建议有标准 `id:`（等于 `eventId`），便于通用解析：

```text
id: 1
event: started
data: {"type":"started","content":"ok","streamId":"s-01HZX...","eventId":1}

id: 2
event: message_delta
data: {"type":"message_delta","content":"已","streamId":"s-01HZX...","eventId":2}

id: 10
event: message_end
data: {"type":"message_end","content":"已记账：午餐 35.00 元…","streamId":"s-01HZX...","eventId":10}
```

| event | 必填字段 | 说明 |
|-------|----------|------|
| `started` | `type`, `content`, `streamId`, `eventId` | `content` 固定 `"ok"` |
| `message_delta` | `type`, `content`, `streamId`, `eventId` | 增量文本，按序拼接 |
| `message_end` | `type`, `content`, `streamId`, `eventId` | 完整回复 |
| `error` | `type`, `message`, `streamId`, `eventId` | 本轮失败；HTTP 可能仍为 200 |

旧字段仍在；多出来的 `streamId`/`eventId` 旧逻辑可忽略。

### 4.3 非流式 HTTP 错误

| HTTP | body | 场景 |
|------|------|------|
| 400 | `{ "message":"消息不能为空" }` | content 空 |
| 401 | `{ "message":"未登录或会话已失效" }` | token 无效 |
| 409 | 见下 | 已有**其它** running 流时又发新 `POST` |

409 示例（可先续传旧流）：

```json
{
  "message": "上一条消息仍在处理中",
  "streamId": "s-01HZX...",
  "lastEventId": 12,
  "status": "running"
}
```

**注意：** 对同一 `streamId` 的续传 **不会** 409。

---

## 5. `GET /api/chat/streams/{streamId}`（续传）

### 5.1 请求

```http
GET /api/chat/streams/{streamId}?afterEventId=12
Authorization: Bearer <token>
Accept: text/event-stream
```

| 参数 | 位置 | 必填 | 说明 |
|------|------|------|------|
| `streamId` | path | 是 | `started` 下发的 ID |
| `afterEventId` | query | 否 | 本地已处理的最大 `eventId`；默认 `0` |
| `Last-Event-ID` | header | 否 | 与 query 二选一；**都传时以 query 为准** |

### 5.2 行为

- 先补推 `eventId > afterEventId` 的已缓冲事件，再挂 live（若仍 `running`）
- **不再要求**发 `started`；可能先收到可选事件 `resume`（可忽略）
- 事件类型仍为 `message_delta` / `message_end` / `error`

| 流状态 | 客户端预期 |
|--------|------------|
| `running` | 补齐后继续增量，直到 `message_end` / `error` |
| `completed` | 补齐缺失 delta + `message_end`，连接结束 |
| `failed` | 收到 `error` |
| `cancelled` | `error.message == "已取消"` |

### 5.3 错误

| HTTP | body |
|------|------|
| 401 | `{ "message":"未登录或会话已失效" }` |
| 403 | `{ "message":"无权访问该流" }` |
| 404 | `{ "message":"流不存在或已过期" }` |
| 400 | `{ "message":"afterEventId 非法" }` |

流约保留 **30 分钟**；404 时：气泡定稿为已有文本，toast「回复已结束或过期」。

---

## 6. `POST .../cancel`（仅用户停止）

```http
POST /api/chat/streams/{streamId}/cancel
Authorization: Bearer <token>
```

200：

```json
{ "streamId": "s-01HZX...", "status": "cancelled" }
```

- App **进后台不要调** cancel，只断开 URLSession 即可  
- 取消后服务端会推 `error`（`已取消`）并释放 busy，之后可再发新消息

---

## 7. 可选：`GET .../status`

```http
GET /api/chat/streams/{streamId}/status
Authorization: Bearer <token>
```

```json
{
  "streamId": "s-01HZX...",
  "status": "running",
  "lastEventId": 18,
  "contentLength": 256,
  "expireAt": "2026-07-29T06:00:00+08:00"
}
```

---

## 8. 推荐 Swift 流程

### 8.1 发送

1. `POST /api/chat`，长超时（≈300s）  
2. 解析 SSE：遇 `started` → 持久化 `streamId`，气泡标 `streaming`  
3. `message_delta` → 追加 UI + `assistantText`，更新 `lastEventId`  
4. `message_end` / `error` → 定稿，清「未完成」标记  

### 8.2 进后台

- `task.cancel()` 或让连接自然断即可  
- **保留**本地 `streamId` / `lastEventId` / `assistantText`  
- **不要**调 cancel  

### 8.3 回前台

若存在 `status == streaming` 的气泡：

```http
GET /api/chat/streams/{streamId}?afterEventId={lastEventId}
```

继续拼 delta，直到 end/error。  
若 404 → 定稿已有文本并提示过期。

### 8.4 409 处理

新发送收到 409 且 body 含 `streamId` → 先按该 `streamId` + 本地/`lastEventId` 续传，完成后再允许新发送。

### 8.5 模型示意

```swift
struct ChatServerEvent: Decodable {
    let type: String
    let content: String?
    let message: String?
    let streamId: String?
    let eventId: Int64?
    // resume 可选
    let afterEventId: Int64?
    let serverLastEventId: Int64?
    let status: String?
}

struct ChatBusyError: Decodable {
    let message: String
    let streamId: String?
    let lastEventId: Int64?
    let status: String?
}

struct StreamingBubbleState: Codable {
    var streamId: String
    var lastEventId: Int64
    var assistantText: String
    var status: String // streaming | completed | failed
}
```

解析时：优先用 SSE 的 `event:`；校验 `data.type`；用 `id:` 或 `eventId` 更新游标。未知事件名（如 `resume`）直接忽略。

### 8.6 续传请求骨架

```swift
func resumeChat(streamId: String, afterEventId: Int64, token: String) async throws {
    var comps = URLComponents(string: "\(baseURL)/api/chat/streams/\(streamId)")!
    comps.queryItems = [URLQueryItem(name: "afterEventId", value: String(afterEventId))]
    var req = URLRequest(url: comps.url!)
    req.httpMethod = "GET"
    req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
    req.timeoutInterval = 300

    let (bytes, response) = try await URLSession.shared.bytes(for: req)
    guard let http = response as? HTTPURLResponse else { return }
    if http.statusCode == 404 {
        // 定稿本地文本 + toast
        return
    }
    guard http.statusCode == 200 else {
        // 读 JSON message
        return
    }
    // 与 POST 相同的 SSE 行解析；忽略 resume；更新 lastEventId / 文本
}
```

---

## 9. curl 自测

```bash
TOKEN=xxx

# 开流（记下 streamId / eventId）
curl -N -X POST "http://127.0.0.1:8088/api/chat" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"content":"查看我的账户"}'

# 模拟断线后续传
STREAM_ID=s-...
AFTER=5
curl -N "http://127.0.0.1:8088/api/chat/streams/${STREAM_ID}?afterEventId=${AFTER}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream"

# 取消（仅停止按钮）
curl -X POST "http://127.0.0.1:8088/api/chat/streams/${STREAM_ID}/cancel" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 10. 联调检查清单（iOS）

- [ ] `started` 后持久化 `streamId`  
- [ ] 每条事件更新 `lastEventId` 并拼接文本  
- [ ] 进后台只断连，不调 cancel  
- [ ] 回前台 `GET` 续传能接着打字机  
- [ ] 结束后再续传仍能收到补齐 + `message_end`  
- [ ] 续传 404 有友好提示  
- [ ] 新 `POST` 在 busy 时 409，且能用返回的 `streamId` 续传  
- [ ] 停止按钮走 cancel；停止后可发下一轮  
- [ ] 忽略未知事件 `resume` 不崩溃  

---

## 11. 与旧 SSE 文档差异

| 项 | 旧行为 | 现行为 |
|----|--------|--------|
| 事件字段 | 无 streamId/eventId | 均带 streamId + eventId；帧可有 `id:` |
| 进后台 | 结果丢失，需重发 | 生成继续，回前台 GET 续传 |
| 停止 | 仅断连 | 断连≠取消；停止按钮调 cancel |
| 并发 | 409 | 新 POST 仍 409；续传不 409 |
