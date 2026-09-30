# 修改密码 · Spec V1

Status: **FROZEN — Behavior / Validation / Content Structure**
Visual status: **ACTIVE_CLOSURE**
Page ID: `account_privacy_v1.03b`

## 1. Purpose

Allow an authenticated user to replace the current account password.

## 2. Page structure

TopNav:

- Shared `BACK_TITLE`
- title `修改密码`

Fields, in order:

1. 当前密码
2. 新密码
3. 确认新密码

Supporting copy:

`新密码至少 6 位`

Primary action:

`保存`

## 3. Frozen validation

Local validation:

- current password: non-empty;
- new password: 6–72 characters;
- confirmation must equal new password;
- new password must differ from current password.

Input must not exceed 72 characters in the client.

Local invalid state disables Save.

Server remains authoritative.

## 4. Password-field behavior

Each password field:

- obscured by default;
- may use shared visibility Context Icon;
- single-line;
- supports keyboard Next between fields;
- final field may use Done;
- visibility state does not alter field value.

Do not:

- expose plaintext by default;
- copy password into logs/analytics;
- persist password fields in saved UI state beyond the active form lifecycle.

## 5. Submission

On Save:

- enter Loading;
- prevent duplicate submit;
- keep user input while request is pending.

Server error mapping:

- current password wrong → `当前密码错误`;
- session invalid/expired → `登录状态已失效，请重新登录`;
- password rejected → `新密码不符合要求`;
- unknown/network → `修改密码失败，请重试`.

Local validation should be placed as close to the relevant field as possible.

Server/global failure may use the page feedback surface.

## 6. Success

Success copy:

`密码已修改`

After confirmed success:

- clear all password fields;
- return Save to Disabled;
- remain on the page unless product navigation explicitly changes in a future version.

Do not claim other sessions were invalidated unless backend contract explicitly guarantees it.

## 7. Back behavior

If no text has been entered:

- back immediately.

If a draft exists:

- current V1 may leave without a confirmation because no server-side change exists until Save;
- do not persist password drafts after page exit.

## 8. Accessibility

- labels remain visible/associated with fields;
- error state is not color-only;
- password visibility icons have accessible labels;
- loading state is announced;
- minimum interactive target 44dp.

## 9. Visual closure

Historical `01_Change_Password.png` remains reference evidence only.

Visual Frozen requires a current full-page authority aligned to BG_CONTENT, BACK_TITLE, current form components and this validation contract.
