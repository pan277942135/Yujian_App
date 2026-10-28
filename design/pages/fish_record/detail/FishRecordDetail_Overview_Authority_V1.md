# FishRecordDetail Overview Authority V1

Status: **FROZEN**  
Freeze date: **2026-09-30**  
Scope: **Design / Product behavior authority only**  
Runtime implementation: **not changed by this freeze**

## 1. Product role

FishRecordDetail is the single long-term detail destination for one FishRecord.

A-side and B-side are two surfaces of the same FishRecordDetail experience. They are **not separate routes, not separate records, and not separate first-level pages**.

- **A-side · 鱼获记录**: real catch record and original catch context.
- **B-side · 鱼获记忆**: generated digital fish / memory surface bound to the same FishRecord.

Default entry surface is **A-side**, except for the one-time first B-side reveal behavior defined below.

## 2. Frozen A / B page model

```text
FishRecordDetail
   ├─ A-side · 鱼获记录
   │    └─ default entry surface
   │
   └─ B-side · 鱼获记忆
        └─ available only when B-side lifecycle = READY
```

### A-side responsibilities

- original catch photo / Hero
- final species
- length / weight / location and available record metadata
- catch note / memory content
- information editing entry
- manual Flip Icon only when B-side = READY

### B-side responsibilities

- generated fish body / digital fish asset
- memory-oriented presentation for the same FishRecord
- manual Flip Icon back to A-side
- no duplicated FishRecord identity

## 3. Frozen B-side lifecycle

The product lifecycle is limited to four user-facing states:

```text
NOT_GENERATED
      ↓ Generate
GENERATING
   ├─ success → READY
   └─ failure → FAILED
                  ↓ Retry
              GENERATING
```

### State definitions

#### NOT_GENERATED

- No usable B-side asset exists.
- A-side is the only available detail surface.
- Flip Icon is hidden.
- User may start generation from the generation scenario defined under Design Manager 03.

#### GENERATING

- B-side generation is in progress.
- A-side remains usable.
- Flip Icon is hidden.
- Queue / Worker / infrastructure internals must not be exposed as separate user-facing pages.

#### READY

- A valid B-side asset exists and can be presented.
- A-side and B-side can both be reached.
- Flip Icon is visible on both A-side and B-side.
- READY activates the first-reveal rule below if it has not yet been completed.

#### FAILED

- The last generation attempt failed and no usable B-side is available.
- A-side remains usable.
- Flip Icon is hidden.
- Retry returns the lifecycle to GENERATING.
- Failure does not create a separate FishRecordDetail page.

## 4. Frozen first B-side reveal

### Core rule

**Each FishRecord receives at most one automatic A → B reveal after its B-side first becomes READY.**

Generation success alone does **not** mark the first reveal as completed.

The first reveal is completed only when the generated B-side has actually been presented to the user.

Conceptual product state:

```text
b_side_status = READY
first_b_reveal_done = false
        ↓
next real FishRecordDetail presentation
        ↓
enter / stabilize on A-side
        ↓
automatic A → B reveal once
        ↓
B-side is visibly presented
        ↓
first_b_reveal_done = true
```

### Foreground completion

If generation becomes READY while the user is currently on this FishRecordDetail:

- the current session may perform the one-time automatic A → B reveal;
- after B-side is visibly presented, first reveal is marked complete;
- it must not auto-flip again in that session.

### Background completion

If generation becomes READY while the user is away from FishRecordDetail:

- keep first reveal pending;
- on the next opening of this FishRecordDetail, start from A-side;
- then perform the one-time automatic A → B reveal;
- only after B-side is actually shown is first reveal considered complete.

### Interrupted first reveal

If the app/page is closed before B-side is actually presented:

- do not consume the first reveal;
- the next eligible opening may still perform it once.

### What is forbidden

- no automatic B → A flip;
- no automatic flip on every visit;
- no repeated auto flip after first reveal is completed;
- no automatic flip while B-side is NOT_GENERATED / GENERATING / FAILED.

## 5. Frozen subsequent-entry behavior

After `first_b_reveal_done = true`:

- every new FishRecordDetail entry defaults to **A-side**;
- B-side is reached only through explicit user action;
- no automatic A → B flip occurs on subsequent visits.

This preserves the FishRecord as the stable primary record surface while keeping the generated memory one action away.

## 6. Frozen manual Flip Icon behavior

### Visibility

```text
NOT_GENERATED → hidden
GENERATING    → hidden
FAILED        → hidden
READY         → visible
```

### Ownership

The Flip Icon is a **page-internal media utility**, not a Top Navigation action.

- semantic family: `UTILITY / ON_MEDIA`
- same stable visual position on A-side and B-side
- do not mix it into the top navigation Fish Guide / Share actions

### Interaction

On A-side when READY:

```text
Tap Flip Icon → A → B
```

On B-side when READY:

```text
Tap Flip Icon → B → A
```

The control is always explicit after the first reveal. There is no hidden gesture requirement.

### Asset invalidation

If a previously READY B-side becomes unavailable or invalid:

- hide Flip Icon;
- return / remain on A-side;
- do not expose a broken B-side.

## 7. Motion and Reduce Motion boundary

The product semantics are frozen; exact motion timing remains a later motion-spec concern.

Normal motion may use a visual flip transition.

With Reduce Motion enabled:

- do not require a 3D card flip;
- use a reduced transition such as crossfade or immediate surface switch;
- the one-time first reveal still counts once B-side is visibly presented.

## 8. Design Manager ownership

The frozen behavior is distributed without adding new navigation:

- **00 · Overview** — owns this A/B model, lifecycle, first reveal, subsequent-entry rules.
- **01 · A 面 · 鱼获记录** — owns A-side core UI and READY-only Flip Icon condition.
- **02 · B 面 · 鱼获记忆** — owns B-side core UI and manual A ↔ B interaction visual.
- **03 · 鱼体资产生成** — owns NOT_GENERATED / GENERATING / FAILED scenario UI and success handoff to READY.

No standalone “A/B 翻面系统” menu is required.

## 9. Frozen acceptance statements

The following statements are normative:

1. A-side and B-side belong to the same FishRecordDetail.
2. A-side is the stable default entry surface.
3. B-side has exactly four user-facing lifecycle states: NOT_GENERATED, GENERATING, READY, FAILED.
4. Flip Icon is visible only when B-side = READY.
5. The first READY B-side is automatically revealed at most once per FishRecord.
6. Generation success does not consume first reveal until the B-side is actually presented.
7. After first reveal, all future entries default to A-side.
8. Subsequent A ↔ B navigation is manual through Flip Icon.
9. No automatic B → A behavior exists.
10. Reduce Motion may change transition style but must not change the state semantics above.
