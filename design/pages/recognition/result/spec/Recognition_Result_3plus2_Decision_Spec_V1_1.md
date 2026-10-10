# Recognition Result · 3+2 Decision Specification V1.1 — Runtime Audit & Proposed Design Policy

**Status:** CURRENT_RUNTIME_DOCUMENTED / DESIGN_CHANGE_PROPOSAL (NOT a new frozen classifier threshold)  
**Audit date:** 2026-10-10  
**Scope:** design-only: semantics, gates, state priority, test matrix, release criteria  
**Android source reference:** `Yujian_App/main@88209f8d2e824252d75fe88636f74625aaf7981f`  
**Approval status:** Proposed behavioral refinements require explicit separate implementation authorization. NO Kotlin or model changes made.

## 0. Product model and meaning

`3+2` is **three fish-species result states + two image-input recovery states**, *not five bins of one probability*. `TECHNICAL_FAILURE` is a safety fallback outside the five states.

| State | Evidence semantics | Product response |
| --- | --- | --- |
| RESULT_HIGH / RR01 | Fish subject can be located; top species is sufficiently reliable for a **default recommendation**, not a factual guarantee | Preselect Top-1; prominent correction; Metadata/Story, Continue Memory and Save |
| RESULT_MEDIUM / RR02 | Fish subject is usable, but candidates are ambiguous | Present Top-K as suggestions only; require explicit species confirmation before persistence |
| RESULT_LOW / RR03 | Fish subject exists or is reasonably suspected, but no species is reliable enough to claim | Display editable Metadata/Story draft and actions; require manual species selection before any FishRecord |
| ERROR_NO_FISH / RR04 | No eligible fish evidence found by the detector | Retake / Gallery, no FishRecord |
| ERROR_IMAGE_QUALITY / RR05 | The input cannot support safe classification due to a **known reason** (currently geometry/smallness, not proven blur) | Explain reason in recovery family, offer retake/gallery, no FishRecord |
| TECHNICAL_FAILURE | Model/runtime/crop failed; this is **not** evidence of no fish nor poor photo | Generic retry/recovery, never blame user or misclassify as a 3+2 state |

### Fixed design invariants

- Maintain 3+2 and the existing five canonical Frozen state PNGs; RR06 is the content-edit authority index, not a state.
- Fish evidence and **species confidence are distinct signals**. Do not translate a detector confidence score into a species confidence percentage.
- Strong but ambiguous species outputs can be Medium; fish present with low species confidence is Low, **not** No Fish or automatically Image Quality.
- No user-facing classifier probability or debug values in the five Result screens.
- A model Top-1 suggestion is not user confirmation. A high-state preselection is editable.
- No unresolved Low or Medium FishRecord; no guessed unknown/pending species.

## 1. As-implemented runtime — verified source, **not a proposed new policy**

### 1.1 Fish detector and quality gate

Sources:
- `app/src/main/java/com/yujian/ai/ai/FishDetectorEngine.kt`
- `app/src/main/java/com/yujian/ai/ai/FishDetectionQualityGate.kt`
- `app/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt`

Detector `DET_FISH_v0.1`: class `fish` only; detection confidence = objectness × fish probability; non-max suppression IoU 0.45. The gate treats `>=0.35` as strong, `[0.20,0.35)` as weak. Under original orientation `NO_FISH`, the detector retries CW90 / CCW90; otherwise no rotation retry. A qualified recovered attempt is preferred.

Current gate evaluation order (this **order is critical**):

1. Filter fish detections with bbox area >0; sort strong and weak by `confidence × sqrt(box area)`.
2. **No strong** + weak exists → `UNCERTAIN`, `WARNING`, valid expanded bbox, **classifier eligible**, no small-area check in this branch.
3. No strong and no weak → `NO_FISH`, `INVALID`, not classifier eligible.
4. Multiple strong → `MULTIPLE_FISH`, `WARNING`, primary crop selected, **classifier eligible**, area check bypassed.
5. Single strong with bbox touching any edge within **0.015** normalized margin → `INCOMPLETE_FISH`, `WARNING`, classifier eligible, area check bypassed.
6. Single strong, not edge-touching, bbox area **<0.08 of full image** → `FISH_TOO_SMALL`, `INVALID`, classifier blocked.
7. Remaining single strong → `READY`, `GOOD`, classifier eligible.

