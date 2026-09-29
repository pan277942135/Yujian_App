# Text Action V1 · Candidate Spec

Status: **ACTIVE_CLOSURE**
Version: **V1 Candidate**
Date: **2026-09-29**

## 1. Scope

Text Action is a tappable text-only action with no persistent visible container.

It covers account-flow switching, auxiliary account actions, local edit/change actions, lightweight view-all navigation, and low-weight actions placed on image/media surfaces.

It does not cover Primary / Secondary Action Button, Icon Action, Filter / Chip / Selection controls, or inline body links inside long-form legal content.

## 2. Semantic hierarchy

STRONG > NORMAL > MUTED

### STRONG
Use when an action changes the current flow or destination but should remain below the page Primary CTA.
Candidate examples: 创建账号 / 去登录.

### NORMAL
Use for local edit / drill-in / view-all actions.
Candidate examples: 修改鱼种 / 全部 / 编辑.

### MUTED
Use for auxiliary or fallback actions that should be discoverable but visually quieter.
Candidate example: 忘记密码？.

## 3. Geometry and touch target

- Visible text remains containerless.
- Minimum interactive target: **44 × 44dp**.
- Horizontal hit padding: minimum **8dp** each side when layout allows.
- Single line only.
- Do not change adjacent layout because the invisible hit target is larger.
- Do not add pill/background merely to satisfy hit target.

## 4. Typography

### STRONG
- size: **13sp**
- weight: **600**
- light-surface color: **#168B88**

### NORMAL
- size: **14sp**
- weight: **500**
- light-surface color: **#0F7A78**

### MUTED
- size: **13sp**
- weight: **500**
- light-surface color: **#748897**

No underline by default. No italic. No all-caps presentation copy.

## 5. Trailing chevron

For drill-in / edit / view-all actions:
- use a separate trailing chevron icon;
- canonical visual size: **14dp**;
- text ↔ chevron gap: **4dp**;
- icon inherits the action tone;
- do not bake ASCII > or › into the localized string.

Allowed examples: 修改鱼种 + chevron / 全部 + chevron / 编辑 + chevron.
Auth flow-switch and password-recovery actions do not use a chevron.

## 6. Surface tones

### LIGHT
- STRONG: #168B88
- NORMAL: #0F7A78
- MUTED: #748897

### ON_MEDIA
- label: **rgba(247,250,251,0.94)**
- chevron: **rgba(247,250,251,0.82)**
- optional readability shadow: **0 1dp 3dp rgba(11,45,75,0.24)**
- do not add a glass pill solely for Text Action.

## 7. Interaction states

Text Action supports Normal / Pressed / Disabled / Accessibility Focus.
Loading: **N/A**.

Pressed:
- no scale;
- no bounce;
- opacity/tone change only;
- duration: **90ms**.

Pressed tones:
- STRONG: #117F7C
- NORMAL: #0C6D6B
- MUTED: #657D8A
- ON_MEDIA: label alpha 0.72

Disabled:
- LIGHT: #A0ADAF
- ON_MEDIA: rgba(247,250,251,0.38)
- no whole-row fade when neighboring non-action text exists.

Accessibility Focus:
- keep text visual unchanged;
- **2dp #0F7A78** focus outline;
- **2dp external gap**;
- outline follows the 44dp minimum target, not the glyph bounds.

## 8. Motion / haptic / sound

- 90ms pressed tone/alpha transition.
- no scale, bounce, idle animation or shimmer.
- Reduce Motion unchanged; feedback is non-spatial.
- Haptic: none at component level.
- Sound: none.

## 9. Candidate real-page mapping

| Page | Copy | Variant | Tone | Chevron |
|---|---|---|---|---|
| Login | 创建账号 | STRONG | LIGHT | no |
| Register | 去登录 | STRONG | LIGHT | no |
| Login | 忘记密码？ | MUTED | LIGHT | no |
| Recognition Result | 修改鱼种 | NORMAL | LIGHT | yes |
| Normal Home | 全部 | NORMAL | ON_MEDIA | yes |
| FishRecordDetail | 编辑 | NORMAL | ON_MEDIA | yes |

## 10. Known authority conflict before freeze

Existing Login V2.1 Frozen Preview uses teal accent for 忘记密码？.
Candidate V1 intentionally proposes MUTED blue-gray for this action because password recovery is lower priority than Login Primary CTA and 创建账号 flow switch.
Do not revise Login V2.1 frozen visual until this candidate is approved.

## 11. Prohibited

- visible button container for ordinary Text Action;
- Morning Gold;
- underline by default;
- embedding > or › in localized copy;
- scaling/bouncing text on press;
- using Text Action for destructive confirmation;
- using Text Action when a Primary/Secondary Action Button is semantically required;
- page-specific arbitrary colors without a documented tone exception.
