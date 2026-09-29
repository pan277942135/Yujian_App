# Login V2.1 · Frozen Design Spec

Status: **FROZEN**  
Scope: **Design only**  
Page title: **欢迎回来**

## 1. Frozen visual authority

- Frozen preview: `design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Preview.svg`
- Approved source size: **941 × 1672**
- Approved source SHA-256: `98eda20d23f7f6565776eb48e84bb171828c4b66ca3a6658285c5329825d9157`
- Design Manager preview: **SVG 941 × 1672**, referencing the frozen shared Morning Lake master.
- Preview SHA-256: `244765242b5fd5cc594375545af2c1c81cbfb287b8ecf34ca36b2af7a9219338`

The frozen preview is the authority for **layout, hierarchy, field/button treatment, white-space distribution and footer decoration**.

For landscape pixels, the shared background system has higher precedence:
`Morning_Lake_Master_V1 / BG_CONTENT`.

## 2. Page composition

The page is frozen as three visual zones:

1. **Top environmental zone** — no-sun Morning Lake, approximately 32–35% of the page.
2. **Main content zone** — large MistWhite / white field, carrying title, form and actions.
3. **Bottom footer decoration** — low-salience waterside grass, anchored to the absolute page bottom.

The lake fades naturally into the white content field. Do not place a separate glass card around the whole form.

## 3. Top-zone rule

The top environmental zone contains **no text**.

Explicitly prohibited:

- “渔见”
- “拍照收藏每次渔获”
- any replacement slogan
- any decorative headline over the lake

The first textual focal point is the page title **“欢迎回来”**.

## 4. Content hierarchy

Frozen order:

1. 欢迎回来
2. 继续记录你的每一次渔获
3. 账号
4. 账号输入框 / 请输入账号
5. 密码
6. 密码输入框 / 请输入密码 / visibility toggle
7. 忘记密码？
8. 登录
9. 还没有账号？创建账号

No new authentication field may be introduced by visual implementation.

## 5. Form treatment

- Content area: predominantly white / MistWhite
- Input surface: white, restrained, not floating directly on the lake image
- Input radius reference: **18dp**
- Main action height reference: **56dp**
- Main action radius reference: **28dp**
- Main action color direction: deep Lake Teal, approximately `#0F7A78`
- Primary text: DeepLake
- Secondary text: muted lake blue-gray
- No strong gradient, glow or heavy shadow

The form cadence is intentionally tighter than the earlier exploratory version.

## 6. Footer grass

Footer grass is **page-owned foreground decoration**, not part of the background master.

Frozen rules:

- anchored to the absolute bottom edge;
- approximately 8–10% visual height;
- low contrast / low salience;
- left/right natural growth is preferred;
- rocks must remain visually subordinate;
- must not obstruct footer text or CTA;
- must never be baked into `Morning_Lake_Master_V1`.

## 7. Shared design systems

- Background System: `Morning_Lake_Master_V1 / BG_CONTENT`
- Color + Typography V1
- Spacing + Radius V1

BG_CONTENT remains the shared content-background family, but Login uses the frozen **top-lake → white-content** composition instead of exposing the lake over the whole form.

## 8. Motion / haptic / sound

Frozen design decision:

- no automatic environmental motion;
- no automatic haptic;
- no sound;
- only standard control-state feedback and loading indication are permitted.

## 9. Scope boundary

This freeze updates **design authority only**.

It does not claim Android runtime parity, emulator evidence or APK closure. Runtime implementation must be validated separately against this frozen design.
