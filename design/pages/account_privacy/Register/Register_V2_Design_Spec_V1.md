# Register V2 · Frozen Design Spec

Status: **FROZEN**  
Scope: **Design only**  
Page title: **创建账号**

## 1. Frozen visual authority

- Frozen preview: `design/pages/account_privacy/Register/frozen/Register_V2_Frozen_Preview.svg`
- Approved source size: **941 × 1672**
- Approved source SHA-256: `9f28450a96c4149c190fe3bef47897a6c069b8221f14c4b54e95479f745d7585`
- Design Manager preview: **SVG 941 × 1672**, referencing the frozen shared Morning Lake master.
- Preview SHA-256: `e86db2acedd7aea537b2e14a7e3fe766b673f95181566760db5dd3012d2f19e7`

The frozen preview is the authority for **layout, hierarchy, form treatment, white-space distribution and footer decoration**.

For landscape pixels, `Morning_Lake_Master_V1 / BG_CONTENT` remains the shared background authority.

## 2. Relationship to Login

Register V2 is the sibling page of Login V2.1.

It must reuse the same:

- no-sun Morning Lake world;
- top-lake → white-content transition;
- title typography;
- field style;
- button language;
- spacing rhythm;
- footer grass treatment.

It is not a separately styled registration flow.

## 3. Page composition

1. **Top environmental zone** — no-sun Morning Lake; may be equal to or slightly shorter than Login because Register has one additional field.
2. **Main content zone** — predominantly white / MistWhite.
3. **Bottom footer decoration** — low-salience waterside grass at the absolute page bottom.

The environmental zone contains **no “渔见” and no slogan**.

## 4. Content hierarchy

Frozen order:

1. 创建账号
2. 用一个账号，留住你的钓鱼轨迹
3. 账号
4. 3–32 位字母、数字、_ 或 -
5. 密码
6. 至少 6 位 / visibility toggle
7. 昵称
8. 请输入昵称
9. 注册并登录
10. 已有账号？去登录

Frozen business fields are exactly:

- account
- password
- nickname

Do **not** add:

- phone number
- verification code
- confirm password

unless the product contract is separately revised.

## 5. Form treatment

Same Auth family as Login:

- 18dp input radius reference;
- shared `Action Button V1 / PRIMARY` for `注册并登录`: **56dp height / 28dp radius / #0F7A78 fill / white label**;
- DeepLake primary text;
- muted lake blue-gray secondary text;
- large clean white content area;
- no whole-form glass card;
- no strong glow / gradient / decorative effects.

Register may use slightly tighter vertical spacing than Login to accommodate the third field.

## 6. Footer grass

Footer grass is a **page foreground decoration**, not a background asset.

Rules are identical to Login:

- absolute bottom anchoring;
- approximately 8–10% visual height;
- subdued contrast;
- no obstruction of “已有账号？去登录”;
- rocks remain secondary;
- never bake grass into `Morning_Lake_Master_V1`.

## 7. Shared design systems

- Background System: `Morning_Lake_Master_V1 / BG_CONTENT`
- Color + Typography V1
- Spacing + Radius V1
- Primary / Secondary Action Button V1.1: `PRIMARY` for `注册并登录`

## 8. Motion / haptic / sound

Frozen design decision:

- no automatic environmental motion;
- no automatic haptic;
- no sound;
- only normal input/button/loading state feedback.

## 9. Scope boundary

This is a **design freeze only**.

It does not assert runtime implementation or runtime evidence parity.
