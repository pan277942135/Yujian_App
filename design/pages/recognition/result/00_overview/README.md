# 00 — Recognition Result Overview / 结果总览

Status: **DESIGN CLOSED V1**

## 3+2 state model

### Result
1. High / 高置信
2. Medium / 中置信
3. Low / 低置信

### Recovery
4. No Fish / 未检测到鱼
5. Image Quality / 图片质量不足

Technical Failure remains an engineering-safe fallback outside this 3+2 Frozen menu.

## Frozen state authority

| State | Frozen |
| --- | --- |
| High | `05_Result_High_Frozen.png` |
| Medium | `06_Result_Medium_Frozen.png` |
| Low | `07_Result_Low_Frozen.png` |
| No Fish | `08_Error_No_Fish_Frozen.png` |
| Image Quality | `09_Error_Image_Quality_Frozen.png` |

All five remain canonical in `design/pages/recognition/design/`. Menu pages reference them; they are not duplicated.

## Common visual system

### Real image first

All 3+2 states retain the user's real capture as the context/hero media.

### Hero Media Contract

Dynamic user-photo placement is frozen by:

- `../media/Recognition_Result_Hero_Media_Contract_V1.md`
- `../media/hero_media_contract.json`

Shared rule:
- outer Hero geometry stays stable across photo ratios;
- High / Medium / Low = Subject First;
- valid fish bbox → Smart Crop only when FishSafeRect remains protected;
- unsafe crop → Safe Fit;
- No Fish / Image Quality = Evidence Fit, full source preserved;
- no detector/classifier crop as Hero source;
- no blurred-photo support fill;
- no generative expand/outpaint.

### Result is no longer Processing

After Result:
- no AI filament travel;
- no fish contour pulse;
- no detector box;
- no scanner/HUD;
- no continuous Recognition animation.

### Background

Use the Recognition Result / BG_CONTENT visual family.

The Result world is quieter than Home and no longer carries Processing energy.

### Navigation

Use the shared Result Top Navigation / P0 component family.

### Color

- lake/ink neutrals dominate;
- teal is the normal action accent;
- gold is scarce;
- recovery states do not introduce aggressive red error chrome.

## State differentiation

### High

AI result is usable.

Primary content:
- species identity
- lightweight record details
- memory note
- dual save/memory CTA

### Medium

AI gives plausible alternatives.

Primary content:
- one-line confirmation prompt
- horizontal candidate row
- explicit user confirmation
- then reuse normal record flow

### Low

AI cannot responsibly identify the species.

Primary content:
- `无法确认是什么鱼`
- manual species selection
- retake

No metadata/save before species recovery.

### No Fish

The photo does not provide a usable fish subject.

Primary direction:
- retake
- gallery fallback

### Image Quality

The image quality blocks reliable recognition.

Primary direction:
- retake with clearer capture
- gallery fallback

## Shared interaction rules

- no automatic carousel;
- no automatic species switching;
- no confidence scores;
- no debug/model metadata;
- no gamification;
- no no-op controls;
- loading blocks duplicate save;
- optional metadata never silently becomes mandatory;
- location permission is requested only by explicit user action.

## CTA map

| State | Before resolution | After species resolution |
| --- | --- | --- |
| High | already resolved | 继续记录记忆 + 保存本次鱼获 |
| Medium | choose/confirm candidate | 继续记录记忆 + 保存本次鱼获 |
| Low | 手动选择鱼种 / 重新拍摄 | reuse normal resolved-species record flow |
| No Fish | 重新拍摄 / 从相册选择 | N/A |
| Image Quality | 重新拍摄 / 从相册选择 | N/A |

## Motion

Result entry inherits the final 200 ms Processing resolve fade.

After entry:
- only shared component feedback;
- no page-level continuous animation;
- Reduce Motion removes nonessential interpolation.

## Adaptive behavior

All states:
- support vertical constraints;
- keep required actions reachable;
- preserve state hierarchy;
- do not hide CTA behind system bars/IME;
- do not change state semantics based on device height.

## Final design closure

01 High: **CLOSED V1**

02 Medium: **CLOSED V1**

03 Low: **CLOSED V1**

04 No Fish: **CLOSED V1**

05 Image Quality: **CLOSED V1**

Overall 3+2 Result Design Package: **CLOSED V1**

Runtime alignment remains a separate implementation/acceptance task.
