# Recognition State Timeline Spec V1.3

Status: **FROZEN**

Scope: **product / experience design only**.

This specification simplifies Recognition Processing from four user-visible steps to **three meaningful product states**. It intentionally does not define implementation, runtime acceptance, CI, or model behavior.

## 1. Why three states

The previous first two steps — “photo accepted” and “understanding the photo” — do not create a strong enough difference in user understanding to justify two separate Processing states.

They are merged into one clear opening state:

> **图片识别中**

The three-step product story is therefore:

```text
图片识别中
   ↓
已定位到鱼体
   ↓
鱼种识别中
   ↓
RESOLVE
   ↓
识别结果
```

This reduces copy churn while preserving the one moment that matters most: **AI 找到鱼体**.

## 2. State ownership

Recognition Processing contains exactly **three user-visible Processing states**:

1. 图片识别中
2. 已定位到鱼体
3. 鱼种识别中

In addition:

- `RESOLVE` is a short transition, not a fourth Processing state.
- High / Medium / Low belong to Recognition Result.
- No Fish / Image Quality / Technical Failure are Issue exits.
- User Back is an exit action, not Failure.

## 3. Main experience flow

```text
Camera / Gallery
      ↓
   图片识别中
      ├────────→ Issue · No Fish
      ├────────→ Issue · Image Quality
      ↓
   已定位到鱼体
      ↓
   鱼种识别中
      ↓
     RESOLVE
      ├────────→ Result · High
      ├────────→ Result · Medium
      └────────→ Result · Low
```

At any Processing state:

```text
User Back → Previous Surface
Technical Failure → Issue · Technical Failure
```

## 4. State 01 · 图片识别中

### Purpose

Combine the former “photo accepted” and “understanding the photo” beats into one understandable opening state.

### User understanding

> AI 正在理解这张照片，并寻找这次鱼获的线索。

### Allowed

- the original photo is visible immediately;
- peripheral AI atmosphere can establish and remain active;
- the UI communicates whole-photo understanding and searching.

### Forbidden

- fish-local Halo / Contour / Highlight;
- “已定位到鱼体” semantics;
- concrete fish species;
- confidence;
- candidate species;
- fake percentage / fake ETA.

### Possible exits

- Issue · No Fish
- Issue · Image Quality

If either condition is established, Processing ends here. There is no need to force the user through the remaining two steps.

## 5. State 02 · 已定位到鱼体

### Purpose

This is the Recognition page’s primary **AI Moment**.

The user should clearly feel that the system has moved from understanding the whole image to understanding **this fish**.

### User understanding

> AI 已经找到照片中的鱼，现在开始围绕鱼体分析。

### Allowed

- fish-local Halo;
- fish Contour / subject emphasis;
- the fish becomes the visual center;
- peripheral AI field reduces in priority.

### Forbidden

- returning to vague whole-photo search semantics;
- displaying a concrete species result;
- confidence or candidate list.

This state should create the strongest semantic change in the entire Processing experience.

## 6. State 03 · 鱼种识别中

### Purpose

Communicate species-level analysis after the fish has already become the visual focus.

### User understanding

> AI 正在分析鱼体特征，判断它是什么鱼。

### Allowed

- stable fish focus;
- restrained analysis feeling;
- continued but lower-priority AI ambient activity.

### Forbidden

- revealing the fish species before Result;
- confidence / candidate UI;
- fake progress percentage;
- extra rotating copy used only to simulate progress.

## 7. RESOLVE

`RESOLVE` is not a Processing state.

```text
鱼种识别中
   ↓
RESOLVE
   ↓
Result
```

Purpose:

> let the Recognition visual language release naturally and hand the visual center to the Result page.

Rules:

- no new title;
- no new status card;
- no “识别完成” fourth step;
- no decorative waiting step.

Exact pacing belongs to **04 · Motion & Transition**. The current design direction retains an approximately 200ms release, but State Timeline does not freeze timing.

## 8. Fast / normal / slow behavior

### Normal

```text
图片识别中
→ 已定位到鱼体
→ 鱼种识别中
→ RESOLVE
→ Result
```

### Fast

Preserve the three meaningful semantic beats. In particular, do not remove **已定位到鱼体** just because the overall recognition is fast.

### Slow

Remain in the current semantic state.

Do not:

- rotate through extra loading copy;
- add a fourth state;
- show fake percentages;
- show fake time remaining;
- automatically advance just to make the interface look active.

## 9. Legacy UI mapping

The previously archived four Processing UI references remain preserved as design source material.

They are remapped as follows:

| New product state | Existing source UI |
| --- | --- |
| 图片识别中 | `01_Capture_Transition_Frozen.png` + `02_AI_Understanding_Frozen.png` |
| 已定位到鱼体 | `03_Fish_Highlight_Frozen.png` |
| 鱼种识别中 | `04_Fish_Identifying_Frozen.png` |

The first two PNGs now represent **early / late reference frames of the same product state**, not two separate user-visible steps.

## 10. Timing boundary

State Timeline freezes **semantic order**, not precise timing.

Exact duration, easing, state overlap, and transition curves belong to:

> **04 · Motion & Transition**

This prevents state semantics and motion calibration from becoming coupled.

## 11. Current closure statement

The target Recognition Processing model is:

```text
3 Processing States
+ 1 Resolve Transition
+ Result / Issue / Back exits
```

No fourth Processing state should be added without an explicit new design decision.

This three-state product structure is now **FROZEN**. Any future change to the number, meaning, or ordering of Processing states requires an explicit new design version.
