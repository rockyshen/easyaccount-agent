# EasyAccounts iOS (Swift) 对接文档

> 面向：Swift 客户端 Agent / iOS 开发  
> 后端仓库：`easyaccount-agent`  
> 文档日期：2026-07-24  
> 状态：**账户管理 / 分类管理（只读）/ 概览分析 REST 已上线**（需部署含本能力的构建）  
> 侧栏对应：账户管理 · 分类管理 · 概览分析  

---

## 1. 环境

| 环境 | Base URL | WebSocket |
|------|----------|-----------|
| 本机 / Pi | `http://127.0.0.1:8088` | `ws://127.0.0.1:8088/ws?token=<token>` |
| 公网 | `http://118.25.46.207:6088` | `ws://118.25.46.207:6088/ws?token=<token>` |

- 时区业务约定：`Asia/Shanghai`
- 金额字段一律 **String**（两位小数，如 `"12.50"`），Swift 侧用 `Decimal`，不要用 `Double` 做业务运算
- 无统一 `{code,data,msg}` 信封：成功直接返回业务 JSON；失败多为 `{ "message": "..." }` + HTTP 状态码
- `Content-Type: application/json; charset=utf-8`

---

## 2. 鉴权（所有业务接口前置）

### 2.1 Header

```http
Authorization: Bearer <token>
Content-Type: application/json
```

规则：

- Token TTL 默认 30 天；临近过期 7 天内访问会滑动续期
- **单端登录**：同一用户再次登录会使旧 token 全部失效
- 业务接口（accounts / actions / types / dashboard）缺 token 或无效 → `401` `{ "message": "未登录或会话已失效" }`
- Keychain 建议存：`token`、`expiresAt`、`user.id`、`user.name`

### 2.2 注册

`POST /api/auth/register`

```json
{ "name": "rocky", "password": "P@ssw0rd!" }
```

成功 `200`：

```json
{
  "token": "opaque-token-string",
  "expiresAt": "2026-08-23T12:00:00+08:00",
  "user": { "id": 1, "name": "rocky" }
}
```

失败：`400` / `409` → `{ "message": "..." }`（如用户名已存在）

### 2.3 登录

`POST /api/auth/login`  
Body 同注册。失败 `401` → `{ "message": "用户名或密码错误" }`。

### 2.4 免登录检查

`GET /api/auth/me`  
Header: `Authorization: Bearer <token>`

成功 `200`：

```json
{ "id": 1, "name": "rocky" }
```

失败 `401`：`{ "message": "未登录或会话已失效" }`

### 2.5 登出

`POST /api/auth/logout`  
Header: `Authorization: Bearer <token>`（可缺，服务端仍返回成功）

成功：`{ "ok": true }`

### 2.6 App 启动流程（建议）

1. 读 Keychain token → `GET /api/auth/me`
2. `200` → 进主页；可同时连 `WS /ws?token=`
3. `401` → 清 Keychain → 登录页
4. 任意业务接口 `401` → 同登出，提示「登录已失效 / 已在其他设备登录」

---

## 3. 账户管理 ` /api/accounts `

侧栏「账户管理」。需 Bearer。数据按当前登录用户隔离，**不要**传 `userId`。

### 3.1 领域约定

| accountType | 含义 | money | exemptMoney | usedMoney |
|-------------|------|-------|-------------|-----------|
| `0` | 普通/储蓄 | 余额 | 豁免资产 | 一般为 `null` |
| `1` | 信用卡 | 可用额度 | 信用额度 | 已用 = 信用 − 可用 |

- 删除为**软删除**，列表不再返回
- 创建信用卡时：`initialMoney` = **信用额度**（必须 > 0）
- 修改信用卡额度：传 `exemptMoney` = 新信用额度；新额度不得小于已用
- 余额/可用额度主要由记账流水变更，**update 不直接改 money**

### 3.2 列表

`GET /api/accounts`

成功 `200`：数组

```json
[
  {
    "id": 1,
    "name": "招商储蓄",
    "money": "1200.00",
    "exemptMoney": "0.00",
    "card": "尾号1234",
    "createTime": "2026-07-01 10:00:00",
    "note": "",
    "accountType": 0,
    "typeLabel": "普通",
    "usedMoney": null
  },
  {
    "id": 2,
    "name": "招行信用卡",
    "money": "8000.00",
    "exemptMoney": "10000.00",
    "card": "",
    "createTime": "2026-07-02 11:00:00",
    "note": "",
    "accountType": 1,
    "typeLabel": "信用卡",
    "usedMoney": "2000.00"
  }
]
```

### 3.3 创建

`POST /api/accounts`

