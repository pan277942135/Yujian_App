# Android RR09 Shared-Photo Transition Final Contract V1

**Contract ID:** YUJIAN-ANDROID-RR09-SHARED-PHOTO-TRANSITION-V1  
**Design status:** FROZEN_DESIGN_DIRECTION  
**Canonicality:** MAIN_AUTHORITY_ONLY_AFTER_MERGE  
**Android runtime:** NOT_RUN  
**Production Mask qualification:** NOT_QUALIFIED_BY_THIS_CONTRACT  
**Product-owner visual acceptance:** PENDING

## 1. Purpose and authority boundaries

This contract closes the Android implementation ambiguity between Processing P7 and the RR09 Result reveal. It binds the existing source-photo identity, the two permitted Hero placement modes, the live Processing-to-Hero transform, geometry-trust fallbacks, RR09 timing, title rendering and exact-head acceptance into one implementable Android contract.

It is an Android implementation contract; it does **not** replace the upstream domain authorities:

1. **Processing state and minimum story:** Recognition_Processing_Motion_Spec_V1_3.md — 2800ms minimum; actual result/business gates still control navigation.
2. **Shared session/photo handoff:** Processing_Result_Optical_Continuity_Contract_V1.md and its JSON.
3. **Reference geometry only:** SWOS_Shared_Photo_Transition_Contract_V1.md and its JSON. Its 360×640 sample uses a manually traced reference contour and is not a production Mask.
4. **Hero placement/crop:** Recognition_Result_Hero_Media_Contract_V1.md plus hero_media_contract.json V1.2.
5. **Reveal timing, eligibility and Chinese-only title:** Recognition_Result_Motion_Spec_V3.md plus recognition_result_motion_contract_v3.json.
6. **Optical calibration boundary:** SWOS_RR09_Optical_Parameters_V1_2.json. Candidate numeric values remain calibration candidates until measured and visually accepted.

If these sources appear to conflict, preserve the business/state and Hero Media authorities, preserve RR09 V3's 850ms timing, and use this contract to resolve Android transition interpolation, mode selection, handoff readiness and acceptance. Do not change recognition thresholds, classifier results, route semantics, save behavior or model inputs.

## 2. User-visible result

The Result route is entered immediately once the real pipeline, existing business gates and the 2800ms Processing minimum permit it. Do not add a 200ms wait before navigation. RR09 animation, including the photo-continuity segment, is eligible only for a new RESULT_HIGH result with valid species identity under Motion V3. Medium, Low, error, historical and reopened results use the existing static Result presentation; their Hero still uses the correct placement mode below, but they do not play the RR09 transition.

At the first actually presented Result frame (**T0**) for an eligible HIGH result, the Result page is already laid out at its final responsive positions. The original photograph is drawn by one shared transition layer, starting from the last measured Processing photo transform. It moves and scales into the actual Result Hero over 0–200ms. The page itself must not scale as a screenshot.

- The existing Result background and non-title content are already in their final static layout at T0. Do not add a separate content fade, translation, scale or reflow that would alter the existing static Result design.
- Save, edit, back and other valid Result actions are enabled at T0; animation never gates interaction.
- The Chinese species title follows RR09 V3: hidden during photo continuity and gold transfer, forming at 380–550ms, readable at 550–680ms, and stable by 680ms.
- The shared photo layer relinquishes drawing only when the Result Hero is ready to render the same source, selected crop and target transform. There must be no blank frame, duplicate sharp photo, opacity flash or crop jump.
- If the target geometry cannot be trusted before the first Result frame, fail closed to the normal static Result rendering. Do not show the target Hero first and retroactively start a full-screen transition on a later frame.

## 3. Photo placement: exactly two permitted modes

Photo placement and optical geometry trust are separate decisions. A valid detector BBox may be sufficient for a safe presentation crop even when no production-qualified Mask exists. Conversely, a BBox that is unusable for cropping must not be rescued by an approximate contour.

### Mode A — BBOX_ANCHORED_SAFE_CROP

Use only when the real detector BBox is valid and trusted for presentation, its image identity/source dimensions/EXIF-normalized coordinate space match the current PhotoSession, and the Hero Media crop planner verifies that the **entire valid BBox** remains inside the candidate crop.

Use the existing Hero Media V1.2 crop sequence without changing it:

