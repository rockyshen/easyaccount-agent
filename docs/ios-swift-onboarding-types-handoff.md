# EasyAccounts iOS：首次引导 & 分类按用户隔离 — 对接说明

> 面向：Swift / iOS 客户端  
> 后端仓库：`easyaccount-agent`  
> 文档日期：2026-08-06  
> 关联：[`ios-swift-handoff.md`](./ios-swift-handoff.md)（账户 / 分类 / 概览完整 API）  
> 状态：**已合入 `master`**

---

## 1. 本次后端变化（一句话）

| 项 | 变化 |
|----|------|
| `action`（收入/支出/转账） | **仍全局共用**，接口不变 |
| `type`（分类） | **按登录用户隔离**；注册时克隆一份预设分类给你 |
| 默认账户 | **不再自动创建**；由聊天里的 Agent 对话引导用户建账户 |
| 鉴权响应 | register / login / `/me` **新增** `onboarding` 字段（旧客户端忽略即可） |

**结论：协议大体向后兼容；要做好新用户体验，建议 iOS 接 `onboarding` 并处理「无账户」空态。**

---

## 2. iOS 必做 / 建议 / 可不动

### 2.1 建议做（体验）

1. 解析 `onboarding`（见 §3）
2. 新用户账户列表为空时，**不要当成错误**；引导去聊天或侧栏「新建账户」
3. 启动后若 `needsOnboarding == true`，可在聊天页给一句轻提示（非强制全屏向导）
4. 分类管理页文案可改为「我的分类」（改删只影响当前用户）

### 2.2 可以不做（仍能跑）

- 不解析 `onboarding`：忽略未知 JSON 字段即可登录、进主页
- 不改 SSE 聊天协议：仍 `POST /api/chat`
- 分类 CRUD 路径与 body **不变**，不必为隔离传 `userId`

### 2.3 不要做

- 请求里传 `userId`（服务端从 Bearer 取当前用户）
- 假定注册后一定有「现金」等默认账户
- 把 `action` 和 `type` 都当成全局可随便删（`action` 全局只读；`type` 是个人的）

---

## 3. 新增：`onboarding` 字段

出现在：

- `POST /api/auth/register` 成功响应
- `POST /api/auth/login` 成功响应
- `GET /api/auth/me` 成功响应

### 3.1 形状

```json
{
  "onboarding": {
    "needsOnboarding": true,
    "hasAccounts": false,
    "hasTypes": true,
    "typesSeeded": true
  }
}
```

| 字段 | 类型 | 含义 |
|------|------|------|
| `needsOnboarding` | Bool | **当前没有活跃账户** 时为 `true`（需建账户才能正常记账） |
| `hasAccounts` | Bool | 是否有未停用账户 |
| `hasTypes` | Bool | 是否有未停用个人分类 |
| `typesSeeded` | Bool | 当前与 `hasTypes` 同义（注册/登录会幂等补种预设分类） |

### 3.2 完整响应示例

**注册 / 登录 `200`：**

```json
{
  "token": "opaque-token-string",
  "expiresAt": "2026-08-23T12:00:00+08:00",
  "user": { "id": 1, "name": "rocky" },
  "onboarding": {
    "needsOnboarding": true,
    "hasAccounts": false,
    "hasTypes": true,
    "typesSeeded": true
  }
}
```

**`/me` `200`：**

```json
{
  "id": 1,
  "name": "rocky",
  "onboarding": {
    "needsOnboarding": false,
    "hasAccounts": true,
    "hasTypes": true,
    "typesSeeded": true
  }
}
```

### 3.3 Swift 模型建议

```swift
struct OnboardingDTO: Codable, Sendable {
    let needsOnboarding: Bool
    let hasAccounts: Bool
    let hasTypes: Bool
    let typesSeeded: Bool
}

struct AuthSessionDTO: Codable, Sendable {
    let token: String
    let expiresAt: String
    let user: UserDTO
    let onboarding: OnboardingDTO?   // 旧构建可能无此字段
}

struct MeDTO: Codable, Sendable {
    let id: Int
    let name: String
    let onboarding: OnboardingDTO?
}
```

> 将 `onboarding` 设为 **Optional**，兼容尚未部署新后端的环境。

---

## 4. 推荐启动 / 首次使用流程

```text
App 启动
  → Keychain 有 token？
       → GET /api/auth/me
            → 401：清 Keychain → 登录页
            → 200：进主页
                 → 若 onboarding?.needsOnboarding == true
                      → 聊天页轻提示：「先和助手聊聊，建个账户吧」
                 → 用户打开聊天 POST /api/chat
                      → Agent 对话引导建账户 → 确认分类 → 记账
  → 无 token：注册 / 登录页
       → register/login 存 token + onboarding
       → 同上
```

### 4.1 产品约定（与后端一致）

