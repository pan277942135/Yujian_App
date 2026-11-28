# Non-model Gate Deadline Policy V1

**Policy ID:** `YUJIAN_NON_MODEL_GATE_DEADLINE_POLICY_V1`\
**Version:** `1.0.0`\
**Status:** `FROZEN_SPECIFICATION`\
**Applies to:** YuJian AI / PR #346\
**Authority:** Work supplement A, Gate Deadline Policy V1 numeric freeze table

This is a supporting policy for the six frozen core contracts. It does not change their bodies, versions, hashes, or status and is not a seventh core contract.

## 1. Independent clocks and fixed budgets

| Clock or budget | Frozen value | Owner and boundary |
|---|---:|---|
| Active presentation budget | 6,000 ms | SceneClock; Stage 01–13 only |
| Model Attempt Deadline | 60,000 ms | Shared by the four model families |
| Maximum single non-model Gate | 3,000 ms | This policy |
| Maximum AUTO non-model blocked wait per Attempt | 10,000 ms | Union of blocking intervals |
| PixelCopy capture | 1,500 ms | Evidence clock only; excluded from business wait |

SceneClock, Model Deadline, each Gate Deadline, the Attempt blocked-wait budget, and Evidence Clock are independent monotonic-time domains. A Gate cannot reset or extend another clock, advance a Stage, or pause SceneClock. `RecognitionSceneAnimationController` remains the sole Stage owner. A Result Geometry or Glyph Path preparation request may run asynchronously and cannot hold Stage10.

## 2. Frozen Gate table

`maxRetries` excludes the first request. Per-Gate cumulative blocking is bounded by the original deadline and is never renewed by retry. `AUTO` is the only mode that accrues business blocked wait; MANUAL/diagnostic playback may still evaluate Gate deadlines but does not consume the AUTO Attempt budget.

| Gate ID | Timeout ms | Max retries | Per-Gate cumulative ms | Attempt budget | Start event | Success event | Gate failure code |
|---|---:|---:|---:|---|---|---|---|
| `PHOTO_FIRST_FRAME` | 3,000 | 2 | 3,000 | Yes | `PHOTO_STABLE` first-frame request | Current photo first frame presented | `PHOTO_FIRST_FRAME_TIMEOUT` |
| `PROCESSING_GEOMETRY` | 2,000 | 1 | 2,000 | Yes | Normalized photo complete; processing geometry requested | Processing geometry ready | `PROCESSING_GEOMETRY_TIMEOUT` |
| `ORB_ANCHOR` | 1,500 | 1 | 1,500 | Yes | Enter `ORB_WAKE` | Orb anchor ready | `ORB_ANCHOR_TIMEOUT` |
| `RELEASE_RENDER` | 1,500 | 2 | 1,500 | Yes | Enter `ORB_RELEASE` | Release render receipt accepted | `RELEASE_RENDER_TIMEOUT` |
| `PERIMETER_ARRIVAL_RENDER` | 1,500 | 2 | 1,500 | Yes | Enter `PERIMETER_ARRIVAL` | Perimeter arrival render receipt accepted | `PERIMETER_ARRIVAL_RENDER_TIMEOUT` |
| `REVEAL_DECISION` | 1,000 | 0 | 1,000 | Yes | All required model outcomes terminal | Reveal branch selected | `REVEAL_DECISION_TIMEOUT` |
| `MASK_CONTOUR_PSTAR` | 2,000 | 0 | 2,000 | Yes | Mask callback terminal | Trusted contour and P* ready, or explicit untrusted terminal outcome | `MASK_CONTOUR_PSTAR_TIMEOUT` |
| `PATH_BOUND` | 2,000 | 1 | 2,000 | Yes | FishPath dual-end bind requested | Controller and SessionStore both accept binding | `PATH_BOUND_TIMEOUT` |
| `ORB_ONLY_RETURN_PATH` | 1,500 | 1 | 1,500 | Yes | `ORB_ONLY` branch confirmed | Degraded return path ready | `ORB_ONLY_RETURN_PATH_TIMEOUT` |
| `CONTOUR_RENDER` | 1,500 | 2 | 1,500 | Yes | KF10, KF11, or KF12 render requested | Matching contour draw receipt accepted | `CONTOUR_RENDER_TIMEOUT` |
| `FISH_GOLD_RENDER` | 1,500 | 2 | 1,500 | Yes | Enter KF13 | Fish-gold render receipt accepted | `FISH_GOLD_RENDER_TIMEOUT` |
| `RETURN_RENDER` | 2,000 | 2 | 2,000 | Yes | Enter KF14 / `ORB_RETURN` | Full route return render receipt accepted | `RETURN_RENDER_TIMEOUT` |
| `GLYPH_PATH` | 2,500 | 1 | 2,500 | Yes | Valid SpeciesResult available; GlyphPath preparation requested | Current snapshot GlyphPath ready | `GLYPH_PATH_TIMEOUT` |
| `ORB_TO_GLYPH_RENDER` | 2,000 | 2 | 2,000 | Yes | Enter KF15 | Orb-to-glyph render receipt accepted | `ORB_TO_GLYPH_RENDER_TIMEOUT` |
| `GLYPH_REVEAL_NAME_STABLE` | 2,000 | 2 | 2,000 | Yes | Enter KF18 | Current Glyph and name stability receipts accepted | `GLYPH_REVEAL_NAME_STABLE_TIMEOUT` |
| `RESULT_HOST_GEOMETRY` | 3,000 | 2 | 3,000 | Yes | Result host pre-mount / geometry request | Current Result host geometry ready | `RESULT_GEOMETRY_TIMEOUT` |
| `PHOTO_SETTLE` | 2,000 | 1 | 2,000 | Yes | Enter Stage13.2 | Photo settle receipt accepted | `PHOTO_SETTLE_TIMEOUT` |
| `HANDOFF_ACK` | 1,000 | 0 | 1,000 | Yes | HandoffEnvelope emitted | Consumer ACK accepted | `HANDOFF_ACK_TIMEOUT` |
| `SCENE_OWNER_TRANSFER` | 1,000 | 0 | 1,000 | Yes | After Consumer ACK, owner-transfer requested | Single Scene Owner transfer accepted | `SCENE_OWNER_TRANSFER_TIMEOUT` |
| `RESULT_INFO_ARRIVAL` | 1,500 | 1 | 1,500 | Yes | Enter Stage13.4 | Result information frame receipt accepted | `RESULT_INFO_ARRIVAL_TIMEOUT` |
| `RESULT_SCENE_STABLE` | 2,000 | 1 | 2,000 | Yes | Enter Stage13.5 | Stable Result Scene receipt accepted | `RESULT_SCENE_STABLE_TIMEOUT` |
| `RESIDUE_SETTLE_RENDER` | 1,500 | 2 | 1,500 | Yes | Stage13 residue stabilization requested | Residue render receipt accepted | `RESIDUE_SETTLE_RENDER_TIMEOUT` |
| `PIXELCOPY_CAPTURE` | 1,500 | 0 | 0 | No | Actual PixelCopy request submitted | Matching PixelCopy capture receipt | `PIXELCOPY_CAPTURE_TIMEOUT` |

