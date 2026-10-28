# 位置权限 · Privacy Spec V1

Status: **FROZEN — Permission Semantics / Copy / Interaction**
Visual status: **FROZEN — COMPOSITE AUTHORITY**

Visual authority set:
- `design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/01_Info.png`
- `design/pages/account_privacy/My/Account_Login/Data_Privacy/Location_Permission/02_Denied.png`
- adjustments: `design/pages/account_privacy/Active_Path_Visual_Adjustment_Authority_V1.md`
Page ID: `account_privacy_v1.04c`

## 1. Principle

YuJian does not request location permission merely because the app, My, Account & Security, or Data & Privacy is opened.

Permission request is scenario-driven.

The system permission request may occur only after the user explicitly chooses a location action such as:

> `使用当前位置`

while adding/editing a FishRecord location.

## 2. Data & Privacy row

The Data & Privacy page displays current coarse product status:

- `使用期间` — fine or coarse location permission currently granted;
- `已拒绝` — permission was previously requested and is not granted;
- `未授权` — no granted permission and no prior request recorded by the product.

V1 does not need to expose fine-vs-coarse precision on this settings row.

## 3. Row interaction

Tap `位置权限`:

- opens informational UI;
- does **not** directly invoke the Android permission dialog;
- does not modify permission state.

Title:

`位置权限`

Body:

`只有当你主动选择“使用当前位置”时，渔见才会获取你的位置，用于为当前鱼获添加地点。`

Must also state:

`拒绝不会影响拍照识鱼和保存鱼获。`

The current status may be appended.

Primary action:

`知道了`

## 4. Actual permission request

Owned by the location-use context, not this settings page.

Sequence:

1. user explicitly taps `使用当前位置`;
2. check current permission;
3. if needed, request Android runtime location permission;
4. if granted, resolve current location for the active FishRecord task;
5. if denied, keep manual location entry / non-location workflows available.

No background location permission is authorized.

## 5. Denial

Denial must not block:

- recognition;
- saving a FishRecord;
- species correction;
- manual location selection/entry where supported.

No repeated permission prompt on page entry.

No guilt copy.

No red destructive styling.

## 6. Privacy minimization

Location is used for the user-requested FishRecord location action.

Do not describe or design:

- continuous tracking;
- background tracking;
- automatic trip history;
- passive fishing-location collection

unless separately approved by a future privacy revision.

## 7. Error / unavailable location

If permission is granted but current location cannot be resolved:

- do not convert the state to “permission denied”;
- explain that current location could not be obtained;
- preserve manual location path.

## 8. Visual authority

Existing `01_Info.png` and `02_Denied.png` are the byte-preserved base Hi-Fi set.

Visual is **FROZEN via composite authority**. Current permission semantics, BG_CONTENT and shared dialog/settings components are governed by this spec plus `Active_Path_Visual_Adjustment_Authority_V1.md`. Runtime screenshots are not substitutes and no replacement images are required.
