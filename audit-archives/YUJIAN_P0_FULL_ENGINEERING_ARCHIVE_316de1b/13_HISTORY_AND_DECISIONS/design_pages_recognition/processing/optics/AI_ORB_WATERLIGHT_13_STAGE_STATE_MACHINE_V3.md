# AI Orb × WaterLight 13-Stage State Machine V3

## 1. State model

The controller owns one scene clock and one current stage. A stage has an entry timestamp, elapsed local time and progress, but no absolute global timestamp. Progress is sampled from the shared monotonic clock and current real event payload. The 13 stages are semantic identity; local motion timing may be stretched by real model waits. There is no global 2,800 ms / 850 ms / 3,650 ms completion transition.

Every model event, geometry callback and energy event must match the active session, attempt, image, photo SHA, content version and geometry version. The controller assigns an increasing event sequence and rejects stale or duplicate events.

## 2. Stage gates and production branches

| Current stage | Advance condition | If condition is missing |
|---|---|---|
| `PHOTO_STABLE` | Real source photo first frame has been presented | Remain at initial state; do not start elapsed presentation before this frame |
| `ORB_WAKE` | Local wake presentation complete | Continue same Orb wake from shared local time |
| `ORB_RELEASE` | Release path and first visible release effect complete | Continue Orb breathing/release; retain measured source anchor |
| `PERIMETER_ARRIVAL` | Current release path reaches a measured photo-edge point and edge response is drawn | Hold current path; do not synthesize a perimeter hit |
| `PATH_TO_FISH` | Current Detector target and valid PhotoSpace path exist; then Classifier and SlimSAM reach required terminal states | Hold at endpoint in restrained living state. `IDLE/QUEUED/PROCESSING` are pending |
| `CONTOUR_A` | `FISH_FOCUS_READY` and real Mask reveal begins | In MANUAL, allow inspection but show `WAITING_FOR_MASK` / `MASK_UNTRUSTED` and draw no fish outline |
| `CONTOUR_B` | The same trusted contour reaches the next local presentation milestone | Never substitute BBox or another photo's contour |
| `CONTOUR_C` | The same trusted contour reaches closed/stable presentation milestone | Never infer closure from the mask timer if contour is invalid |
| `FISH_GOLD_PEAK` | Real trusted Mask gold presentation completes | Skip in production if Mask is terminal but untrusted/failed |
| `ORB_RETURN` | Current branch's actual return path reaches the original Orb and the Orb receive event is presented | Hold the same return and Orb; no teleport or new Orb |
| `ORB_TO_NAME` | Real Classifier glyph geometry is available and Orb-to-glyph local morph completes | Do not replace the glyph with generic Text alpha |
| `SPECIES_NAME_REVEAL` | Real Chinese glyph becomes readable and the reveal event completes | Keep classifier failure in existing failure/unknown flow |
| `RESIDUE_SETTLE` | Local residue settle event completes | Release temporary optics then expose the ordinary Result page; CTA stays usable |

Trusted Mask route:

`01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11 → 12 → 13`

Terminal untrusted/failed Mask with valid Classifier result:

`01 → 02 → 03 → 04 → 05 → (06–09 production-skipped with explicit reason) → 10 → 11 → 12 → 13`

Classifier failure, attempt cancellation, identity mismatch, invalid photo, or watchdog timeout follows existing failure/cancel recovery. It cannot enter a success reveal path.

## 3. Recognition join

`RecognitionRevealDecisionGate` is the only production branch point:

| Classifier | SlimSAM | Mask / contour | Decision |
|---|---|---|---|
| waiting | waiting/processing | any pending | `WAITING_BOTH` |
| ready | idle/queued/processing | not terminal | `WAITING_SLIMSAM` |
| waiting | trusted terminal | valid | `WAITING_CLASSIFIER` |
| ready | ready | trusted and valid | `FISH_FOCUS_READY` |
| ready | ready or failed terminal | untrusted, invalid or failed | `ORB_ONLY_READY` |
| failed | any | any | `RECOGNITION_FAILED` |
| cancelled/stale | any | any | `CANCELLED` |

Classifier result is saved on arrival, but name reveal and Result handoff wait for the join and the selected visual branch. Classifier confidence is never used as Mask quality. A 60-second watchdog marks an unresolved model as a technical timeout; it is not a terminal `SlimSAM FAILED` success-degradation signal.

## 4. Energy event reducer

The same reducer records event source, target, progress and geometry:

| Event | Source → target | Required geometry |
|---|---|---|
| `RELEASE` | `ORB_RIBBON → PHOTO_PERIMETER` | Orb outer edge to current photo perimeter contact |
| `ARRIVE` | `PHOTO_PERIMETER → PHOTO_PATH` | Same photo transform and path ID |
| `ABSORB` | `PHOTO_PATH → FISH_CONTOUR` | Only valid for trusted current Mask |
| `RETURN` | `FISH_CONTOUR → ORB_CORE` or `PHOTO_PERIMETER → ORB_CORE` | Stored forward path traversed in reverse, or measured perimeter fallback |
| `TRANSLATE` | `ORB_CORE → REVEAL_TEXT` | Original Orb center to current real glyph Path anchor |
| `REVEAL` | `REVEAL_TEXT → REVEAL_TEXT` | Real glyph path/contour and current classifier name |
| `SETTLE` | `REVEAL_TEXT → RESIDUE` | Localized measured title geometry |

The path used for `RETURN` is the same stable path ID created for outward propagation and is sampled in the reverse direction. It is not reconstructed from a new random curve on every frame.

## 5. Debug playback

### MANUAL

- New Debug recognition Session defaults to `MANUAL`.
- No stage auto-advances.
- Next/Previous, Pause/Resume and Replay update the shared controller's scene-time projection only.
- Next is not disabled by absent Mask, absent Result geometry or a pending model.
- Pending/untrusted data is surfaced as a status; renderers remain strict and never fabricate a fish.
- Switching between diagnostic and normal-progress display preserves the exact current frame and event ledger.

### AUTO

- Debug-only mode with a minimum 3,000 ms presentation per diagnostic stage.
- AUTO still obeys each stage's real event gate; a pending model cannot be skipped by a timer.
- Production remains event-driven and does not inherit the Debug 3-second stage floor.
- Release contains no manual playback controls.

### Pause / resume

Pause freezes the one controller sample, all host outputs and stage-local progress. Resume advances from the captured monotonic offset; it does not restart Orb entrance, re-run models, repeat energy events or alter the PhotoSession.

## 6. Lifecycle and Result handoff

Cancellation, new photo, new attempt, geometry change, page re-entry and lifecycle disposal invalidate old events and renderer generations. A stale operation cannot publish a Mask, energy event, texture ownership claim, glyph or Result result. Current attempt state is preserved across Processing → Result through the existing reveal session store. The Result photo uses the same source bitmap and source-to-view geometry; no second photo decode/reload is introduced by animation.

The result CTA remains operable independently of optical settle. Only a valid high-confidence success with a real species identity enters the successful name reveal. Medium, low, no-fish, image-quality and technical failure follow their existing non-celebratory Result flows.

## 7. Observability

Each stage/event record includes:

`sessionId, attemptId, imageId, photoSha256, photoContentVersion, geometryVersion, stage, stageElapsedMs, eventSequence, monotonicTimestamp, detectorState, classifierState, SlimSAMState, maskTrust, contourValidity, sourceHost, targetHost, pathId, renderStatus`.

Renderer status records shader compile, link, texture upload, draw, EGL swap and photo owner separately. `PHOTO_GPU_FIRST_FRAME` means the current full-photo composite was successfully swapped; invoking a draw is insufficient.