The runtime policy also records the corresponding typed `startEvent`, `successEvent`, and failure code in `RecognitionGateDeadlinePolicyV1`; this table is the human-readable source for those constants.

## 3. Attempt blocked-wait accounting

Count only the time that production AUTO cannot continue because one or more non-model business Gates are incomplete. Concurrent blocking intervals are counted as their union, never summed per Gate.

- Model work still `RUNNING` does not consume the 10,000 ms non-model budget. A still-running model is governed by the shared 60,000 ms Model Deadline.
- `REVEAL_DECISION` starts only after all required model outcomes are terminal. `MASK_CONTOUR_PSTAR` starts only after the Mask callback is terminal, so neither includes model inference time.
- Asynchronous preload, Result pre-mount, or geometry work that does not block AUTO is not counted.
- PixelCopy, video capture, and Evidence ZIP export are excluded.
- Only a new Attempt resets this budget. Gate retry, Compose recomposition, Stage changes, and Result navigation do not.
- At 10,000 ms, emit `NON_MODEL_GATE_WAIT_BUDGET_EXCEEDED`, freeze the first-failure record, and stop waiting indefinitely.

## 4. Identity, ordering, retry, and failure

Each Gate run is keyed by Attempt ID and pins PhotoIdentity hash plus any applicable Snapshot ID/hash, Geometry Version, and Event Sequence. Requests and receipts with stale identity, hash/version mismatch, or out-of-order sequence fail closed. A duplicate `armOnce` returns the original run and deadline.

Only transient render-not-presented and temporarily unavailable geometry conditions are retryable. Identity mismatch, PhotoIdentity mismatch, Snapshot hash mismatch, Geometry Version mismatch, event reorder, invalid FishPath, untrusted Mask, and upstream terminal failure cannot retry. Retry preserves the original absolute deadline, Gate instance, Attempt, and accumulated budget; a successful receipt is accepted once. `PATH_BOUND` timeout never selects `ORB_ONLY` automatically.

The common failure categories are `NON_MODEL_GATE_TIMEOUT`, `GATE_IDENTITY_MISMATCH`, `GATE_EVENT_ORDER_INVALID`, `GATE_RETRY_EXHAUSTED`, `NON_MODEL_GATE_WAIT_BUDGET_EXCEEDED`, and `BLOCKED_BY_UPSTREAM_FAILURE`. Each timeout also emits its Gate-specific failure code from the table and records `FIRST_FAILURE` once. Downstream dependent Gates become `BLOCKED_BY_UPSTREAM_FAILURE`; unrelated asynchronous preparation is not mislabeled as the cause.

## 5. Gate event evidence

The immutable runtime event carries: `policyId`, `policyVersion`, `attemptId`, `gateId`, `gateInstanceId`, `eventName`, `eventSequence`, `timestampUptimeMs`, `deadlineUptimeMs`, `retryCount`, `photoIdentityHash`, optional `snapshotId`, optional `snapshotContentHash`, optional `geometryVersion`, AUTO/MANUAL mode, model-running state, Gate status, failure category/code, and blocked upstream IDs. Evidence observes these events and does not drive production state.

The one application-root scheduler asks for the nearest active Gate Deadline or Attempt blocked-budget boundary and evaluates due work. It must not create per-Gate timers, use wall clock, or be recreated by Compose recomposition. PixelCopy uses its separate evidence-only deadline. This scheduler reports Gate outcomes and never owns the Stage clock.

## 6. Unit-test acceptance matrix

| Acceptance | Required assertion |
|---|---|
| All Gate constants | All 23 IDs, timeout values, retry counts, budget flags, and maximums match this policy |
| Arm once | Duplicate request retains the original absolute deadline |
| Retry | Retry limit is exact; retry cannot move deadline or reset budget |
| Parallel waits | Overlapping blocking intervals count once |
| Model exclusion | Model-running time is excluded from non-model blocked wait |
| PixelCopy exclusion | Evidence capture does not consume business wait budget |
| Attempt isolation | Same Attempt cannot reset; a new Attempt does |
| Timeout | Gate-specific timeout and `FIRST_FAILURE` are immutable |
| Dependency failure | Downstream Gate is blocked with `BLOCKED_BY_UPSTREAM_FAILURE` |
| Stage10 independence | `RETURN_RENDER` has no Result Geometry or Glyph Path dependency |
