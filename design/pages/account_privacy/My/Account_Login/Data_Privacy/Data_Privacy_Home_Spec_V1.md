# 数据与隐私 · Home Spec V1

Status: **FROZEN — IA / Behavior / Content Structure**
Visual status: **FROZEN — COMPOSITE AUTHORITY**

Visual authority:
- base Hi-Fi: `design/pages/account_privacy/My/Account_Login/Data_Privacy/00_Data_Privacy.png`
- adjustments: `design/pages/account_privacy/Active_Path_Visual_Adjustment_Authority_V1.md`
Page ID: `account_privacy_v1.04a`

## 1. Purpose

Provide one clear place for:

- AI model-improvement consent;
- location-permission status/information;
- deferred data portability entry;
- deferred account-deletion entry;
- Privacy Policy access.

## 2. Navigation

Parent:

`账号与安全`

TopNav:

- Shared `BACK_TITLE`
- title `数据与隐私`

## 3. Canonical sections

### 数据使用

1. AI 模型改进
2. 位置权限
3. 导出我的数据 · Deferred

### 账号数据

4. 注销账号 · Deferred

### 法律

5. 查看《隐私政策》

Section labels are quiet supporting hierarchy, not card titles.

## 4. AI 模型改进 row

Row includes current state:

- 读取中…
- 已开启
- 已关闭

Trailing switch reflects server-backed consent state.

While loading:

- switch disabled;
- row does not open a state-changing sheet.

Tap row or switch when ready:

- opens the same explicit consent/withdrawal confirmation sheet;
- no instant silent toggle.

Behavior authority:

`AI_Model_Improvement/AI_Model_Improvement_Consent_Spec_V1.md`

## 5. Location row

Label:

`位置权限`

Status:

- 使用期间
- 已拒绝
- 未授权

This row is informational.

Opening it must **not** request Android permission.

Behavior authority:

`Location_Permission/Location_Privacy_Spec_V1.md`

## 6. Deferred rows

`导出我的数据` and `注销账号` remain visible so IA is stable, but current MVP behavior is:

> ENTRY ONLY / COMING SOON

Recommended presentation:

- normal readable row;
- trailing `即将推出` / equivalent muted status;
- do not visually imply the historical full future flow is available.

These rows have Design Manager status `DEFERRED`.

## 7. Privacy Policy

`查看《隐私政策》` opens the legal document route.

Legal copy approval is a separate gate and does not block this page IA.

## 8. Loading / failure

Privacy settings load:

- page shell and non-dependent rows remain available;
- AI consent row shows `读取中…`.

Failure:

- retain navigation rows;
- show restrained inline error;
- provide retry for consent state loading;
- do not guess the consent state.

## 9. Shared systems

- BG_CONTENT
- BACK_TITLE
- current settings surfaces
- current switch semantics
- current spacing/radius/type

The existing `00_Data_Privacy.png` is the byte-preserved base Hi-Fi and Visual is **FROZEN via composite authority**. Current Deferred treatment for Export/Delete, state labels, BG_CONTENT and shared components are governed by `Active_Path_Visual_Adjustment_Authority_V1.md`. No replacement image is required.
