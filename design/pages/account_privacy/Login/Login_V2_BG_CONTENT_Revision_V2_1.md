# Login V2.1 · Frozen Design Spec

Status: **FROZEN**  
Scope: **Design only**  
Page title: **欢迎回来**

## 1. Frozen visual authority

- Final visual authority: `design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png`
- Approved source attachment: `160939.png`
- Approved canvas: **864 × 1536 (9:16)**
- Approved source SHA-256: `2d338ae93153339f96d8d06759f4676ca9ee9df83a071375ed537683b6ce114c`
- Design Manager preview mode: **direct authority image**, no SVG/HTML reconstruction.

The final high-fidelity image is the page-level visual authority for **composition, hierarchy, background treatment, typography placement, field/button treatment, white-space distribution and footer decoration**.

Implementation continues to reuse the shared `Morning_Lake_Master_V1 / BG_CONTENT` system where applicable, but the rendered result must visually match this frozen authority. The shared background system does not override the approved final page image.

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
- Main action uses shared `Action Button V1 / PRIMARY`: **56dp height / 28dp radius / #0F7A78 fill / white label**.
- Primary text: DeepLake
- Secondary text: muted lake blue-gray
- `忘记密码？` uses Text Action V1 / MUTED (`#748897`, 13sp/500 reference); `创建账号` uses STRONG.
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
- Primary / Secondary Action Button V1.1: `PRIMARY` for `登录`
- Text Action V1: `创建账号` = STRONG; `忘记密码？` = MUTED
- Icon Action V1: password visibility = CONTEXT / ON_LIGHT

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
