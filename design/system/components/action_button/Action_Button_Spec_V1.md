# Primary / Secondary Action Button V1

Status: **FROZEN**

## 1. Design intent

普通业务按钮必须稳定、清晰、低装饰，不与 Primary Capture Button 争夺品牌视觉中心。

层级：

```
PRIMARY
>
SECONDARY_STRONG
>
SECONDARY_MUTED
```

不允许同一动作区出现两个 PRIMARY。

## 2. Shared geometry

- Height: **56dp**
- Radius: **28dp**
- Minimum horizontal padding: **24dp**
- Minimum touch target: **56dp height**
- Label: **16sp / Medium-Semibold / single line**
- Optional leading icon: **18–20dp**
- Icon ↔ label gap: **8dp**
- Paired action gap: **12dp**
- Single-action layout: fill available content width
- Paired layout: equal width by default

Button width must remain stable between Normal / Pressed / Loading.

## 3. PRIMARY

Use when the action completes or commits the page's main task.

Visual:

- Fill: **#0F7A78**
- Label: **#FFFFFF**
- Border: none
- Heavy shadow: prohibited
- Gradient: prohibited
- Glow: prohibited
- Gold: prohibited

Pressed:

- Fill: **#0C6D6B**
- Scale: **0.985**
- No bounce

Disabled:

- Fill: **#D9E4E3**
- Label: **#91A5A6**

Loading:

- preserve width and height;
- hide label;
- show centered 18dp progress indicator;
- no pulsing / breathing.

## 4. SECONDARY_STRONG

Use for a meaningful alternative that should remain clearly actionable but lower than PRIMARY.

Visual:

- Surface: **#F4F8F7**
- Label: **#0B2D4B**
- Border: **1dp rgba(15,122,120,0.28)**
- Shadow: none
- Gold: prohibited

Pressed:

- Surface: **#EAF3F1**
- Scale: **0.985**

Disabled:

- Surface: **#F2F5F5**
- Border: **#E3E9E9**
- Label: **#A0ADAF**

## 5. SECONDARY_MUTED

Use for recovery / fallback / lower-priority alternatives.

Visual:

- Surface: **rgba(247,250,251,0.72)**
- Label: **#748897**
- Border: **1dp rgba(116,136,151,0.24)**
- Shadow: none
- Gold: prohibited

Pressed:

- Surface alpha increases to **0.92**
- Scale: **0.985**

Disabled follows SECONDARY_STRONG disabled treatment.

## 6. Pairing rules

### Primary + Secondary

Recognition result:

```
[ SECONDARY_STRONG ] [ PRIMARY ]
继续记录记忆           保存本次鱼获
```

- same height;
- equal width;
- 12dp gap;
- PRIMARY on the trailing/right side for the current frozen result layout.

### Secondary + Muted

Low-confidence correction:

```
[ SECONDARY_STRONG ] [ SECONDARY_MUTED ]
手动选择                 重新拍摄
```

“重新拍摄” is intentionally downgraded and must not become PRIMARY.

## 7. State behavior

Required states:

- Normal
- Pressed
- Disabled
- Loading
- Accessibility Focus

Accessibility Focus:

- retain original button visual;
- add 2dp Deep Lake Teal focus outline with 2dp external gap;
- do not use gold focus rings.

## 8. Motion

Press feedback:

- press-in: 90ms, scale 1.0 → 0.985, ease-out;
- release: 120ms, scale 0.985 → 1.0, ease-out;
- no spring overshoot;
- no idle animation;
- no shimmer.

Reduce Motion:

- disable scale transform;
- retain pressed fill / surface feedback.

## 9. Haptic / sound

Button component itself defines:

- no automatic haptic;
- no custom tap haptic;
- no sound.

Business-level success/error haptic belongs to the page/event contract, not the shared button component.

## 10. Loading / repeated taps

When Loading:

- action is non-repeatable;
- button remains at the same dimensions;
- progress indicator replaces label;
- repeated tap is ignored;
- page owns timeout/error handling.

## 11. Six frozen mappings

| Page / state | Copy | Variant |
|---|---|---|
| Login | 登录 | PRIMARY |
| Register | 注册并登录 | PRIMARY |
| Recognition Result · normal save | 保存本次鱼获 | PRIMARY |
| Recognition Result · memory path | 继续记录记忆 | SECONDARY_STRONG |
| Recognition Result · low confidence | 手动选择 | SECONDARY_STRONG |
| Recognition Result · low confidence | 重新拍摄 | SECONDARY_MUTED |

## 12. Prohibited

- page-specific new primary color;
- Morning Gold fill for ordinary actions;
- multiple PRIMARY buttons in one action group;
- different heights/radii for sibling buttons;
- heavy shadow / neon / glow;
- turning “重新拍摄” into a dominant CTA;
- using Primary Capture Button styling for ordinary actions.
