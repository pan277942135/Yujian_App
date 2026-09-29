# 编辑资料 · Design Package V1

Status: **PARTIAL — 01 FROZEN / 02–06 in design**

本目录是「编辑资料」的正式设计管理根目录。

## 二级目录

1. [01 · 编辑资料首页](./01_Profile_Home/README.md) — **FROZEN**
2. [02 · 头像修改](./02_Avatar_Edit/README.md) — PARTIAL
3. [03 · 昵称编辑](./03_Nickname_Edit/README.md) — PARTIAL
4. [04 · 保存与反馈](./04_Save_Feedback/README.md) — PARTIAL
5. [05 · 异常与边界状态](./05_Edge_States/README.md) — PARTIAL
6. [06 · 交互与适配规范](./06_Interaction_Adaptation/README.md) — PARTIAL

## Scope

编辑资料只负责个人资料编辑。账号安全、修改密码、数据与隐私继续归属各自模块。

当前资料边界：

- 头像
- 账号（只读）
- 昵称

未单独确认前，不新增手机号、邮箱、性别、生日、地区、签名等字段。

## Current Frozen Authority

`01 · 编辑资料首页` 已冻结：

- Visual: `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/frozen/Edit_Profile_Home_V1_1_Frozen.webp`
- Spec: `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/README.md`
- Manifest: `design/pages/account_privacy/My/Edit_Profile/01_Profile_Home/frozen/manifest.json`

02–06 不因 01 冻结而自动继承 FROZEN 状态。

## Top Navigation

- Default edit-profile navigation uses **Top Navigation V1 / BACK_TITLE / FROZEN**.
- Back → Icon Action V1 / NAVIGATION.
- Save / confirm remains page content unless a future frozen revision explicitly upgrades the page to BACK_TITLE_ACTIONS.
