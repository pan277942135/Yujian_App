# Recognition Processing Visual Spec V1.1

Status: **FROZEN**

## Visual intent

Recognition must feel like the original fishing photograph is being quietly understood, not scanned by a machine. The photo is always visually dominant. AI effects are supporting light phenomena, not a separate interface layer.

Forbidden:
- scanner rectangle, detector box, crosshair, HUD grid, radar, progress bar;
- full-frame blue/gold tint;
- neon ring wrapping the entire frame;
- fish glow before OUTLINE;
- replacing the captured image with a synthetic recognition screen.

## Layer stack

Back to front:

1. **L1 Original Photo** — opaque, ContentScale.Crop, no processing tint.
2. **L2 Edge Ambient Bloom** — narrow blue/gold edge/corner response only.
3. **L3 Blue Filaments** — four fixed paths B1–B4.
4. **L4 Gold Filaments** — three fixed paths G1–G3.
5. **L5 Sparse Particles** — 8 normal, 4 in low-performance mode.
6. **L6 Fish Halo** — only OUTLINE / CLASSIFYING and only when a real focus bbox exists.
7. **L7 Fish Contour** — only when a valid visual subject contour is available.
8. **L8 Status UI + Back**.

The original photo must remain readable between all overlay layers.

## Color / alpha tokens

| Token | Frozen value |
|---|---|
| AI_BLUE_CORE | #BFEAF2 |
| AI_BLUE_HOT | #D8F5FA |
| AI_GOLD_CORE | #F6D99B |
| AI_GOLD_HOT | #FFE7AE |
| STATUS_BG | #1A2C35 @ 0.85 |
| STATUS_TEXT_SECONDARY | #BFD0D7 |
| Filament hot point | <= 0.50 |
| Mid bloom | 0.05–0.10 |
| Outer bloom | 0.025–0.055 |
| Fish halo | 0.10–0.18 |
| Fish contour core | 0.30–0.42 |

## Per-phase visual contract

### CAPTURED — “正在准备识别”

- Photo: unchanged, immediately visible.
- Edge intensity: 0.20.
- Filaments: 0.15.
- Particles: 0.05.
- Fish focus: OFF.
- Status copy:
  - title: 正在准备识别
  - subtitle: AI 已获取这张照片
- Full restrained status card.

### DETECTING — “正在理解这张照片”

- Photo: unchanged and dominant.
- Edge intensity: 0.30.
- Filaments: 0.35.
- Particles: 0.20.
- Fish focus: OFF. No bbox/halo/contour may imply a located fish.
- Status copy:
  - title: 正在理解这张照片
  - subtitle: 寻找这次鱼获的线索
- Lightweight status treatment; no dark panel is required behind this phase.

### OUTLINE — “已定位到鱼体”

- Edge intensity: 0.22.
- Filaments: 0.24.
- Particles: 0.12.
- Fish halo + perimeter response begin only after a real focus bbox exists.
- Halo/contour reveal over 360–380ms.
- Status copy:
  - title: 已定位到鱼体
  - subtitle: 正在分析这次鱼获

### CLASSIFYING — “正在认识这条鱼”

- Edge intensity: 0.18.
- Filaments: 0.20.
- Particles: 0.08.
- Fish focus is the visual priority.
- Halo/contour breathe slowly; no pulsing detector box.
- Status copy:
  - title: 正在认识这条鱼
  - subtitle: 分析鱼体特征
- During the final 200ms after a real RESULT exists, ambient/status/fish focus fade together toward result presentation.

## AI edge field geometry

Seven paths are fixed design data, not generative art.

Blue:
- B1: (.78,-.02) → c1(.94,.06) → c2(1.02,.22) → (.96,.42), 1.2dp, phase 0.00
- B2: (1.01,.18) → c1(.92,.31) → c2(.95,.52) → (1.02,.68), 1.0dp, phase 0.23
- B3: (1.02,.61) → c1(.92,.75) → c2(.84,.89) → (.66,1.02), 1.2dp, phase 0.47
- B4: (.22,1.02) → c1(.38,.95) → c2(.52,.95) → (.66,1.01), 0.8dp, phase 0.71

Gold:
- G1: (-.02,.18) → c1(.03,.08) → c2(.12,.02) → (.26,-.02), 1.1dp, phase 0.12
- G2: (-.02,.33) → c1(.04,.47) → c2(.02,.62) → (-.01,.78), 0.9dp, phase 0.39
- G3: (-.01,.74) → c1(.08,.86) → c2(.20,.95) → (.38,1.02), 1.2dp, phase 0.64

Each filament uses:
- outer: 10dp, alpha multiplier 0.08;
- mid: 4dp, alpha multiplier 0.22;
- core: per-path width above, hot color, <=0.50 alpha;
- round caps and joins;
- visible dash segment 0.18–0.35 of path-cycle length.

## Particles

- Normal count: 8; low-performance: 4.
- Normal diameter feel: ~1–2dp; hot: ~3–4dp.
- Normal peak alpha <=0.35; hot <=0.50.
- Motion is sparse and secondary; particles may never look like confetti.
- Deterministic screenshot mode uses fixed anchors and visual clock.

## Fish focus geometry

The focus region maps from the real detector bbox through the same photo transform.

- center: mapped bbox center.
- radiusX: mapped bbox width × 0.62 + 14dp.
- radiusY: mapped bbox height × 0.72 + 14dp.
- radial outer extent: 1.18 × ellipse radii.
- interior remains transparent; do not fill/tint the fish.
- perimeter ellipse: 1.2dp gold response, reveal only after OUTLINE.
- radial halo peak alpha: <=0.18.

### Contour Level A

When a valid subject alpha/contour exists:
- contour threshold: alpha >=36/255;
- source sampling stride: 4px equivalent in current runtime;
- core: #FFE7AE, 1.5dp, alpha 0.30–0.42;
- glow: 8dp, alpha ≈42% of contour core;
- OUTLINE reveal: 0 → 0.42 over ~360ms;
- CLASSIFYING breathing: 0.36 ↔ 0.42 over 1900ms.

### Fallback Level B

When subject contour is unavailable but bbox is valid:
- retain bbox-derived halo + restrained perimeter ellipse;
- do not draw a rectangle;
- classification continues normally.

### Degraded Level C

Only for explicit low-performance degradation:
- center-biased halo may remain;
- contour can be disabled;
- visual degradation must never alter detector/classifier semantics.

## Status overlay

CAPTURED / OUTLINE / CLASSIFYING:
- nominal height 112dp;
- corner radius 30dp;
- background #1A2C35 @ 0.85;
- border #F6D99B @ ~0.50, 1dp;
- horizontal padding 28dp;
- indicator 54dp, 4dp ring;
- title 20sp medium; subtitle 14sp.

DETECTING:
- lightweight variant;
- indicator 40dp, 3dp ring;
- title 18sp medium; subtitle 14sp;
- no large dark glass block.

All variants fade with resolveProgress during final result handoff.
