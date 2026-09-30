# 账号与安全 · Home Spec V1

Status: **FROZEN — IA / Behavior / Content Structure**
Visual status: **ACTIVE_CLOSURE**
Page ID: `account_privacy_v1.03a`

## 1. Naming authority

Canonical page title:

> **账号与安全**

Historical title `账号与登录` is superseded for new design/runtime parity work.

## 2. Purpose

This page owns authenticated-account security entry points.

It is not the login screen and does not manage registration.

## 3. Navigation

Parent:

`我的`

TopNav:

- Shared `BACK_TITLE`
- title `账号与安全`

Rows:

1. current account identity;
2. 修改密码;
3. 数据与隐私;
4. 退出当前账号.

## 4. Current account card

Display:

- label `当前账号`;
- read-only username/account id intended for the user.

Do not add:

- inline account editing;
- password hints;
- internal user IDs;
- token/session identifiers.

## 5. Security navigation

`修改密码`:

- opens Change Password;
- no inline password editor here.

`数据与隐私`:

- opens Data & Privacy.

No duplicate `隐私政策` row is required here.

## 6. Logout

Label:

`退出当前账号`

Presentation:

- low-emphasis destructive/danger text;
- not a large primary button;
- not visually equivalent to account deletion.

Logout is not account deletion.

If runtime can safely terminate the current authenticated session without data loss, a separate confirmation is not required in V1.

After logout:

- authenticated account state is cleared;
- navigation resolves to the appropriate unauthenticated entry;
- no “注销成功” language.

## 7. Shared systems

- BG_CONTENT
- BACK_TITLE
- settings surface / rows
- shared icon actions
- shared spacing/radius

Historical `00_Account_Login.png` remains a visual reference until a current approved high-fidelity binary is frozen.

## 8. Visual closure gate

Current visual must show the new title `账号与安全`.

A historical PNG with title `账号与登录` cannot be promoted unchanged as current Frozen Authority.
