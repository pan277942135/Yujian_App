# Register V2 Design Spec V1

Status: **ACTIVE_CLOSURE**
Feature: **Auth / Register**

## 1. Product role

Create a YuJian account without leaving the same visual world as Login.

Business semantics remain:

- username: 3–32 chars, letters / numbers / underscore / hyphen
- password: 6–72 chars
- nickname: required, 1–20 chars
- successful registration continues to login/session creation
- back CTA returns to Login

## 2. Shared background

Register uses the same shared background as Login:

- System: `Morning Lake Background System V1`
- Master: **Morning_Lake_Master_V1**
- Master: **no sun**
- Variant: **BG_CONTENT**
- MistWhite veil: **15% nominal**
- saturation: **92% nominal**
- effective contrast: **about 89–92%**
- luminance: **about +3%**
- blur: **none**

Register must not create or own another lake background.

## 3. Page hierarchy

Top brand block:

- 渔见
- 拍照收藏每次渔获

Content block:

- 创建账号
- 用一个账号，留住你的钓鱼轨迹
- 账号
- 密码
- 昵称
- 注册并登录
- 已有账号？去登录

## 4. Form treatment

Use the same auth family rules as Login V2:

- 24dp horizontal page padding reference
- 18dp input radius
- translucent light input surface
- DeepLake labels and primary text
- muted lake-gray helper/placeholder text
- teal primary action
- 56dp primary button height
- 28dp button radius
- no large opaque white card wrapping the whole form

Register may scroll vertically on smaller screens while the background remains fixed.

## 5. Error/loading

- loading: replace button label with compact progress indicator
- error: compact inline error text; do not create a new full-screen error state
- invalid form: primary CTA remains disabled

## 6. Shared-system references

- Background System / `BG_CONTENT`
- Mist Glass Surface
- Color + Typography
- Spacing + Radius

## 7. Freeze gate

Register V2 remains ACTIVE_CLOSURE until:

1. canonical high-fidelity screenshot is archived;
2. SHA / provenance is registered;
3. Login / Register visual family is reviewed together;
4. no legacy `AuthLayout` warm-card visual remains in the production route.
