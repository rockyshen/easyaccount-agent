# EasyAccounts — 聊天附件媒体访问（缩略图 / 原图）后端交接稿

面向：后端 Agent / easyaccount-agent 开发  
客户端：EasyAccount iOS (Swift)  
文档日期：2026-08-06  
状态：**后端已实现**（`/content` + 长期保留 + 元数据 url/thumbnailUrl + 上传生成 thumb）  
关联：

- 既有上传与开聊：`docs/chat-attachments-api-backend-handoff.md`
- SSE：`docs/sse-stream-resume-api.md`（若存在）/ `docs/ios-swift-sse-resume-handoff.md`

## 实现摘要

| 能力 | 状态 | 说明 |
|------|------|------|
| `GET /api/chat/attachments/{id}/content?variant=` | ✅ P0 | 返回缩略图 / 原图字节；鉴权；跨用户 404 |
| 开聊引用后长期保留 | ✅ P0 | `markReferenced` 同时延长 `expires_at`（默认 365 天，可配） |
| `GET /api/chat/attachments/{id}` 元数据 | ✅ P1 | 含 `url` / `thumbnailUrl` / `thumbWidth` / `thumbHeight` |
| 上传响应扩展 | ✅ | 同上字段；服务端同步生成 JPEG thumb（最长边 256） |
| 未引用短 TTL GC | ✅ P2 | 定时任务清理 `referenced=0 AND expires_at < now` |

## 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/attachments` | 上传；响应含 `url` / `thumbnailUrl` |
| GET | `/api/chat/attachments/{id}/content` | `variant=thumbnail\|original`（默认 original） |
| GET | `/api/chat/attachments/{id}` | 元数据 |
| DELETE | `/api/chat/attachments/{id}` | 未引用可删；已引用 409 |

### Content 成功响应

- `Content-Type`: 实际上传 MIME（缩略图为 `image/jpeg`）
- Body: 原始字节（非 JSON）
- `Cache-Control: private, max-age=86400`

### 错误

| HTTP | 场景 |
|------|------|
| 400 | `variant` 非法 → `不支持的 variant` |
| 401 | 未登录 → `未授权` |
| 404 | 不存在 / 非本人 / 未引用已过期 → `附件不存在或已过期` |

## 生命周期

| 阶段 | 策略 |
|------|------|
| 已上传、未被 `/api/chat` 引用 | 短 TTL：`easyaccount.attachments.ttl-hours`（默认 24h） |
| 开聊成功引用 | `referenced=1`，`expires_at = now + referenced-retention-days`（默认 365） |
| GC | 仅清理未引用且过期；已引用不受短 TTL 影响 |

## 存储结构

```
{storage-dir}/u-{userId}/{attachmentId}/original.jpg
{storage-dir}/u-{userId}/{attachmentId}/thumb.jpg
```

`url` / `thumbnailUrl` 指向本服务 content 接口（需 Bearer）。可通过
`easyaccount.attachments.public-base-url` 拼绝对地址；为空时返回相对路径。

## 配置

```yaml
easyaccount.attachments:
  ttl-hours: 24
  referenced-retention-days: 365
  public-base-url: ""          # 例 http://118.25.46.207:6088
  thumb-max-edge: 256
  thumb-jpeg-quality: 0.75
  gc-interval-ms: 3600000
  storage-dir: ./data/chat-attachments
```

## 验收对照

- [x] `GET /content?variant=thumbnail` 返回小图字节
- [x] `GET /content?variant=original` 返回原图字节
- [x] 跨用户 id → 404
- [x] 上传后短时间内可开聊
- [x] 开聊引用后延长保留，历史 `/content` 仍可读
- [x] 上传响应带 `thumbnailUrl`（及 `url`）
- [x] 未登录 → 401（拦截器）
