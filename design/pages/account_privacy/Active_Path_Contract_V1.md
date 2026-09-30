# Account & Privacy · Active Main Path Contract V1

Status: **FROZEN — IA / Behavior / Content Structure**
Visual closure: **ACTIVE_CLOSURE**
Scope: **Current MVP active path only**

## 1. Canonical path

```
Home avatar
  → 我的
      → 账号与安全
          → 修改密码
          → 数据与隐私
              → AI 模型改进
              → 位置权限
```

This is the active Account & Privacy path for the current MVP.

The following flows are intentionally outside this closure:

- Forgot Password — DEFERRED
- Export My Data — DEFERRED
- Delete Account — DEFERRED
- About / Legal content closure — separate workstream
- Edit Profile internal state closure — separate workstream

## 2. Naming

The previous design label `账号与登录` is superseded by:

> **账号与安全**

Rationale:

- Login / Register acquisition lives under 01 · 登录与注册.
- This surface manages an authenticated account, password security, privacy/data settings and logout.
- The new label removes IA duplication.

Android runtime may still display the historical title until Runtime Parity. Runtime existence does not override this design authority.

## 3. Shared system requirements

All active-path surfaces consume current shared authorities:

- Morning_Lake_Master_V1 / BG_CONTENT
- Top Navigation V1 / BACK_TITLE
- Mist Glass / current settings surfaces
- Color & Typography V1
- Spacing & Radius V1
- Action / Text / Icon V1
- Default Profile Avatar V1

No page may redefine these locally.

## 4. Visual governance

Historical PNGs remain composition/reference evidence only unless explicitly promoted by a versioned visual freeze.

Do not:

- call a historical screenshot current Frozen Authority;
- use Android runtime screenshots as Design Authority;
- reconstruct high-fidelity visuals in HTML/SVG and call them frozen;
- retain the legacy sunrise Account background where current BG_CONTENT is required.

The six behavior/content specs in this path are frozen independently of the missing current high-fidelity binary closure.

## 5. Navigation rules

- Back returns to the immediate parent.
- System Back and TopNav Back are semantically identical.
- No success page may create a dead-end back stack.
- Modal/sheet state closes before leaving its parent page.
- Deferred rows never expose their historical future-flow state machines in production.

## 6. Runtime boundary

This contract is design authority.

It does not claim Android Runtime Parity. Runtime differences are follow-up implementation work and do not reopen this contract unless a product requirement changes.
