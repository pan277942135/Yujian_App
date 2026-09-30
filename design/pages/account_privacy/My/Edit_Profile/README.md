# 编辑资料 · Design Authority Index V2

Status: **PARTIAL**

本目录是编辑资料的 authority index。页面冻结状态逐项计算，不因单页冻结而把整个 Edit Profile package 标为 FROZEN。

## 02 · 我的与资料菜单

- `02B · 编辑资料首页` — **FROZEN**; `01_Profile_Home/README.md` and its frozen image.
- `02C · 头像修改` — **FROZEN**; `02_Avatar_Edit/README.md` and `Avatar_Change_V1_Development_Contract.md`.
- `02D · 昵称编辑` — **BLOCKED_INTEGRITY** for Visual Authority. Behavior / machine / development contracts remain frozen, but the repository WebP fingerprint does not match the declared frozen fingerprint.
- `04_Save_Feedback/README.md`, `05_Edge_States/README.md`, and `06_Interaction_Adaptation/README.md` remain preserved for history and no longer create independent Design Manager menus.

## Consolidated State / Interaction Contract

### 01 · Edit Profile Home

The current frozen home covers avatar, read-only account, nickname, and the default Save state. Save is Disabled until an edit makes the form dirty. Page layout and defaults remain governed by `01_Profile_Home/README.md`.

### 02 · Avatar Change

The current frozen contract covers source selection, camera / Photo Picker, square crop and zoom, pending preview, upload during the unified profile save, and success/failure/retry. The exact behavior remains in `02_Avatar_Edit/Avatar_Change_V1_Development_Contract.md`.

### 03 · Nickname Edit — BLOCKED_INTEGRITY

The behavior / machine / development contract remains frozen and covers Default, Focus, Filled, error and length-boundary states. Trimmed nickname length is 1–20 characters. The development and machine contracts remain authoritative.

**Blocking visual integrity gate:** the repository WebP SHA-256 is `26abab813e915fed11c34e1c9febf2eac96d3876e6fa945ccb22372b8d62079b`, while the existing frozen manifest and README record `5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`. The file is preserved unchanged. Until the originally approved byte-identical source is recovered, the visual must not be presented as FROZEN or parity PASS. Do not regenerate, optimize, convert, or re-encode it.

### 04 · Save Feedback — PARTIAL

The existing structure-only file lists Unchanged, Dirty, Saving, Success, Failure and Retry. It does not freeze the feedback surface, error copy, or retry rules. Do not infer unresolved behavior.

### 05 · Edge States — PARTIAL

The existing structure-only file lists avatar read failure, image format/size errors, network failure, invalid nickname, expired session and service error. Priority, recovery behavior and draft-retention rules remain unresolved.

### 06 · Interaction / Adaptation — PARTIAL

The existing structure-only file lists keyboard / IME, scrolling, small screens, Back, unsaved changes, Safe Area and Accessibility. Exact behavior, breakpoints and accessibility parameters remain unresolved.

These three partial areas stay inside this authority index and do not become separate navigation entries. Their source READMEs are retained unchanged for historical traceability.

## Runtime Boundary

Existing runtime/API behavior does not make a design state Frozen. This index does not claim Android runtime parity or completion.
