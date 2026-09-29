# Recognition State Timeline Spec V1.2

Status: **ACTIVE CLOSURE**

Scope: **product / experience design only**.

This document defines what the user should understand at each Recognition Processing stage and how states relate to one another. It does **not** define Android implementation, model callbacks, controller behavior, CI, runtime bugs, or engineering acceptance.

## 1. State ownership

Recognition Processing itself contains exactly **four visible processing states**:

1. `CAPTURED`
2. `DETECTING`
3. `OUTLINE`
4. `CLASSIFYING`

In addition:

- `RESOLVE` is a short **transition**, not a fifth Processing state.
- High / Medium / Low are **Result-page states**, not Processing states.
- No Fish / Image Quality / Technical Failure are **Issue exits**, not Processing states.
- User Back is an **exit action**, not Failure.

## 2. Main experience flow

```text
Camera / Gallery
      ↓
   CAPTURED
      ↓
   DETECTING
      ├────────────→ Issue · No Fish
      ├────────────→ Issue · Image Quality
      ↓
    OUTLINE
      ↓
 CLASSIFYING
      ↓
   RESOLVE
      ├────────────→ Result · High
      ├────────────→ Result · Medium
      └────────────→ Result · Low
```

At any Processing state:

```text
User Back → Previous Surface
Technical Failure → Issue · Technical Failure
```

## 3. State contracts

### CAPTURED

**Purpose**

Give the user immediate confirmation that the selected/captured photo has entered Recognition.

**User understanding**

> “AI 已经拿到这张照片，识别马上开始。”

**Pacing**

Approx. **350ms** minimum visual beat.

**Allowed**

- original photo is already visible;
- peripheral AI atmosphere may begin to establish;
- status copy may communicate preparation.

**Forbidden**

- fish-local highlight;
- “已找到鱼” semantics;
- fish species or confidence information.

---

### DETECTING

**Purpose**

Communicate whole-photo understanding and fish-target search.

**User understanding**

> “AI 正在理解这张照片，并寻找这次鱼获的线索。”

**Pacing**

At least approx. **600ms** as the normal minimum beat. It may remain longer when the process has not progressed.

**Allowed**

- whole-photo AI activity;
- peripheral ambient field;
- status copy about understanding/searching.

**Forbidden**

- premature fish contour / halo;
- “已定位到鱼体” before that product claim is valid;
- fish species, confidence, candidate list.

**Possible exits**

- `Issue · No Fish`
- `Issue · Image Quality`

If either condition is established, Processing ends here. Do not continue through OUTLINE or CLASSIFYING merely to complete an animation story.

---

### OUTLINE

**Purpose**

Create the key Recognition “AI Moment”: the user should clearly feel that the fish itself has been located.

**User understanding**

> “AI 已经找到这条鱼，现在开始围绕鱼体分析。”

**Pacing**

At least approx. **600ms** as the normal minimum beat.

**Allowed**

- fish-local Halo;
- fish contour / subject emphasis;
- AI field may reduce in visual priority;
- copy may state that the fish body has been located.

**Forbidden**

- returning to vague whole-photo search semantics;
- concrete fish species result;
- confidence / candidate selection UI.

---

### CLASSIFYING

**Purpose**

Communicate species-level analysis after the fish body has already become the visual focus.

**User understanding**

> “AI 正在分析鱼体特征，判断它是什么鱼。”

**Pacing**

At least approx. **1250ms** as the normal minimum beat. It may remain longer when a conclusion is not yet ready.

**Allowed**

- stable fish focus;
- restrained breathing / analysis feeling;
- status copy about identifying the fish.

**Forbidden**

- displaying a fish species before entering Result;
- displaying confidence or candidate choices early;
- fake percentage progress or invented ETA.

---

## 4. RESOLVE transition

`RESOLVE` is **not** a Processing state.

It is a short **200ms visual release** between CLASSIFYING and the Result page.

```text
CLASSIFYING
   ↓
RESOLVE · 200ms
   ↓
RESULT
```

Its purpose is only to let the Recognition visual language release naturally:

- ambient AI field fades;
- fish focus releases;
- Processing status UI leaves;
- Result presentation takes over.

RESOLVE must not add:

- a new title;
- a new status card;
- “识别完成” as a fifth Processing step;
- extra waiting purely for decoration.

## 5. Result exits

After successful Recognition, the Processing page leaves through RESOLVE and enters one of three Result-page states:

| Result state | Product meaning |
| --- | --- |
| High | conclusion is clear |
| Medium | likely result exists; user confirmation may be useful |
| Low | species cannot be confirmed reliably |

These states belong to **Recognition Result**, not Recognition Processing.

## 6. Issue exits

Issue states are exits from Processing, not extra loading steps.

### No Fish

Use when the photo does not provide a usable fish target.

```text
DETECTING → Issue · No Fish
```

Do not show OUTLINE or CLASSIFYING afterward.

### Image Quality

Use when the image conditions are insufficient for reliable Recognition.

```text
DETECTING → Issue · Image Quality
```

Do not pretend that fish analysis continued.

### Technical Failure

Use when Recognition cannot continue for a technical reason.

```text
Any Processing State → Issue · Technical Failure
```

Technical Failure is not a fifth Processing card.

## 7. User Back

Back is a user-controlled exit:

```text
Any Processing State
      ↓
   User Back
      ↓
Previous Surface
```

Rules:

- Back is not Failure.
- Back does not route to an Issue page.
- Back does not need to wait for the current state’s minimum pacing.
- Back does not need RESOLVE.

## 8. Fast / normal / slow experience rules

### Normal

```text
CAPTURED
→ DETECTING
→ OUTLINE
→ CLASSIFYING
→ RESOLVE
→ Result
```

### Fast

Even when Recognition concludes very quickly, preserve the meaningful four-stage story.

Do not collapse into:

```text
CAPTURED → Result
```

The purpose is not to artificially delay the user; it is to preserve understandable state semantics and the key “found the fish” moment.

### Slow

When a stage takes longer, remain in that stage.

Do **not**:

- advance just because a timer expired;
- cycle copy to simulate progress;
- show a fake percentage;
- loop backward to a previous state.

The state may last longer; the meaning must remain stable.

## 9. Non-goals of State Timeline

This spec intentionally does not define:

- Android state classes;
- detector / classifier events;
- bbox implementation;
- controller logic;
- coroutine / lifecycle behavior;
- runtime evidence;
- CI gates;
- performance implementation;
- exact animation curves.

Those belong to later implementation/runtime work or other Recognition Design Manager sections.

## 10. Current closure statement

The State Timeline product model is:

```text
4 Processing States
+ 1 Resolve Transition
+ Result / Issue / Back exits
```

No additional Processing state should be introduced without a new design decision.

The page remains **ACTIVE_CLOSURE** until this product-state structure is reviewed and explicitly frozen.