The crop expands the selected bbox by **0.15 of its width and height per side** (clamped to image). `GOOD` and `WARNING` both pass; `INVALID` blocks. Crop/model errors may lead to technical failure.

**Important:** this quality gate does not itself measure blur, exposure, glare or occlusion intensity from image pixels. Therefore the RR05 generic label “照片不够清晰” is **not a verified diagnosis** for every input currently routed there.

### 1.2 Classifier and 3-band assignment

Sources:
- `app/src/main/java/com/yujian/ai/ai/FishRecognitionEngine.kt` (logits → Softmax probabilities)
- `app/src/main/java/com/yujian/ai/ui/identify/RecognitionUiState.kt` (presentation)
- `app/src/main/java/com/yujian/ai/ai/FishRecognitionPipeline.kt` (pipeline routing, duplicate threshold checks)

Let `p1 = top1.confidence`, `p2 = second candidate confidence`, and `margin = p1 - p2`.

| Precondition | Existing route |
| --- | --- |
| `failureCode != null` | TECHNICAL_FAILURE |
| `status == NO_FISH` | ERROR_NO_FISH |
| not classifier-eligible OR prediction absent without recorded failure | ERROR_IMAGE_QUALITY |
| classifier eligible and `p1 < 0.45` | RESULT_LOW |
| classifier eligible, `p1 >= 0.45`, candidate2 exists, `margin < 0.12` | RESULT_MEDIUM |
| otherwise (including absent candidate2) | RESULT_HIGH |

The application **does not downgrade High due to detector WARNING**, `UNCERTAIN`, `MULTIPLE_FISH` or `INCOMPLETE_FISH`. The `0.45 / 0.12` logic is duplicated in two Kotlin files, so the values could drift.

Boundary semantics: `p1==0.45` is not Low; `margin==0.12` is High. Values are Softmax scores over the known species class set, **not calibrated correctness rates**, and do not inherently detect species absent from the catalog.

Examples using **possible** Softmax top scores (sum <= 1):
- p1=.80 / p2=.10 → High;
- p1=.46 / p2=.30 → High (potentially unsafe even though margin=.16);
- p1=.46 / p2=.42 → Medium;
- p1=.31 / p2=.21 → Low.

### 1.3 User-facing persistence

High: Top-1 selected by default with `修改鱼种`. Medium: suggested candidate ≠ selected. Low: no selected species; editable Metadata and Story may exist as draft; unresolved CTA first enters `LOW_MANUAL` and cannot persist. No Fish/Image Quality: recovery only. Technical Failure: user-safe generic retry. These are already documented in Result V1.1 UI contracts.

## 2. Evaluation — what is reasonable / what needs refinement

| Audit | Disposition | Reason |
| --- | --- | --- |
| 3+2 separation | KEEP | Clear species ambiguity vs capture recovery boundary |
| Detector-first → classifier | KEEP | Avoids classifier on obviously empty photos |
| Low retaining editable draft | KEEP | Respects capture moment without inventing an unknown-species record |
| p1=.45 AND margin=.12 may label High | **REVIEW (P0)** | A low absolute Softmax top-score can be overconfident in closed-set classification; need calibration evidence |
| WARNING → High unconstrained | **REVIEW (P0)** | Weak-only / multiple detections create physical-subject uncertainty independent of species score |
| Small area bypasses in weak/multiple/edge branches | **REVIEW (P0)** | Nonuniform application of declared 8% minimum |
| RR05 always says image unsharp | **REVIEW (P1)** | Current invalid signal often means too-small fish, not verified blur |
| Predictable test examples with impossible p1+p2 >1 | **REVIEW (P1)** | Ensure realistic Softmax vectors in documentation/tests |
| Duplicated .45/.12 checks in pipeline/UI | **REVIEW (P1)** | Split-brain threshold drift risk |
| No explicit out-of-catalog rejection | **REVIEW (P1)** | Closed-set Softmax must select a class even for unfamiliar fish |

## 3. Recommended target policy — design **proposal**, not implemented/frozen

Strict priority; first matching predicate wins:

