# Recognition State Timeline Review V1.2

Status: **ACTIVE CLOSURE**

Scope: State relationship only. This review does not redesign the four Frozen Processing keyframes and does not change detector/classifier semantics.

## 1. Core model — two lanes, one semantic source

Recognition has two different clocks:

1. **Real Pipeline State** — semantic truth from the production detector/classifier.
2. **Presented Visual State** — what the user is currently shown.

Frozen rule:

> Presented Visual MAY lag the Real Pipeline so each meaningful phase can be perceived, but it MUST NEVER lead the Real Pipeline or invent a semantic claim that has not happened.

Therefore Recognition is not a fixed 2.8s animation. 2.8s is only the nominal fast-result visual story.

## 2. Main processing sequence

```text
REAL PIPELINE
Photo accepted
   ↓
DETECTING
   ↓ real classifier-eligible primary bbox exists
OUTLINE
   ↓ classification starts
CLASSIFYING
   ↓ prediction ready
RESULT
```

```text
PRESENTED VISUAL
CAPTURED ≥350ms
   ↓ only if Real >= DETECTING
DETECTING ≥600ms
   ↓ only if Real >= OUTLINE
OUTLINE ≥600ms
   ↓ only if Real >= CLASSIFYING
CLASSIFYING
   ↓ real RESULT exists + stable-focus requirement satisfied
RESOLVE 200ms
   ↓
Result route
```

## 3. Processing phase contracts

| Presented phase | Minimum | Real gate | Allowed claim |
| --- | ---: | --- | --- |
| CAPTURED | 350ms | selected/captured photo accepted | photo has entered Recognition |
| DETECTING | 600ms | detector started | understand the whole photo; no fish-local claim |
| OUTLINE | 600ms | real classifier-eligible primary bbox exists | fish body has been located |
| CLASSIFYING | 1250ms nominal minimum | classifier started | analyze fish features |
| RESOLVE | 200ms | real RESULT exists | presentation release only; no new semantic claim |

RESOLVE is **not** a new `RecognitionPhase`. It is a presentation substate inside the tail of CLASSIFYING.

## 4. Fast result

If the model finishes early, the result is retained while the four processing visual phases are presented in order.

The user must not see:

```text
CAPTURED → RESULT
```

even if inference completes extremely quickly.

The valid story remains:

```text
CAPTURED → DETECTING → OUTLINE → CLASSIFYING → RESOLVE → Result
```

provided the real result contains the real detector assessment required for OUTLINE/fish focus.

## 5. Slow detector / classifier

If the detector is slower than the visual minimum:

```text
CAPTURED 350ms
→ DETECTING 600ms
→ DETECTING continues...
→ real bbox arrives
→ OUTLINE
```

The visual layer MUST NOT advance to OUTLINE just because 600ms elapsed.

If classification is slower:

```text
OUTLINE
→ CLASSIFYING 1250ms
→ CLASSIFYING continues...
→ real RESULT arrives
→ RESOLVE 200ms
→ Result
```

## 6. Resolve rule — P0 closure requirement

Current fast-result behavior can provide the 200ms resolve, but the current controller can skip it when RESULT arrives after CLASSIFYING has already exceeded its minimum.

Frozen target rule for closure:

```text
resolveStart =
  max(
    CLASSIFYING_ENTERED_AT + 1050ms,
    REAL_RESULT_READY_AT
  )

resultRouteAt = resolveStart + 200ms
```

This guarantees:

- at least ~1050ms stable CLASSIFYING/fish-focus presentation before fade;
- exactly one final 200ms visual release;
- fast and slow inference use the same ending grammar;
- the result is never delayed by another full 1250ms after it is ready.

## 7. Terminal branches

### Success / confidence branches

All three are real RESULT terminals:

- SUCCESS → Result High
- CONFIRM → Result Medium
- UNKNOWN → Result Low

High / Medium / Low are **Result-page states**, not Recognition Processing states.

### Detection terminal branches

If detector/quality gate reaches:

- NO_FISH → Recognition Issue · No Fish
- TOO_FAR / image-quality failure → Recognition Issue · Image Quality

These terminal branches have precedence over visual pacing. Do not fabricate OUTLINE or CLASSIFYING.

### Technical terminal branches

- invalid crop
- classifier failure
- runtime exception
- unavailable input image

route to Technical Failure / Issue immediately.

The normal product flow does **not** require a perceptible FAILURE processing card. Existing FAILURE copy is fallback-only if routing cannot complete synchronously.

## 8. User Back / cancellation

Back is not a recognition failure.

Rule:

```text
User Back
→ cancel/leave Recognition Processing immediately
→ return to previous surface
→ no Issue page
→ no delayed result delivery after exit
```

Minimum phase durations do not block explicit user exit.

## 9. Fish-focus semantic gate

- CAPTURED: OFF
- DETECTING: OFF
- OUTLINE: requires real primary bbox
- CLASSIFYING: requires real primary bbox
- subject contour is asynchronous visual enhancement only

A late contour may upgrade Level B → Level A without changing Recognition phase.

## 10. Closure findings

### Confirmed

- real pipeline is the semantic authority;
- visual may lag but does not lead;
- OUTLINE maps to a real classifier-eligible bbox;
- fast result retains all four processing stages;
- slow detector/classifier holds the real current state;
- RESULT is routing-only.

### Blocking

1. Slow-result path must guarantee the same 200ms Resolve as fast-result path.
2. FAILURE must be documented as routing/fallback semantics, not a required fifth Processing visual card.

Until these two points are closed, **01 · State Timeline remains ACTIVE_CLOSURE**.