```json
{
  "name": "现金",
  "initialMoney": "100.00",
  "card": "",
  "note": "",
  "accountType": 0
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | ✅ | 账户名 |
| initialMoney | string | 建议 | 普通=初始余额；信用卡=信用额度 |
| card | string | ❌ | 卡号备注 |
| note | string | ❌ | 备注 |
| accountType | int | ✅ | `0` / `1`，缺省 → `400` |

成功 `200`：单个账户对象（同列表元素）。  
失败 `400`：`{ "message": "..." }`

### 3.4 更新

`PUT /api/accounts/{id}`

```json
{
  "name": "招商储蓄",
  "card": "尾号5678",
  "note": "日常",
  "exemptMoney": "200.00"
}
```

| 字段 | 说明 |
|------|------|
| name | 非空字符串才更新 |
| card / note | 传入即更新（可传 `""`） |
| exemptMoney | 普通=豁免；信用卡=新信用额度 |

成功 `200`：更新后的账户对象。  
失败 `400`：不存在 / 已停用 / 额度非法等。

### 3.5 删除

`DELETE /api/accounts/{id}`

成功 `200`：`{ "ok": true }`  
失败 `400`：`{ "message": "..." }`

### 3.6 Swift 模型

```swift
struct AccountDTO: Codable, Identifiable, Sendable {
    let id: Int
    let name: String
    let money: String
    let exemptMoney: String
    let card: String?
    let createTime: String?
    let note: String?
    let accountType: Int
    let typeLabel: String?
    let usedMoney: String?
}

struct CreateAccountRequest: Codable, Sendable {
    let name: String
    let initialMoney: String
    let card: String?
    let note: String?
    let accountType: Int
}

struct UpdateAccountRequest: Codable, Sendable {
    let name: String?
    let card: String?
    let note: String?
    let exemptMoney: String?
}

struct ApiMessage: Codable, Sendable {
    let message: String
}

struct OkResponse: Codable, Sendable {
    let ok: Bool
}
```

UI 建议：

- 普通：主数字 `money`；副文案豁免 `exemptMoney`（>0 时）
- 信用卡：主数字 `money`（可用）；副文案「已用 `usedMoney` / 额度 `exemptMoney`」

---

## 4. 分类管理（只读）

侧栏「分类管理」。需 Bearer。  
`action` / `type` 为**全局共享**数据；**当前无增删改接口**，一期做成只读浏览即可。

### 4.1 收支类型

`GET /api/actions`

成功 `200`：

```json
[
  { "id": 1, "hName": "收入", "exempt": false, "handle": 0 },
  { "id": 2, "hName": "支出", "exempt": false, "handle": 1 },
  { "id": 3, "hName": "转账", "exempt": false, "handle": 2 }
]
```

| handle | 含义 |
|--------|------|
| `0` | 收入 |
| `1` | 支出 |
| `2` | 转账/内部 |

### 4.2 分类树

`GET /api/types?actionId={actionId}`

- `actionId` **必填**，缺省 → `400` `{ "message": "actionId 不能为空" }`
- 父节点 `parent == -1`；子节点在 `childrenTypes`

成功 `200`：

```json
[
  {
    "id": 10,
    "tName": "餐饮",
    "parent": -1,
    "childrenTypes": [
      { "id": 11, "tName": "午餐", "parent": 10, "childrenTypes": null },
      { "id": 12, "tName": "咖啡", "parent": 10, "childrenTypes": null }
    ]
  }
]
```

### 4.3 Swift 模型

```swift
struct ActionDTO: Codable, Identifiable, Sendable {
    let id: Int
    let hName: String
    let exempt: Bool
    let handle: Int
}

struct TypeNodeDTO: Codable, Identifiable, Sendable {
    let id: Int
    let tName: String
    let parent: Int?
    let childrenTypes: [TypeNodeDTO]?
}
```

页面流程建议：先拉 `/api/actions` → 用户点选某一 `action` → 再拉 `/api/types?actionId=` 展示树。

---

## 5. 概览分析 `/api/dashboard`

侧栏「概览分析」。需 Bearer。

### 5.1 获取概览

`GET /api/dashboard`

成功 `200`：

```json
{
  "totalAsset": "1200.00",
  "netAsset": "1000.00",
  "curIncome": null,
  "curOutCome": null,
  "yearIncome": "5000.00",
  "yearOutCome": "3200.00",
  "yearBalance": "1800.00",
  "accounts": [
    {
      "id": 1,
      "accountName": "招商储蓄",
      "accountAsset": "1200.00",
      "exemptAsset": "0.00",
      "percent": "100",
      "note": ""
    },
    {
      "id": 2,
      "accountName": "招行信用卡(信用卡)",
      "accountAsset": "8000.00",
      "exemptAsset": "2000.00",
      "percent": "0",
      "note": ""
    }
  ],
  "monthDetails": null
}
```

### 5.2 字段语义

| 字段 | 含义 |
|------|------|
| totalAsset | 普通账户余额合计（不含信用卡可用额度） |
| netAsset | 总资产减去豁免与信用卡已用 |
| yearIncome / yearOutCome / yearBalance | **当前自然年**收入 / 支出 / 结余 |
| accounts[].accountAsset | 普通=余额；信用卡=可用额度 |
| accounts[].exemptAsset | 普通=豁免；信用卡=**已用额度** |
| accounts[].percent | 占 totalAsset 比例字符串（可能无 `%`）；信用卡常为 `"0"` |
| accounts[].accountName | 信用卡名可能带 `"(信用卡)"` 后缀 |
| curIncome / curOutCome / monthDetails | 字段存在，**当前后端未填充**（多为 `null`），UI 勿依赖 |

### 5.3 Swift 模型

```swift
struct DashboardDTO: Codable, Sendable {
    let totalAsset: String
    let netAsset: String
    let curIncome: String?
    let curOutCome: String?
    let yearIncome: String?
    let yearOutCome: String?
    let yearBalance: String?
    let accounts: [DashboardAccountDTO]?
    let monthDetails: [DashboardMonthDTO]?
}