1. Start with 10% horizontal / 12% vertical padding.
2. If a source edge is reached, stop expansion at that edge and transfer the missing expansion to the opposite side.
3. Retry 8%/8%, 4%/4%, then 0%/0%.
4. Accept a crop only if it contains the full valid BBox, satisfies the target Hero ratio and stays inside the oriented source after rounding.
5. If all profiles fail, switch to Mode B.

At R1, the shared overlay's source transform and crop must match the actual Result Hero's planned cropRect and rendering transform. Interpolate continuously toward that exact target; do not apply a second crop after the animation. A trusted BBox may drive crop even if the Mask trust tier is only TRUSTED_BBOX_ONLY; this never authorizes a rectangle outline or a contour.

### Mode B — FULL_SOURCE_FIT_SAME_SOURCE_AMBIENT

Use when the BBox is missing, invalid, stale, untrusted or in a mismatched coordinate space; when every safe-crop profile fails; and for NO_FISH / IMAGE_QUALITY states.

- Keep the complete EXIF-normalized original visible in the foreground using FIT/contain geometry.
- Preserve its aspect ratio. Fill the remaining fixed Hero container with a low-contrast blurred cover derived from that **same source photo**.
- Use the full source rectangle [0,0,1,1]; do not aggressively center-crop.
- No stretching, generated fill, outpainting, fish repair, solid letterbox, second photo, repeated sharp tile or post-entry crop jump.
- If BBox coordinates are untrusted but the real BBox still exists as untrusted data, do not use it to crop or draw an optical trace.

The existing Hero Media V1.2 state-specific container ratios remain authoritative. At the 360dp reference width: HIGH = 322×210dp; MEDIUM/LOW = 322×178dp; NO_FISH = 322×245dp; IMAGE_QUALITY = 322×214dp. Actual runtime bounds and insets take precedence over these reference sizes.

#### Frozen fallback reference sample — 360×640dp, 3:4 oriented source

These values apply to Mode B only; they are not universal constants for other screens or for Mode A.

| Sample | Outer photo/clip bounds (x, y, width, height) | Foreground source-photo bounds (x, y, width, height) |
|---|---|---|
| R0 / T0 | (0, 0, 360, 640) | (0, 80, 360, 480) |
| R0.5 / 100ms | (9.5, 26.5, 341, 425) | (50.625, 66.5, 258.75, 345) |
| R1 / 200ms | (19, 53, 322, 210) | (101.25, 53, 157.5, 210) |

For Mode A, derive R0, R0.5 and R1 from the live start transform and the selected Hero crop; do not reuse Mode B's foreground rectangle.

## 4. One transform and the R0.5 midpoint

The following must be shared across the transition: PhotoSession ID, recognition session/attempt IDs, stable imageId, SHA-256 of the persisted EXIF-normalized source file, source dimensions, detector BBox, Mask/contour (when trusted), source anchor, clip geometry and the source-to-window transform. The current Handoff factory's photoSourceIdentity string contains imageId/source/dimensions but not a content hash; it does not satisfy the SHA invariant. Add explicit SHA propagation through the existing image/session handoff without creating a second PhotoSession or re-encoding the photo per frame.

Let u = clamp(t / 200, 0, 1), where t is RR09 elapsed time in milliseconds. Define the photo interpolation parameter as:

**q(u) = u² × (3 − 2u)**

Use q to interpolate the source scale, translation and clipping bounds from the measured Processing geometry to the actual Hero geometry. This symmetric curve is smooth at both ends and satisfies:

- 0ms: q = 0 — exact Processing start.
- 100ms: q = 0.5 — exact geometric midpoint.
- 200ms: q = 1 — exact Hero target.

**Do not use an ease-out curve that advances beyond the midpoint at 100ms.** The current Android RecognitionRevealClock uses easeOutCubic(t / 200); at 100ms that yields 0.875. This violates the frozen R0.5 sample even though the geometry resolver itself linearly interpolates scale and translation.

The photo is one bitmap/source rendered through one continuous transform, not a crossfade between a full-screen image and a separately loaded Hero image. BBox, Mask and gold must be mapped through that same transform. The overlay/hero handoff must be atomic at R1.

## 5. Geometry trust and optical behavior

Photo mode selection must not promote Mask quality. Use these trust tiers:

