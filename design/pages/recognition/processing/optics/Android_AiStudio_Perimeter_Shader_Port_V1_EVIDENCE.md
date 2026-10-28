# AI Studio Perimeter Shader Port V1 — Candidate Evidence

**Task:** `YUJIAN_ANDROID_AISTUDIO_PERIMETER_SHADER_PORT_V1`
**State:** Implementation candidate; API 28 device and source-browser visual gates remain `NOT_RUN`.
**Source authority:** `pan277942135/demo--Image-light-effect@794e7f2698525761bbb2f3e5d775b5dae8d7a25a`
**Android base:** `pan277942135/Yujian_App@632cada103bf022adeb873bd6e66464555b509e4`

## Candidate changes

| File | Change |
|---|---|
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt` | GLES2 photo compositor, perimeter-only GLSL ES 1.00 shader, 16 seeded perimeter nodes, source-alpha and Overlay composition, lifecycle-bound rendering. |
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMath.kt` | Source path sampler, linear reveal/time mapping, capped-DPR coordinate mapping, and deterministic two-ended node placement. |
| `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt` | Binds the compositor to the existing PhotoSession Bitmap, `RecognitionImageTransform`, and `processingElapsedMs`; removes the old ambient field, fish-focus visible layer, and photo shockwave from this photo layer. Recognition pipeline and focus-data calculations remain in place. |
| `app/src/test/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMathTest.kt` | Boundary coverage for the seven required timestamps, coordinate scaling, source path geometry, and the sixteen-node reveal. |

## Frozen parameters and coordinate mapping

| Parameter | AI Studio source | Android candidate |
|---|---:|---:|
| Spread | 1200 ms | 1200 ms; linear shared `processingElapsedMs` |
| Intensity / speed | 1.50 / 1.00 | 1.50 / 1.00; speed is applied once to `u_time` |
| Margin ratio | 0.015 | 0.015 of photo bounds |
| Glow size | 48 backing-store Shader units | 48 source units transformed using `targetPhysicalPx / measuredSourceBackingScale`; measured scale must match `min(sourceDPR, 2)` within tolerance |
| Colors | `#FEF08A`, `#38BDF8`, `#2DD4BF` | Same RGB constants |
| Node path | 280 CSS-coordinate samples | Same 280 samples; source CSS coordinates map uniformly to Android photo pixels |
| Source DPR / CSS / backing size | Not captured | Not available; renderer currently marks its identity mapping as unmeasured and unverified |
| Android photo bounds / render size | Browser reference unavailable | Runtime log emits `photoContentRect`, viewport and scale; not captured yet |

No fixed-unit visual equivalence is claimed until the source browser reports DPR, CSS dimensions, and backing dimensions. `glowSize=48` is not converted to dp.

## Composition semantics

The source perimeter fragment emits `finalRgb` and `finalAlpha = a`. With WebGL `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` over transparent, the drawing buffer contains premultiplied RGB `finalRgb * a` and alpha `a²`. The Android compositor carries that intermediate alpha through the source Overlay blend over the actual PhotoSession photo rather than treating `a` as the final photo-composite alpha.

The 16-node accent pass first screens each halo/core into a transparent accumulator, including the Canvas `globalAlpha` carry-over, then applies Overlay over the perimeter-composited photo. The bottom-center flourish is omitted. The source's undefined reverse smoothstep is translated only as `1.0 - smoothstep(0.3, 2.8, dBox)`.

## Validation state

| Gate | Status | Evidence |
|---|---|---|
| Source provenance | `READ` | Exact source checkout SHA above; `shaders.ts` and `fish-catch-caustics.ts` inspected. |
| Control-plane Admission | `PASS` | Validated at Android base `632cada103bf022adeb873bd6e66464555b509e4`; six layers and 733 synchronized paths. |
| Local build / unit tests / AndroidTest compile / lint | `NOT_RUN` | This environment has no Gradle executable or wrapper, Kotlin compiler, Android SDK, or `adb`; exact-head Android CI is required. |
| GLES compile/link logs and runtime backend | `NOT_RUN` | Requires Android execution. Candidate logs GLES version, renderer, shader compile/link, and photo dimensions. |
| Source browser DPR / CSS / backing dimensions | `NOT_RUN` | No reachable AI Studio source preview is available. |
| Seven-timestamp comparison and full recording | `NOT_RUN` | Requires a usable source browser and Android device/runner. |
| API 28 physical-device install and runtime | `NOT_RUN` | No attached physical API 28 device. An emulator alone cannot satisfy the requested final gate. |
| APK SHA-256 | `NOT_RUN` | Requires exact-head Android CI artifact. |
| Lifecycle runtime cases | `NOT_RUN` | Source implements session-key replacement, page resume/pause, dispose, and terminal phase handling; photo switch, cancel/exit, retry, success, and failure still need device capture. |
| Detector / Classifier output comparison | `NOT_RUN` | The implementation diff does not edit the recognition pipeline or model input path; no same-input runtime trace has been captured. |

**Acceptance:** Do not mark visual `PASS`, freeze, or task completion until the exact Android source SHA and APK are tested on an API 28 physical device and compared with the exact-source browser at 0, 300, 600, 900, 1200, 1800, and 2600 ms.
