# Android Effect 01 + Upward Caustic Surge + Photo Shockwave Integration V1

**Contract ID:** `YUJIAN-ANDROID-EFFECT01-UPWARD-SCAN-SHAKE-V1`  
**Governance status:** `ACTIVE_CLOSURE`  
**Implementation values:** `FROZEN`  
**Runtime:** `NOT_VERIFIED`  
**Product-owner visual acceptance:** `CANDIDATE_NOT_VISUALLY_APPROVED`

## 1. Authority and scope

This is an Android implementation supplement for the existing recognition Processing scene. It does not replace or amend:

- [SWOS Optical Contract V1](SWOS_Optical_Contract_V1.md), which remains authoritative for optical layer meaning, photo-space mapping, trust boundaries, API 28 and reduced-motion behavior.
- [Recognition Processing Motion Spec V1.3](../motion/Recognition_Processing_Motion_Spec_V1_3.md), which remains authoritative for event-gated Processing pacing and the Processing-to-Result handoff.
- The frozen recognition state machine, detector/classifier thresholds, PhotoSession identity, image bytes, EXIF normalization, crop geometry, result routing, R0–R5 or P1–P7 semantics.

The three rendering responsibilities are separate but share the current Processing visual-clock start and current PhotoSession:

1. Effect 01 base peripheral water-light field.
2. Upward Caustic Surge, an optical water-refraction wave.
3. One bounded Photo Shockwave transform.

The internal implementation term “scan” does not authorize a scanner visual. The wave must read as continuous natural caustic travel, never as radar, targeting, a system scan indicator, a mechanical sweep line, or a new user-visible state. No HUD, reticle, closed neon frame, full-photo flash, or full-screen wash is allowed.

## 2. Effect 01 base field

### 2.1 Frozen parameter values

| Parameter | Value |
|---|---:|
| `sceneId` | `fish-catch-recognition` |
| `displayName` | `鱼获识别` |
| `spreadDurationMs` | `1200` |
| `intensity` | `1.50` |
| `speed` | `1.00` |
| `marginRatio` | `0.060` |
| `glowSize` | `36` |
| `goldColor` | `#FEF08A` |
| `cyanColor` | `#38BDF8` |
| `greenColor` | `#2DD4BF` |
| `bottomMaskStrength` | `1.00` |
| `blendRatio` | `0.75` |
| `blendMode` | `overlay` |

No implementation or calibration step may change these numeric values, colors or identifiers.

### 2.2 Source provenance and native mapping

At audit base `874beafb9cb865e2577f72888a380ff5aee9c1a0`, repository search found no `createFishCatchOverlay`, `fish-catch-caustics`, or `fish-catch-recognition` renderer source. Open Android PR #183 at head `9888ab37abd6fbd92dc0362d32e4bba3a487a183` changes workflow/runtime-test files only and does not carry the renderer. The uploaded task specification contains parameter/behavior requirements, not the original renderer source. Therefore the implementation must be described as a **native equivalent**, not as a direct port, original-algorithm reuse, or pixel-perfect recreation unless the original source and its provenance are later supplied and verified.

The original implementation’s private unit semantics cannot be inferred from the parameter names. The native equivalent shall retain each frozen numeric value and use this explicit Android projection, which is an implementation mapping rather than a claim about the unavailable source:

- `marginRatio=0.060`: edge-field depth is 6% of the shorter side of the displayed `photoContentRect`; geometry is clipped to that same rect.
- `glowSize=36`: native blur radius is 36 Android logical dp, density-converted at draw time.
- `intensity=1.50`: base-field strength multiplier; it must not be reused to drive the surge.
- `speed=1.00`: native-field time multiplier against the existing elapsed-time clock.
- `spreadDurationMs=1200`: base-field spatial spread window; it does not delay navigation or start a second timer.
- `bottomMaskStrength=1.00`: full application of the native bottom-up alpha mask to the base field only; it never modifies the source photo.
- `blendRatio=0.75`: fixed opacity weight for the base field before compositing.
- `blendMode=overlay`: API 28-compatible overlay compositing for the isolated optical group; verify on the target Android runtime.

All other geometry uses the actual displayed photo content rectangle after EXIF normalization and the existing FIT/CROP transform. The field may have uneven, broken edge activity, but it must not form a closed rectangular outline. The photo itself remains immutable and visually dominant.

## 3. Upward Caustic Surge

The surge is an independent transient refraction layer. It may reveal the existing Effect 01 output by spatial activation; it must not change any Effect 01 parameter.