struct DashboardAccountDTO: Codable, Identifiable, Sendable {
    let id: Int
    let accountName: String
    let accountAsset: String
    let exemptAsset: String?
    let percent: String?
    let note: String?
}

struct DashboardMonthDTO: Codable, Sendable {
    let month: String?
    let income: String?
    let outcome: String?
    let balance: String?
}
```

UI：顶部总资产 / 净资产 + 年度收入支出结余；下方账户列表（可用 `percent` 画条形/简单占比）。

---

## 6. 错误与网络约定

| HTTP | Body | Swift 处理 |
|------|------|------------|
| 200 | 业务 JSON | 解码对应 DTO |
| 400 | `{ "message": "..." }` | Alert / Toast 展示 `message` |
| 401 | `{ "message": "..." }` | 清 token，回登录 |
| 409 | `{ "message": "..." }` | 注册用户名冲突 |
| 5xx | 可能有 message | 通用错误文案 |

`URLSession` 建议统一：

1. 附加 `Authorization`（除 login/register）
2. 非 2xx 先尝试解 `ApiMessage`
3. 金额展示用 `NumberFormatter` + `Decimal`

---

## 7. 实现清单（给 Swift Agent）

按侧栏三项实现原生页（**不要**用 WebSocket 聊天驱动这些管理页）：

1. **鉴权层**：login / register / me / logout + Keychain  
2. **账户管理页**：`GET/POST/PUT/DELETE /api/accounts`  
3. **分类管理页**（只读）：`GET /api/actions` + `GET /api/types?actionId=`  
4. **概览分析页**：`GET /api/dashboard`  
5. （可选，独立）聊天助手：`WS /ws?token=` — 与上述三页解耦  

联调顺序建议：公网 Base URL → login → me → dashboard → accounts → actions/types。

### 7.1 curl 冒烟（部署后）

```bash
BASE=http://118.25.46.207:6088

# 登录
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"name":"rocky","password":"YOUR_PASSWORD"}' | jq -r .token)

# 概览
curl -s "$BASE/api/dashboard" -H "Authorization: Bearer $TOKEN" | jq .

# 账户
curl -s "$BASE/api/accounts" -H "Authorization: Bearer $TOKEN" | jq .

# 分类
curl -s "$BASE/api/actions" -H "Authorization: Bearer $TOKEN" | jq .
curl -s "$BASE/api/types?actionId=2" -H "Authorization: Bearer $TOKEN" | jq .
```

---

## 8. 接口速查

| Method | Path | Auth | 说明 |
|--------|------|------|------|
| GET | `/health` | 无 | 探活 |
| POST | `/api/auth/register` | 无 | 注册并登录 |
| POST | `/api/auth/login` | 无 | 登录 |
| POST | `/api/auth/logout` | Bearer 可选 | 登出 |
| GET | `/api/auth/me` | Bearer | 免登录校验 |
| GET | `/api/accounts` | Bearer | 账户列表 |
| POST | `/api/accounts` | Bearer | 创建账户 |
| PUT | `/api/accounts/{id}` | Bearer | 更新账户 |
| DELETE | `/api/accounts/{id}` | Bearer | 软删账户 |
| GET | `/api/actions` | Bearer | 收支类型（只读） |
| GET | `/api/types?actionId=` | Bearer | 分类树（只读） |
| GET | `/api/dashboard` | Bearer | 概览分析 |
| WS | `/ws?token=` | token | 聊天助手（非本三页必需） |

---

## 9. 非目标（本期不做）

- 分类 / 收支类型的新增、改名、删除  
- 仪表盘月度明细 `monthDetails`（后端未填充）  
- 用自然语言 WebSocket 替代上述 REST 管理页  

后端实现参考 PR：账户/分类/概览 REST（`/api/accounts` · `/api/actions` · `/api/types` · `/api/dashboard`）。
