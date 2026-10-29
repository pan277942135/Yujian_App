# Icon Action V1 · Frozen Spec

Status: **FROZEN**
Version: **V1**
Date: **2026-09-29**

## 1. Shared geometry

- Minimum touch target: **44×44dp**.
- Navigation glyph: **22dp**.
- Utility glyph: **20–22dp**.
- Context glyph: **18–20dp**.
- Default container: **transparent**.
- Optional ON_MEDIA support disc: **36dp circle** centered inside 44dp touch target.
- Support disc fill: `rgba(8,25,38,0.16)`.
- Support disc edge: `1dp rgba(255,255,255,0.18)`.

## 2. Icon family

Use one rounded-outline vector language:

- visually consistent stroke weight;
- round cap / round join;
- avoid mixing filled and outline variants in one action group;
- no emoji or text glyph substitutes;
- no ASCII arrows.

## 3. NAVIGATION

Purpose: move out of the current navigation layer.

Icons:
- Back → rounded back arrow.
- Close → rounded X; only for modal/sheet/full-screen transient layer dismissal.

Rules:
- Back and Close are not interchangeable.
- Input clear X is CONTEXT, not NAVIGATION Close.
- Default glyph: Deep Lake `#0B2D4B` at 92% emphasis.
- ON_MEDIA glyph: LakeWhite 94%.

## 4. UTILITY

Purpose: page-level tools that do not define the page's primary business action.

Frozen library:
- `card_flip` — A/B side manual flip.
- Share.
- Fish Guide / knowledge.
- Search — only when it opens/focuses search; a decorative search-field icon is not an action.
- Filter — only when it opens filter UI.
- More.
- Edit — allowed as page-level utility, but existing FishRecordDetail Hero `编辑` remains Text Action V1 unless a page revision explicitly changes it.

Card Flip special rule:
- icon remains static;
- the card/content performs the flip motion;
- no automatic repeating flip;
- manual action is available only when B-side exists.

## 5. CONTEXT

Purpose: local action owned by one field, card, media area or row.

Candidate library:
- Clear field.
- Password visibility.
- Voice input.
- Add media.

Context action never becomes a page-level navigation or primary CTA.

## 6. Tones

### ON_LIGHT
- default glyph: `rgba(11,45,75,0.88)`.
- pressed: `#0F7A78`.
- disabled: `rgba(116,136,151,0.38)`.

### ON_MEDIA
- glyph: `rgba(247,250,251,0.94)`.
- pressed glyph: `rgba(247,250,251,0.78)`.
- support disc permitted only when contrast requires it.

Gold is prohibited for ordinary Icon Action.

## 7. States

Required:
- Normal
- Pressed
- Disabled
- Accessibility Focus

Optional semantic state:
- Selected / Active belongs to the owning feature contract (for example Filter), not the generic Icon Action visual.

Pressed:
- **no scale**;
- no rotation;
- no bounce;
- color/alpha/support-surface change only;
- duration **90ms**.

Focus:
- 2dp `#0F7A78` outline;
- 2dp external gap;
- outline follows the 44dp target.

Loading: **N/A** for generic Icon Action.

## 8. Motion / haptic / sound

- No idle animation.
- No icon self-animation for card flip.
- Reduce Motion: no special branch required because press feedback is non-spatial.
- Haptic: none at component level.
- Sound: none.

## 9. Frozen page mapping

| Page / area | Action | Family | Tone |
|---|---|---|---|
| shared top bar | Back | NAVIGATION | ON_LIGHT / ON_MEDIA by surface |
| modal/sheet | Close | NAVIGATION | ON_LIGHT |
| FishRecordDetail | Fish Guide | UTILITY | ON_LIGHT |
| FishRecordDetail | Share | UTILITY | ON_LIGHT |
| FishRecordDetail B-side | Card Flip | UTILITY | ON_MEDIA |
| My Catches / Fish Guide | Search trigger | UTILITY | ON_LIGHT |
| My Catches | Filter trigger | UTILITY | ON_LIGHT |
| auth/input | Clear | CONTEXT | ON_LIGHT |
| auth/input | Password visibility | CONTEXT | ON_LIGHT |

## 10. Explicit exclusions

- Text Action chevron is governed by Text Action V1.
- Search Field leading icon is decoration unless separately tappable.
- Filter chips are Selection Controls.
- Primary Capture Button is its own branded component.
- Decorative location / length / weight icons are not actions.

## 11. Frozen Visual Authority

1. `design/system/components/icon_action/visual/authority/01_Families.svg` — 01 · 三大家族; SHA-256 `4417417743c83aa1eee64bdc4d556c930100aa0c48976407b293483e946d193f`.
2. `design/system/components/icon_action/visual/authority/02_NAVIGATION_States.svg` — 02 · Navigation 四态; SHA-256 `cccb91ab86ef67c07fd607b370b5032006f0ad6dbe7f6fe0690b9c2ff08bd2eb`.
3. `design/system/components/icon_action/visual/authority/03_UTILITY_Library.svg` — 03 · Utility 图标库; SHA-256 `b8d6c7da213c092504d02e6de47317c448747b238ad30a744c80c19dc591fb8c`.
4. `design/system/components/icon_action/visual/authority/04_UTILITY_States.svg` — 04 · Utility 四态; SHA-256 `fe01cf1ff86252a4d8bb3c809acd834db8f3c499b403351bceea2c139e1877c0`.
5. `design/system/components/icon_action/visual/authority/05_CONTEXT_Library.svg` — 05 · Context 图标库; SHA-256 `96a345bf96ad1c8222354c06d937c9c395175494cac2e65de8375c5b25fd8348`.
6. `design/system/components/icon_action/visual/authority/06_Real_Usage.svg` — 06 · 真实场景落位; SHA-256 `acfe607f69dccf541561ec166fff1675520682e0aaaa90063d500b8dca782e50`.
