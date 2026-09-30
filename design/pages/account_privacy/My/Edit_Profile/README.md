# 编辑资料 · Design Authority Index V2

Status: **PARTIAL — all design behavior/state contracts frozen; Nickname visual integrity remains blocked**

本目录是编辑资料的 authority index。页面冻结状态逐项计算，不因单页冻结而把整个 Edit Profile package 标为 FROZEN。

## 02 · 我的与资料菜单

- `02B · 编辑资料首页` — **FROZEN**; `01_Profile_Home/README.md` and its frozen image.
- `02C · 头像修改` — **FROZEN**; `02_Avatar_Edit/README.md` and `Avatar_Change_V1_Development_Contract.md`.
- `02D · 昵称编辑` — **BLOCKED_INTEGRITY** for Visual Authority. Behavior / machine / development contracts remain frozen, but the repository WebP fingerprint does not match the declared frozen fingerprint.
- `04_Save_Feedback/README.md`, `05_Edge_States/README.md`, and `06_Interaction_Adaptation/README.md` remain preserved for history and no longer create independent Design Manager menus.
- Their canonical frozen authority is `Edit_Profile_State_Interaction_Spec_V1.md`.

## Consolidated State / Interaction Contract

### 01 · Edit Profile Home

The current frozen home covers avatar, read-only account, nickname, and the default Save state. Save is Disabled until an edit makes the form dirty. Page layout and defaults remain governed by `01_Profile_Home/README.md`.

### 02 · Avatar Change

The current frozen contract covers source selection, camera / Photo Picker, square crop and zoom, pending preview, upload during the unified profile save, and success/failure/retry. The exact behavior remains in `02_Avatar_Edit/Avatar_Change_V1_Development_Contract.md`.

### 03 · Nickname Edit — BLOCKED_INTEGRITY

The behavior / machine / development contract remains frozen and covers Default, Focus, Filled, error and length-boundary states. Trimmed nickname length is 1–20 characters. The development and machine contracts remain authoritative.

**Blocking visual integrity gate:** the repository WebP SHA-256 is `26abab813e915fed11c34e1c9febf2eac96d3876e6fa945ccb22372b8d62079b`, while the existing frozen manifest and README record `5de45bba50734ec9cfac73fce50feebdf78f70a8f3a41789c3d923816f069ea3`. The file is preserved unchanged. Until the originally approved byte-identical source is recovered, the visual must not be presented as FROZEN or parity PASS. Do not regenerate, optimize, convert, or re-encode it.

### 04–06 · State / Edge / Interaction — FROZEN

Canonical authority:

`Edit_Profile_State_Interaction_Spec_V1.md`

This unified contract freezes:

- UNCHANGED / DIRTY_VALID / DIRTY_INVALID
- SAVING / SUCCESS / FAILURE_RETRYABLE / FAILURE_SESSION / PARTIAL_SUCCESS
- draft preservation and truthful partial-success reconciliation
- avatar read/rejection/permission failures
- nickname validation/rejection handling
- unsaved-change Back confirmation
- IME / keyboard / one-scroll small-screen adaptation
- Safe Area / Accessibility
- no custom motion, haptic or sound requirement

The former 04/05/06 files remain historical indexes only and do not create separate navigation entries.

## Runtime Boundary

Existing runtime/API behavior does not make a design state Frozen.

Current design package status:
- behavior/state/interaction = **FROZEN**
- Edit Profile Home visual = **FROZEN**
- Avatar visual = **FROZEN**
- Nickname behavior = **FROZEN**
- Nickname visual = **BLOCKED_INTEGRITY**

Android Runtime Parity remains separate.