```text
INPUT_IMAGE
  -> detector / bounded orientation retry
  -> detector failed / crop exception / classifier exception?
         -> TECHNICAL_FAILURE (outside 3+2)
  -> no detection >= weak threshold, all permitted attempts exhausted?
         -> ERROR_NO_FISH
  -> fish evidence but invalid crop or insufficient fish coverage?
         -> ERROR_IMAGE_QUALITY (reason-coded framing/small-target guidance)
  -> fish evidence but quality WARNING?
         -> do not auto-elevate to HIGH:
              weak-only detection -> default LOW / manual recovery;
              multiple strong fish -> default LOW until target fish clarified;
              edge-touching fish -> evaluate using evidence-aware safeguard;
  -> valid fish + classifier prediction
         -> calibrated HIGH gate [to be learned from evaluation evidence]
         -> remaining plausible, ambiguous candidates MEDIUM
         -> no defensible species LOW
```

This policy is **intended behavior** pending model/QA data. Do not change `0.45`, `0.12`, detector `0.35/0.20`, or `0.08` in Android from this document. Specifically, an unverified new numerical HIGH cutoff must **not** be frozen merely for visual consistency.

### Recommended safeguards for future proposal review

1. Apply minimum-size validity consistently across all detection branches; preserve a reason code and treat border-clipped fish carefully rather than blanket-rejecting it.
2. Separate detector reliability (strong/weak/multi/edge), classifier confidence (p1/margin), and actual pixel-quality evidence; do not merge scores into a made-up universal probability.
3. Never allow weak-only or multi-subject warning to be promoted to High on classifier Softmax alone; maintain an easy manual species recovery.
4. Border-touching fish is normal in field photos; do not automatically send all of it to RR05. Maintain a dedicated acceptance cohort for partial/tail-edge cases.
5. RR05 should support **reason-specific help** in a future version: too small/too far, genuine blur/occlusion (only if independently measured). Changing text visible in Frozen PNGs requires approved versioned visual update; do not silently replace copy.
6. Unknown-species / out-of-distribution detection must be evaluated separately; Softmax alone cannot guarantee class coverage.
7. Share one authoritative threshold decision implementation in a future engineering task, with invariant unit tests for UI/pipeline parity.

## 4. Acceptance evidence required before freezing replacement cutoffs

Collect a label-verified, holdout dataset of **real field photos** stratified by:
- single fish, two+ fish, no fish, weak fish detection, small fish, edge-clipped / incomplete fish;
- day/night, glare, blur, backlighting, hand-held, bucket, riverbank, vertical/landscape;
- each of the supported species, visually similar species pairs, and **out-of-catalog** fish.

Compute per-state confusion matrix, high-state precision/coverage tradeoff, actual calibration reliability bins / ECE or Brier, Medium confirmation Top-K coverage, Low recovery success, and No Fish / Image Quality false-rejection rate. Evaluate boundary behavior at thresholds and segmentation by detector GOOD/WARNING reason. Preserve image/label/model SHA and dataset release IDs.

**Proposal release gates:** choose High minimum precision and recall/coverage goals with product owner; approve confidence thresholds from the measured precision-coverage curve, not intuition. No model changes are authorized in this audit.

## 5. Executable test matrix (future Android task; no implementation in this design PR)

| Input condition | As-is observed/expected | Proposed expectation |
| --- | --- | --- |
| no detections >=.20 after allowed retries | NO_FISH | NO_FISH |
| weak detector .25, classifier p1=.80 / p2=.10 | HIGH (possible) | must not auto High; prefer Low recovery |
| single strong .90, area .03, no edge | IMAGE_QUALITY | retain blocked classification with framing guidance |
| two strong, primary area <.08 | classification still attempted | smallness safeguard applies consistently |
| single strong touches border | WARNING, classification allowed, High possible | retain fish-aware permissiveness but verify safety |
| GOOD, p1=.46 / p2=.30 | HIGH | needs calibration to authorize High |
| GOOD, p1=.46 / p2=.42 | MEDIUM | candidate-confirmation |
| GOOD, p1=.31 / p2=.21 | LOW | manual recovery |
| classifier exception with failureCode | TECHNICAL_FAILURE | TECHNICAL_FAILURE |
| eligible but prediction null without failureCode | IMAGE_QUALITY fallback | classify as a technical invariant failure, not blame photo |

## 6. Authority boundary

- Frozen visual: `design/pages/recognition/design/05–09` (unchanged).
- Current state composition: `design/pages/recognition/result/01_high` etc and `engineering/layout_geometry_contract.json` (unchanged).
- Current runtime semantics: source Kotlin and model contract (read-only observed evidence).
- **This document is a design decision proposal / audit** and does NOT supersede original frozen routing or thresholds.
- It may inform future *versioned* Result decision-contract freeze after source-data-backed validation and user approval.

