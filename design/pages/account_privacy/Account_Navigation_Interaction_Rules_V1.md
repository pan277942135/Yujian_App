# Account & Privacy · Navigation & Interaction Rules V1

Status: **FROZEN**
Version: **V1.0 FINAL**
Date: 2026-10-01

## 1. Purpose

This document freezes navigation, Back, confirmation, sheet/dialog, feedback and interaction behavior across Account & Privacy.

## 2. Canonical navigation hierarchy

```
我的
├─ 编辑个人资料
│  ├─ 头像修改
│  └─ 昵称编辑
├─ 账号与安全
│  ├─ 修改密码
│  └─ 数据与隐私
│     ├─ AI 模型改进
│     ├─ 位置权限
│     ├─ 导出我的数据 · DEFERRED
│     ├─ 注销账号 · DEFERRED
│     └─ 隐私政策
└─ 关于渔见
   ├─ 用户协议
   └─ 隐私政策
```

Equivalent legal-document entries may converge on the same Shared Legal Document Shell.

## 3. Back behavior

TopNav Back and system Back are semantically identical.

### Read-only page

Back returns to the immediate parent.

### Editable page with no unsaved changes

Back returns immediately.

### Editable page with unsaved changes

Applies to active edit drafts such as:

- Edit Profile;
- Nickname;
- pending Avatar change.

Show one confirmation:

Title:
`放弃未保存的修改？`

Body:
`当前修改还没有保存。`

Actions:

- keep: `继续编辑`
- discard: `放弃修改`

Rules:

- Continue Editing closes confirmation and preserves the draft.
- Discard clears only the local unsaved draft and returns to parent.
- do not show this confirmation in PRISTINE or after a fully confirmed save.

### While SAVING

Do not leave the page while the save result is unresolved unless the underlying request is explicitly cancellable and a separate contract defines cancellation.

## 4. Settings are not dirty forms

AI Consent and Location do not use generic unsaved-change confirmation.

### AI Consent

A state change is an explicit confirmed transaction.

Before confirmation:

- persisted state remains authoritative.

After server confirmation:

- new state is authoritative.

Back simply closes the sheet or returns to parent; there is no hidden unsaved toggle draft.

### Location

Account & Privacy Location UI is informational.

Back closes the info surface/returns to Data & Privacy.

Opening or closing it does not create a permission-change draft.

## 5. Confirmation container rules

Use the existing page-specific authority first.

### Confirm Dialog

Use for compact high-consequence local decisions where two choices are sufficient.

Frozen current use:

- discard unsaved edit changes.

Future destructive account deletion is DEFERRED and must not reuse this rule to activate deletion without a new versioned design.

### Bottom Sheet

Use for contextual choice/explanation that benefits from more copy or multiple actions.

Frozen current uses include:

- AI model improvement enable confirmation;
- AI model improvement disable confirmation;
- avatar source/selection surfaces where defined by Avatar authority.

Location uses its frozen Location visual/behavior authority; this generic document must not force a different container than the approved page authority.

## 6. Container priority

Do not choose Dialog vs Bottom Sheet based on developer convenience.

Priority:

1. page-specific frozen authority;
2. this interaction rule;
3. Shared component behavior.

Do not convert an approved full page into a sheet, or a frozen sheet into a full page, without a design revision.

## 7. Toast rule

Toast is allowed only for short, non-critical, non-state-bearing feedback.

Examples:

- copy success, if such an action exists in a future page.

Core account/privacy outcomes must not rely only on Toast.

Do not use Toast as the only confirmation for:

- password change;
- profile save;
- consent change;
- authentication failure;
- permission state;
- logout state.

The durable page/control state must reflect the outcome.

## 8. Inline feedback

Preferred for:

- validation;
- save success/failure;
- load failure;
- consent save failure;
- permission explanation.

Feedback appears near the affected control/section whenever possible.

## 9. Primary-action rules

Primary actions must:

- state what they do;
- have one clear outcome;
- block duplicate submit while saving;
- expose Disabled / Loading semantically.

Do not place multiple visually competing Primary actions in one decision surface.

## 10. Logout

`退出当前账号` is not account deletion.

Current design:

- restrained destructive/danger text;
- no large destructive hero CTA;
- no account-deletion language.

A separate logout confirmation is not required by V1 when logout can safely complete without data loss.

If future product behavior makes logout materially destructive to unsaved data, that requires a versioned revision rather than an ad hoc runtime dialog.

## 11. Deferred rows

Forgot Password, Export My Data and Delete Account remain DEFERRED.

Interaction rule:

- entry may communicate Coming Soon;
- do not navigate into the historical future-flow boards;
- do not expose disabled fake controls that imply a hidden executable function;
- activation requires a design/product revision.

## 12. External system handoff

Android permission prompts are external system UI.

Location handoff rule:

1. user explicitly chooses `使用当前位置` in the FishRecord location context;
2. app checks current permission;
3. request system permission only when required;
4. return to the active FishRecord task;
5. denial preserves non-location/manual paths.

Account & Privacy page entry never triggers this handoff.

## 13. Keyboard / IME

Editable text fields:

- remain visible above IME;
- page may scroll to focused field;
- no nested vertical scroll;
- IME Done hides keyboard where appropriate;
- Save/error remains reachable;
- keyboard dismissal does not submit unless the page-specific contract explicitly says so.

## 14. Focus and modal behavior

When a Dialog/Sheet opens:

- accessibility focus enters the modal;
- background interaction is blocked as appropriate;
- closing returns focus to the initiating control when possible.

Back closes the topmost modal before leaving its parent page.

## 15. Double-action prevention

During a network mutation:

- duplicate submit disabled;
- repeated tap does not create multiple requests;
- visual loading maps to the single active request.

This applies to:

- profile save;
- password change;
- consent save.

## 16. Link/navigation semantics

Rows with chevrons navigate.

Switches represent current state.

A row must not visually look like a switch and behave like navigation, or vice versa, without an explicit combined-control pattern defined by the page spec.

AI Consent row/switch both open the same explicit confirmation behavior when ready.

## 17. Legal documents

Privacy Policy and User Agreement:

- one outer vertical scroll;
- Back returns to parent;
- viewing does not imply consent;
- links are explicit interactive elements;
- no acceptance CTA is added inside the document shell unless a future flow explicitly owns consent.

## 18. Motion / haptic / sound

Interaction feedback follows:

`Account_Privacy_Interaction_Feedback_Spec_V1.md`

No page may add private celebration, shake, vibration or sound to compensate for unclear interaction/state design.

## 19. Reopen rule

New navigation layers, new modal semantics, new destructive actions or new active Deferred flows require a versioned design revision.
