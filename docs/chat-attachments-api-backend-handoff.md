# EasyAccounts — 聊天附件（图片）接口说明（后端实现稿）

面向：后端 Agent / easyaccount-agent  
客户端：EasyAccount iOS (Swift)  
文档日期：2026-08-06  

实现状态：后端已按本文落地 API 层（上传 / 开聊携带 `attachmentIds` / GET·DELETE）。  
媒体访问（缩略图 / 原图 `/content`、长期保留）见：`docs/chat-attachments-media-access-backend-handoff.md`。

## 推荐调用顺序

1. `POST /api/chat/attachments`（multipart 字段名 `file`）→ `attachmentId`
2. `POST /api/chat` JSON `{ content, attachmentIds }` → SSE 如常

不要用 multipart 直接打在 `POST /api/chat` 上再开 SSE。

## 已实现端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/attachments` | 上传单个图片附件（响应含 `url` / `thumbnailUrl`） |
| GET | `/api/chat/attachments/{id}/content` | 按 `variant` 返回缩略图 / 原图字节 |
| GET | `/api/chat/attachments/{id}` | 查询元数据（含可读 URL） |
| DELETE | `/api/chat/attachments/{id}` | 删除未引用附件 |
| POST | `/api/chat` | 扩展 `attachmentIds`；SSE 协议不变；引用后长期保留 |

## 业务接入

开聊时校验附件归属与过期后进入 SSE；在 `started` 之后对每张图调用 `BillImageParseService`，将结构化结果拼入 Agent 文本输入，并标记附件已引用（延长 `expires_at`）。

**确认后再记账：** 带附件的当轮会附带「禁止写入、先请用户确认」指令；Agent 只展示待确认清单。用户下一条明确确认后，再调用写入工具落库。

配置见 `easyaccount.attachments.*`、`easyaccount.bill-parse.*`。
