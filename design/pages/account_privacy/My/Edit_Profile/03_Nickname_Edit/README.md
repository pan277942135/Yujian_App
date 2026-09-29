# 03 · 昵称编辑

Status: **FROZEN**

## Frozen Authority

### Visual Authority
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp`

SHA-256:
`29bf3a189eaaba5bada3efba0a999a83ab07e7a348300383c99b124dbdd5fdec`

Canvas:
`1491 × 1055`

### Development / Behavior Authority
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/Nickname_Edit_V1_Development_Contract.md`

### Frozen Manifest
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/manifest.json`

## 本页冻结范围

- Default
- Focus
- Filled
- Error
- 1 字符最小合法边界
- 20 字符最大合法边界
- 空 / 仅空格非法
- 超过 20 字符非法
- 字符计数
- Clear Action
- Save eligibility
- 保存后昵称同步
- 失败保留 Draft + Retry

## 核心业务规则

后端真实合同是：

> **trim 后昵称长度必须为 1–20 个 Unicode 字符；空 / 仅空格非法；超过 20 非法。**

特别说明：

- **1 个字符合法**；
- 当前后端没有额外字符类型限制；
- 不得自行增加“至少 2 个字符”“至少 3 个字符”或字符黑名单；
- 服务器返回结果是最终 Profile Authority。

## 保存语义

昵称编辑只产生 Local Draft。

`保存修改` 前：
- 不更新 UserSession；
- 不更新 My / Home；
- 不做乐观持久化。

服务器成功后：
- 使用 server response 更新 Profile / Session；
- My / Home 同步；
- 无其他 Pending Change 时 Save 回到 Disabled。

失败：
- 保留用户输入；
- 不覆盖服务器昵称；
- 允许直接重试。

## 公共组件依赖

- Background System V1 / BG_CONTENT
- Top Navigation V1 / BACK_TITLE
- Action Button V1
- Icon Action V1 / Clear
- Color & Typography V1
- Spacing & Radius V1

## Authority precedence

1. Backend API contract：业务校验最终权威；
2. `Nickname_Edit_V1_Development_Contract.md`：客户端行为 / 开发权威；
3. `Nickname_Edit_V1_Frozen.webp`：视觉状态与布局权威。

较早生成、包含“2–20 字符”描述的草稿图已废弃，不得作为开发依据。

## Runtime

Design = **FROZEN**

Runtime = **待后续按 Frozen Contract 收口与 Evidence Gate 验证**