Let `t` be elapsed milliseconds from the existing Processing visual-clock start, `H` be the displayed photo height, and `p = clamp(t / 1100, 0, 1)`. Define `e(p)=3p²-2p³` and the principal wave center `yCenter=H(1-e(p))`, with `y=0` at the photo top and `y=H` at the bottom.

| Property | Frozen value |
|---|---:|
| Duration | 1100 ms |
| Direction | Bottom to top |
| Maximum arc deviation | ±2.5% of displayed photo height |
| Main wave-body width | 10% of displayed photo height |
| Bright crest width | 1.5% of displayed photo height |
| Soft trailing field | 8% of displayed photo height |
| Maximum surge composite opacity | 22% |
| Palette | `#FEF08A`, `#38BDF8`, `#2DD4BF` |

The crest is a deterministic smooth curve across the photo width, with a fixed arc bounded by ±2.5% H. It must not be a thick straight bar, a random per-frame path, or several independent moving points. Gold is localized near the crest, blue supports the leading refraction edge, and teal supports the broad trailing light; the three colors must blend continuously and remain under the 22% surge-layer opacity cap.

The edge-activation mask uses the same elapsed time and activates the bottom first, both side edges progressively from bottom to top, and the upper edge last. It applies only to Effect 01's rendered output. At 1100 ms the activation is complete and held; the base field continues its original spread to 1200 ms without restarting.

## 4. Photo Shockwave

One shockwave starts at elapsed time 850 ms and ends at 1110 ms. It transforms only the fixed photo-plus-optical-layer visual group inside its existing clip; layout measurements, surrounding UI, photo data and recognition state remain unchanged.

| Property | Frozen value |
|---|---:|
| Trigger | 850 ms |
| Duration | 260 ms |
| First upward movement | At most −3 dp during 0–60 ms |
| Single reverse rebound | At most +2 dp during 60–120 ms |
| Rotation | At most ±0.15° |
| Scale peak | 1.004× |
| Settle | Exact original translation, rotation and 1.000× scale by 260 ms |

Use a deterministic, continuous easing curve: smooth rise, one restrained reverse rebound, then damped settling. No second impact, random vibration, layout resize or residual transform is permitted. The same start timestamp drives this transform and both optical layers. A new PhotoSession resets the transform; cancellation, retry, rapid photo replacement and page disposal stop or replace the prior session’s animation without leaving a running loop.

## 5. Timing and state ownership

The existing Processing visual clock and real pipeline events remain authoritative. This supplement does not create a product state, delay, independent animation clock, inference request or completion event.

- Processing keeps its existing 2800 ms **minimum visual pacing** and phase/event gates.
- RR09 keeps its existing 850 ms motion after the first actually presented Result frame.
- 3650 ms is the nominal combined optical duration only when the real result is ready for handoff at the 2800 ms Processing boundary. Slow inference may extend the actual end-to-end time; timers must never synthesize recognition success or bypass the existing gates.
- P1–P7, R0–R5 and the existing 2600–2800 ms water release remain unchanged.
- The surge is complete at 1100 ms; Effect 01's original spread completes at 1200 ms; the shockwave settles by 1110 ms. Nothing is restarted after completion.

## 6. Android platform, lifecycle and performance

- Use the existing Compose/native drawing owner and current image/session path. Do not add a WebView, second PhotoSession, second Processing state machine, or duplicate renderer loop.
- Support Android API 28. Do not depend on `RuntimeShader` or Android 13-only APIs.
- Use the existing EXIF-normalized photo geometry and `photoContentRect`; map dp, density, Crop/Fit and orientation once so photo and effect stay aligned.
- Cache reusable paint/path/gradient resources. Do not allocate a full-size bitmap or perform a full-image CPU transform each frame; do not run model inference from the animation.
- On reduced motion, render a stable equivalent composition while preserving business state and real-event gates.
- On cancellation, route exit or session change, stop animation work and release resources.

## 7. Engineering evidence and owner acceptance

The Work gate requires exact-source evidence for implementation, API 28 compatibility, relevant unit/instrumentation tests, build, lint, targeted runtime where available, APK provenance and lifecycle behavior. A missing physical device must be recorded as `NOT_RUN`, not PASS.

Product-owner visual acceptance is a separate human decision. Until that review is recorded, visual status remains `CANDIDATE_NOT_VISUALLY_APPROVED`; successful compilation or instrumentation cannot promote it.

The original source algorithm remains `NOT_FOUND` at the audited baseline. Any later source recovery requires renewed license/provenance review before describing the native renderer as a port.