| Geometry state | Allowed behavior | Prohibited behavior |
|---|---|---|
| TRUSTED_MASK | Three gold layers share the same qualified Mask; a contour-following source and spatial transfer are allowed only when source identity, mapping and anchor checks pass. | Reconstructing a contour in screen coordinates or changing the source crop independently. |
| TRUSTED_BBOX_ONLY | If HIGH + valid species identity + source mapping are all valid, use only a subtle bounded regional glow. A spatial transfer may run only from an explicit validated source anchor; otherwise use non-spatial title resolution. | Drawing the BBox rectangle, claiming a Mask, or inventing a contour/anchor. |
| UNTRUSTED, MISSING or STALE fish geometry, while photo identity and photo transform remain valid | For an eligible HIGH result, retain RR09 timing but use a restrained non-spatial Chinese-title resolution. | Fish-specific glow, contour trace, spatial travel or a guessed source point. |
| Invalid photo identity, source-to-window transform mismatch or unresolved Hero target geometry | Render the normal static Result and its correct Hero placement mode; do not start a transition retroactively. | Any shared-photo animation, spatial transfer or guessed transform. |

The three optical layers are Gold Core / Soft Glow / Halo and must use the same trusted Mask and transform. The Android renderer currently hard-codes 28px / 13px / 4px contour widths and fixed alpha values; these are implementation observations, **not approved final calibration values**. Align Android values with the machine-readable SWOS/RR09 parameter contract, convert units using device density and the shared source transform, and keep candidate values marked as candidates until visual comparison is accepted.

For Chinese-name formation, particles must be clipped to the actual Chinese glyph mask. Uniformly distributing particles over the title rectangle or revealing text only with a generic horizontal rectangle is not equivalent to glyph formation. No English scientific-name line or empty reserved row is permitted by RR09 V3.

## 6. RR09 timing and fallback behavior

The RR09 clock starts at the first actually presented Result frame. The total remains 850ms after navigation; Processing remains 2800ms minimum. The nominal 3650ms combined visual story is not an inference deadline.

| RR09 elapsed | Phase | Required behavior |
|---|---|---|
| 0–200ms | Photo continuity | Same source photo moves to the actual Hero. Ambient water light is zero. No added navigation wait. |
| 200–380ms | Gold transfer | Gold moves from a validated fish region toward the existing Chinese title only when geometry and anchor are trusted. |
| 380–550ms | Chinese-name formation | The actual Chinese glyphs form; the complete name is not readable early. |
| 550–680ms | Chinese-name reveal | The Chinese name becomes fully readable in its existing title block; one restrained sweep at most. |
| 680–850ms | Afterglow fade | Transient optics decay to zero; static Result remains stable and interactive. |

Eligibility:

- New RESULT_HIGH with valid species identity and valid photo geometry: run the 850ms RR09 timeline once. With trusted Mask, use contour-driven transfer; with BBox-only, use bounded regional glow and only a validated anchor; with untrusted fish geometry but valid photo transform, use the non-spatial title fallback on the same 380/550/680ms boundaries. If photo identity or the source-to-window transform itself is invalid, use static Result and do not run RR09.
- RESULT_MEDIUM / RESULT_LOW: use the existing static Result presentation; no RR09 photo transition, gold celebration or title reveal.
- NO_FISH / IMAGE_QUALITY: use Mode B in the existing static Result presentation; no RR09 photo transition or celebration.
- Invalid identity, historical/reopened result, missing source identity or invalid transform: render the safe static Result. Do not invent a transition origin.
- Reduce Motion: skip spatial travel, particles, sweep and animated photo interpolation; present the correct final photo mode and an immediately readable title. Never hide valid result data or disable actions.
- After 850ms, stop the frame clock and all transient drawing. Recomposition, rotation, editor/selector return or repeated rendering of the same session must not replay the reveal.

## 7. Implementation gaps that must close

