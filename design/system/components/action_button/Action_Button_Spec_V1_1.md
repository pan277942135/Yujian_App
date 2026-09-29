# Primary / Secondary Action Button V1.1 · Visual Refinement

Status: **FROZEN**  
Frozen date: **2026-09-29**  
Supersedes: **V1 for current visual treatment only**

## 1. Scope

V1.1 keeps the V1 semantic hierarchy unchanged:

```
PRIMARY
>
SECONDARY_STRONG
>
SECONDARY_MUTED
```

The six frozen action mappings are unchanged. V1.1 refines:

- surface/material treatment;
- subtle depth;
- paired-button proportions;
- pressed / loading / disabled / accessibility focus states.

It does **not** change button height, radius, semantic priority or business behavior.

## 2. Shared geometry

- Height: **56dp**
- Radius: **28dp**
- Minimum horizontal padding: **24dp**
- Label: **16sp / Semibold 600 / single line**
- Optional leading icon: **18–20dp**
- Icon ↔ label gap: **8dp**
- Paired action gap: **12dp**
- Single action: fill available content width
- Width remains stable between Normal / Pressed / Loading

## 3. PRIMARY

Use for the one action that completes or commits the page's main task.

### Normal

- Base fill: **#0F7A78**
- Top highlight: **rgba(255,255,255,0.08)**
- Bottom shade: **rgba(6,71,72,0.08)**
- Fine edge: **1dp rgba(255,255,255,0.10)**
- Label: **#FFFFFF**
- Shadow: **0 2dp 6dp rgba(11,45,75,0.10)**
- Gradient-looking decoration: prohibited
- Glow: prohibited
- Gold: prohibited

The highlight/shade is restrained surface light, not a decorative gradient.

### Pressed

- Fill: **#0C6D6B**
- Scale: **0.985**
- Shadow strength: reduce approximately **30%**
- No bounce / no overshoot

### Disabled

- Fill: **#D9E4E3**
- Label: **#91A5A6**
- Do not disable by lowering whole-component opacity.

### Loading

- keep dimensions unchanged;
- hide label;
- centered **18dp white progress indicator**;
- repeated tap disabled;
- no loading copy that changes width;
- no shimmer / pulse / breathing.

## 4. SECONDARY_STRONG

Use for a meaningful alternative that should remain clearly actionable.

### Normal

- Surface: **rgba(247,250,251,0.88)**
- Label: **#0B2D4B**
- Border: **1dp rgba(15,122,120,0.26)**
- Inner highlight: **1px rgba(255,255,255,0.65)**
- Shadow: **0 1dp 4dp rgba(11,45,75,0.05)**
- Gold: prohibited

This is a restrained Mist / Lake White surface, not a glass-effect spectacle.

### Pressed

- Surface: **#EAF3F1**
- Border alpha: approximately **0.36**
- Scale: **0.985**

### Disabled

- Surface: **#F2F5F5**
- Border: **#E3E9E9**
- Label: **#A0ADAF**

### Loading

- centered **18dp Deep Lake Teal progress indicator**;
- same dimensions and repeat-tap rule as PRIMARY.

## 5. SECONDARY_MUTED

Use for recovery, fallback or intentionally lower-priority alternatives.

### Normal

- Surface: **rgba(247,250,251,0.45)**
- Label: **#748897**
- Border: **1dp rgba(116,136,151,0.18)**
- Shadow: none
- Gold: prohibited

### Pressed

- Surface alpha: **0.90**
- Label: slightly darkened MistBlueGray
- Scale: **0.985**

### Disabled

Same disabled surface family as SECONDARY_STRONG.

### Loading

**N/A.** SECONDARY_MUTED does not own a loading state. For `重新拍摄`, activation transitions directly into the target capture/navigation flow; any waiting state is owned by the destination page. Do not show an inline spinner inside SECONDARY_MUTED.

## 6. Pairing ratios

Paired buttons are **not always 50 / 50**.

### Recognition Result · confirmed

```
[ 继续记录记忆 ] [ 保存本次鱼获 ]
       44%              56%
```

- SECONDARY_STRONG: **44%**
- PRIMARY: **56%**
- gap: **12dp**
- PRIMARY remains trailing/right in current frozen layout.

### Recognition Result · low confidence

```
[ 手动选择 ] [ 重新拍摄 ]
     58%          42%
```

- SECONDARY_STRONG: **58%**
- SECONDARY_MUTED: **42%**
- gap: **12dp**

The ratio reinforces hierarchy without changing height or typography.

## 7. Single-action layouts

Login and Register:

- left/right page content margin: **24dp**
- button: fill available content width
- height: **56dp**
- radius: **28dp**

No page-specific width/radius variants.

## 8. Accessibility Focus

- preserve original button surface;
- add **2dp #0F7A78** outline;
- **2dp external gap**;
- never use a gold focus ring.

## 9. Motion

Press feedback:

- press-in: **90ms**, scale 1.0 → 0.985, ease-out;
- release: **120ms**, scale 0.985 → 1.0, ease-out;
- no spring overshoot;
- no idle animation.

Reduce Motion:

- disable scale transform;
- preserve pressed color / surface feedback;
- loading progress remains functional.

## 10. Haptic / sound

Shared component:

- no automatic haptic;
- no custom tap haptic;
- no sound.

Business success/error feedback belongs to page/event contracts.

## 11. Frozen mapping

| Page / state | Copy | Variant | Layout |
|---|---|---|---|
| Login | 登录 | PRIMARY | full width |
| Register | 注册并登录 | PRIMARY | full width |
| Recognition Result · confirmed | 保存本次鱼获 | PRIMARY | pair 56% |
| Recognition Result · confirmed | 继续记录记忆 | SECONDARY_STRONG | pair 44% |
| Recognition Result · low confidence | 手动选择 | SECONDARY_STRONG | pair 58% |
| Recognition Result · low confidence | 重新拍摄 | SECONDARY_MUTED | pair 42% |

## 12. Prohibited

- gold fill for ordinary actions;
- multiple PRIMARY buttons in one action group;
- page-specific height/radius;
- equal-width pairing when the frozen V1.1 ratio applies;
- heavy shadow / neon / glow;
- visible decorative gradients;
- loading text that changes button width;
- using Primary Capture Button visual language for ordinary actions.


## 13. Frozen Visual Authority Set

The complete V1.1 static UI authority is the following six-reference set. Each reference is independently usable by development and QA.

1. `visual/authority/01_Base_Visual.svg` — PRIMARY / SECONDARY_STRONG / SECONDARY_MUTED.
2. `visual/authority/02_Single_Buttons.svg` — 登录 / 注册并登录 full-width single-button usage.
3. `visual/authority/03_Paired_Buttons.svg` — 44/56 confirmed-result pair and 58/42 low-confidence pair.
4. `visual/authority/04_PRIMARY_Five_States.svg` — Normal / Pressed / Loading / Disabled / Focus.
5. `visual/authority/05_SECONDARY_STRONG_Five_States.svg` — Normal / Pressed / Loading / Disabled / Focus.
6. `visual/authority/06_SECONDARY_MUTED_States.svg` — Normal / Pressed / Disabled / Focus; Loading = N/A.

The historical `visual/Action_Button_V1_1_Frozen_Visual.svg` is retained as an overview only and does not supersede this six-reference authority set.
