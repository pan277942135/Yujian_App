# 03 · 昵称编辑

Status: **BLOCKED_INTEGRITY — Behavior / Machine / Development Contract FROZEN; Visual Binary Identity Unresolved**

## Governance status

- Behavior / Machine / Development contracts: **FROZEN**
- Intended approved Visual Authority identity: **FROZEN fingerprint recorded**
- Current repository visual binary: **BLOCKED_INTEGRITY**
- Overall Design Manager child status: **BLOCKED_INTEGRITY**

Current repository SHA-256:
`26abab813e915fed11c34e1c9febf2eac96d3876e6fa945ccb22372b8d62079b`

Declared approved/frozen SHA-256:
`5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`

These do not match. Recover the originally approved byte-identical source before restoring Visual FROZEN. Do **not** regenerate, convert, optimize, or re-encode a substitute.

## Frozen Contract Authorities

### Visual Authority
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/Nickname_Edit_V1_Frozen.webp`

Visual SHA-256:
`5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`

Approved source:
`昵称编辑流程与交互规范.png`

Approved source SHA-256:
`5d96c4654abe9899042f29f33987bc49eb21d4b1c427963bda1a0a3fef23d55e`

Canvas:
`1491 × 1055`

> Visual Authority 已恢复为产品确认的第一张定稿流程图。后续曾重新生成的“修正版”不再属于任何 Authority。

### Development / Behavior Authority
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/Nickname_Edit_V1_Development_Contract.md`

### Machine Contract
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/contracts/Nickname_Edit_V1_Contract.json`

### Frozen Manifest
`design/pages/account_privacy/My/Edit_Profile/03_Nickname_Edit/frozen/manifest.json`

## 本页冻结范围

- Default
- Focus
- Filled
- Error
- 字数边界
- Clear Action
- Save eligibility
- 保存后状态
- 保存失败后 Draft 保留与 Retry

## 视觉与业务规则的 Authority 边界

第一张定稿图负责：

- 页面构图与信息层级；
- Default / Focus / Filled / Error / Max / Saved 的视觉关系；
- 字符计数的视觉位置；
- Save Disabled / Enabled / Saving / Success / Failure 的视觉语义；
- 与 Edit Profile 首页共享的头像、账号只读、昵称和按钮布局。

**图中的示意业务文字不覆盖后端合同。**

昵称真实业务校验以 Backend + Machine Contract + Development Contract 为准：

> **trim 后昵称长度为 1–20 个 Unicode 字符；空 / 仅空格非法；超过 20 非法。**

因此：

- 1 个字符合法；
- 20 个字符合法；
- 不得自行增加“至少 2 个字符”“至少 3 个字符”；
- 当前后端没有额外字符类型黑名单。

## 保存语义

昵称编辑只形成 Local Draft。

`保存修改` 前：

- 不更新 UserSession；
- 不更新 My / Home；
- 不做乐观持久化。

服务器成功后：

- 使用 server response 更新 Profile / Session；
- My / Home 同步昵称；
- 无其他 Pending Change 时 Save 回到 Disabled。

失败：

- 保留用户输入；
- 不覆盖服务器昵称；
- 允许直接重试。

## Authority precedence

1. Backend API Contract — 服务端业务规则最终权威；
2. Machine Contract — 枚举、边界、状态、保存条件的机器权威；
3. Development / Behavior Contract — 客户端实现与交互权威；
4. Visual Authority — 布局、视觉状态和信息层级权威。

若 Visual Authority 中的示意文字与 1–3 冲突，开发不得按图片文字覆盖合同。

## 公共组件依赖

- Background System V1 / BG_CONTENT
- Top Navigation V1 / BACK_TITLE
- Action Button V1
- Icon Action V1 / Clear
- Color & Typography V1
- Spacing & Radius V1

## Runtime

Design behavior/contracts = **FROZEN**

Visual integrity = **BLOCKED_INTEGRITY**

Runtime = **待后续按 Frozen Contract 收口与 Evidence Gate 验证**