| Gap | Current Main observation | Required correction / evidence |
|---|---|---|
| G01 — R0.5 timing | RecognitionRevealClock uses ease-out cubic; 100ms maps to 0.875. | Use midpoint-preserving q(u); unit-test 0/100/200ms against exact endpoints and midpoint. |
| G02 — crop-mode binding | Hero Media V1.2 defines safe crop and same-source fallback, but transition must use the actual Hero cropRect and transform. | Test both modes, every crop retry and fallback; assert no second crop jump at R1. |
| G03 — first-frame ownership | If the Hero is presented before transition geometry is ready, a later overlay can retroactively jump back to the Processing image. | Before first presentation, either draw the shared photo at progress 0 with the Hero hidden, or fail closed to static Result. Never reveal the target Hero and then start the overlay later. |
| G04 — BBox-only eligibility | The trust model has TRUSTED_BBOX_ONLY, but current eligibility collapses non-Mask cases into TITLE_FADE_ONLY. | Implement a separate bounded-region path; no BBox rectangle; require a validated anchor for spatial travel. |
| G05 — fallback timing | Current non-full-reveal clock path can finish title fade in the first 200ms. | For eligible HIGH + valid identity without spatial geometry, keep RR09's 380–550 / 550–680 title boundaries and remove only spatial effects. |
| G06 — glyph formation | Current particles are distributed by title-rectangle fractions and title clipping is horizontal, not a glyph mask. | Use actual glyph-mask clipping; verify no complete readable name before 550ms and no English row. |
| G07 — optical calibration | Android uses hard-coded 28/13/4 widths and fixed alpha values. | Bind calibrated values to the SWOS/RR09 parameter source; do not mark candidate values frozen without owner comparison. |
| G08 — English title row | RecognitionResultScreen can render scientificName in the title row. | Enforce RR09 V3 Chinese-only title with no English line or reserved blank row. |
| G09 — ineligible-state animation | PHOTO_ONLY eligibility can animate photo continuity for states excluded by RR09 V3. | Gate all RR09 motion, including photo continuity, to new HIGH + valid identity; keep Medium/Low/error/historical results static. |
| G10 — lifecycle/one-shot | Handoff keys exist, but exact runtime proof is required. | Test retry, rapid photo change, recomposition, rotation, selector/editor return, CTA during animation and no frame clock after 850ms. |
| G11 — source SHA propagation | Current photoSourceIdentity is imageId + source + dimensions; it does not prove normalized source bytes. | Compute SHA-256 once when the EXIF-normalized source file is persisted, before inference begins, and propagate it unchanged through the existing image/session handoff; never wait until T0, recompute from a rendered frame or infer it from imageId. |

## 8. Acceptance gates and required evidence

All evidence must be bound to the exact PR HEAD and resulting Main SHA. The final contract passing static validation does not mean Android runtime passed.

1. **Contract consistency:** validate JSON schema/required fields; verify 2800 + 850 = 3650, no extra pre-navigation wait, RR09 boundaries and Chinese-only title.
2. **Geometry unit tests:** q(0)=0, q(100)=0.5, q(200)=1; same-source transform; clip and scale midpoint; R1 transform parity with Hero; reference Mode B bounds within 2dp.
3. **Mode tests:** valid BBox with every safe-crop profile; source-edge compensation; infeasible crop fallback; missing/invalid/stale BBox; EXIF rotations; portrait and landscape photos; NO_FISH and IMAGE_QUALITY.
4. **Trust tests:** TRUSTED_MASK contour; TRUSTED_BBOX_ONLY regional glow with no rectangle; invalid/stale geometry with no spatial trace; no manual fixture contour accepted as production Mask.
5. **RR09 tests:** deterministic boundaries 0/200/380/550/680/850ms; HIGH-only one-shot; eligible HIGH fallback keeps the same title timing; Medium/Low/error/historical routes play no RR09 transition; CTA/save/edit/back work at T0; no active frame clock after 850ms.
6. **Native runtime:** exact-head build, unit tests, Android test compilation, lint, API 28 runtime and representative physical-device recording. Matrix: 320×480, 360×640, 390×844, 412×915, 430×932 and 800×360; font scales 1.0/1.3/1.5 where applicable; portrait/landscape source; EXIF 0/90/180/270; both photo modes; reduced motion.
7. **Evidence package:** exact source SHA, workflow/run IDs, test report, R0/R0.5/R1/R2/R3/R4/R5 captures, continuous recording, measured bounds/transform error, original photo SHA and permission provenance, APK SHA-256.
8. **Product-owner visual acceptance:** compare the actual installed APK against the frozen contract. CI, static screenshots, a Web demo or source inspection alone cannot satisfy this gate.

**Current status:** contract proposal only; Android implementation gaps remain open; runtime NOT_RUN; product-owner visual acceptance PENDING. No PASS is implied by this document.
