# Navigation / Flow Spec V2

Status: **PARTIAL package / Active MVP path FROZEN**

## 1. Active MVP path — FROZEN

```
Home avatar
  → 我的
      → 编辑个人资料
      → 账号与安全
          → 修改密码
          → 数据与隐私
              → AI 模型改进
              → 位置权限
              → 导出我的数据 · DEFERRED
              → 注销账号 · DEFERRED
              → 隐私政策
      → 关于渔见
```

Active-path owner:

`design/pages/account_privacy/Active_Path_Contract_V1.md`

The historical design label `账号与登录` is superseded by `账号与安全`.

## 2. Authentication

```
Login
  → Register
  → Forgot Password · DEFERRED
```

Forgot Password historical future-flow reference:

```
Account
  → Verification
  → New Password
  → Success
  → Login
```

This four-step recovery flow is **not current production behavior** until a verified recovery channel exists.

## 3. Data & Privacy

### AI Model Improvement — active

```
Data & Privacy
  → current consent state
      OFF → Enable confirmation sheet
      ON  → Disable confirmation sheet
```

Consent must be explicit. Withdrawal must not block recognition, FishRecord save or correction.

### Location — active

```
Data & Privacy
  → Location info/status only

FishRecord location action
  → explicit “使用当前位置”
  → permission check/request when needed
```

Opening Data & Privacy or the Location row does not request system permission.

### Export — DEFERRED

Historical reference states:

```
Overview → Processing → Ready / Failed / Expired
```

Production remains ENTRY ONLY / COMING SOON.

### Delete Account — DEFERRED

Historical reference states:

```
Explanation → Re-auth → Final Confirmation
```

Production remains ENTRY ONLY / COMING SOON.

## 4. Exit / back behavior

- Back from a full page returns to its immediate parent.
- A sheet/dialog closes before the underlying page is left.
- System Back and TopNav Back are semantically equivalent.
- Success state must not create a broken/re-entered completed form.
- Deferred flows must not expose historical future-state screens in production.
- Runtime navigation differences are tracked as Runtime Parity, not by reopening this design flow.

## 5. Shared system boundary

Current account pages use:

- Morning_Lake_Master_V1 / BG_CONTENT
- Top Navigation V1 / BACK_TITLE
- Shared Default Profile Avatar V1
- current Action / Text / Icon / Spacing / Radius authorities

Legacy Account & Privacy background imagery remains historical reference only.