| 步骤 | 谁负责 |
|------|--------|
| 克隆预设分类 | **后端**（注册时；空分类用户登录时补种） |
| 创建第一个账户 | **Agent 对话** 或用户在侧栏 `POST /api/accounts` |
| 微调分类 | Agent 或侧栏分类管理 |
| 强制原生多步向导 | **不做**（本阶段以聊天引导为主） |

### 4.2 UI 文案示例（可选）

- 聊天置顶 / Toast：`先建一个账户才能记账，跟我说「建个微信，余额 200」也可以。`
- 账户列表空态：`还没有账户。去和助手聊聊，或点右上角新建。`
- 分类页副标题：`这些分类只属于你，可随意增删改。`

---

## 5. 分类：行为变化说明

### 5.1 不变的接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/actions` | 全局收入/支出/转账 |
| GET | `/api/types?actionId=` | **当前用户**的分类树 |
| POST | `/api/types` | 创建（写入当前用户） |
| PUT | `/api/types/{id}` | 更新（只能改自己的） |
| DELETE | `/api/types/{id}` | 软删（只能删自己的） |

请求/响应 JSON 字段（`hname` / `tname` / `childrenTypes` 等）与 [`ios-swift-handoff.md`](./ios-swift-handoff.md) §4 **相同**。

### 5.2 变了的语义

| 以前（旧认知） | 现在 |
|----------------|------|
| 所有用户共享一套 type | 每个用户各自一套 type |
| 用户 A 删「餐饮」会影响 B | **只影响 A** |
| 新用户可能看到库里已有全局分类 | 新用户看到的是**为自己克隆的预设树** |

### 5.3 预设分类（注册后大致会有）

**支出：** 餐饮（早餐/午餐/晚餐/咖啡/外卖）、交通、购物、居住、娱乐、医疗、其他支出  
**收入：** 工资、理财、其他收入  
**转账：** 账户互转、信用卡还款  

具体 id **不要写死**；以 `GET /api/types?actionId=` 返回为准。

### 5.4 错误体验

- 用别人的 `typeId` 记账 → 后端拒绝（「分类不存在或不属于当前用户」）
- 客户端只需展示 `message`，并提示刷新分类列表

---

## 6. 账户：空列表是正常态

新注册用户：

- `GET /api/accounts` → `[]`
- `onboarding.needsOnboarding` → `true`
- `onboarding.hasTypes` → 一般为 `true`（已克隆分类）

建账户两种方式（等价）：

1. **聊天**：跟 Agent 说「建个微信，余额 200」
2. **侧栏**：现有 `POST /api/accounts`（body 不变，见主 handoff §3）

建好后再次 `/me`：`needsOnboarding` 变为 `false`。

---

## 7. Agent / 聊天侧注意点

- SSE 协议不变，见 [`ios-swift-sse-handoff.md`](./ios-swift-sse-handoff.md)
- 无账户时用户直接说「午餐 35」，Agent 应**先引导建账户**而不是落账
- iOS **不必**调用新工具；工具由服务端 Agent 使用（含 `getOnboardingStatus`）
- 可选：注册后进聊天时发一句用户可见欢迎语，或由产品决定是否自动发「开始使用」（非必须）

---

## 8. 联调检查清单

- [ ] 新注册用户：`hasTypes == true`，`hasAccounts == false`，`needsOnboarding == true`
- [ ] `GET /api/accounts` 为空不崩溃，有空态文案
- [ ] `GET /api/types?actionId=` 能看到预设树（餐饮等）
- [ ] 用户 A 改/删分类后，用户 B 的分类不受影响（双账号验证）
- [ ] 聊天建账户成功后，`/me` 的 `needsOnboarding` 变为 `false`
- [ ] 侧栏建账户同样可使 `needsOnboarding` 变为 `false`
- [ ] 旧 App 忽略 `onboarding` 仍可登录、聊天、CRUD

---

## 9. 与主文档的关系

| 文档 | 用途 |
|------|------|
| **本文** | 本次「首次引导 + type 用户隔离」增量说明 |
| [`ios-swift-handoff.md`](./ios-swift-handoff.md) | 账户/分类/概览完整 REST |
| [`ios-swift-sse-handoff.md`](./ios-swift-sse-handoff.md) | 聊天 SSE |
| [`ios-swift-sse-resume-handoff.md`](./ios-swift-sse-resume-handoff.md) | SSE 断点续传 |

主 handoff 中鉴权与分类章节已同步本次语义；细节冲突时以**已部署后端实际响应**为准。

---

## 10. 环境

| 环境 | Base URL（与主文档一致） |
|------|--------------------------|
| 本机 / Pi | `http://127.0.0.1:8088` |
| 公网 | `http://118.25.46.207:6088` |

需部署包含本次能力的构建（`master` 已含）。确认方式：register 成功响应是否带 `onboarding` 对象。
