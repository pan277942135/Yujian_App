# AI Studio Perimeter Shader Port V1 — Candidate Evidence

**Task:** `YUJIAN_ANDROID_AISTUDIO_PERIMETER_SHADER_PORT_V1`
**State:** Draft implementation; source-browser and physical API 28 visual gates remain `NOT_RUN`.
**AI Studio source SHA:** `794e7f2698525761bbb2f3e5d775b5dae8d7a25a`
**Initial Android implementation / CI-tested source SHA:** `3ad7b0257ec67807f4d5fe124ea98ea7a6a8624a`
**PR:** [#244](https://github.com/pan277942135/Yujian_App/pull/244)
**Android CI run:** [40669933101](https://github.com/pan277942135/Yujian_App/actions/runs/40669933101)

The latest Android implementation SHA is the current non-document PR head. A follow-up initializes the GLSurfaceView paused and lets the existing LifecycleOwner resume it only when the recognition page reaches `RESUMED`; the initial baseline results below remain historical for that follow-up. The current exact-head CI status and source SHA are maintained in the PR and `harness/build-log.md`.

## Candidate changes

| File | Change |
|---|---|
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderOverlay.kt` | GLES2 photo compositor, perimeter-only GLSL ES 1.00 shader, 16 seeded perimeter nodes, source-alpha and Overlay composition, lifecycle-bound rendering. |
| `app/src/main/java/com/yujian/ai/ui/recognition/RecognitionPerimeterShaderMath.kt` | Source path sampler, linear reveal/time mapping, capped-DPR coordinate mapping, and deterministic two-ended node placement. |
| `app/src/main/java/com/yujian/ai/ui/screens/RecognizingScreen.kt` | Binds the compositor to the existing PhotoSession Bitmap, `RecognitionImageTransform`, and `processingElapsedMs`; removes the old ambient field, visible fish-focus layer, and photo shockwave from this photo layer. Recognition pipeline and focus-data calculations remain in place. |
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
| Source DPR / CSS / backing size | Not captured | Not available; the current page integration uses an identity mapping marked unmeasured and unverified |
| Android photo bounds / render size | Browser reference unavailable | GLES runtime log emits `photoContentRect`, viewport and scale; no device log captured |

No fixed-unit visual equivalence is claimed until the source browser reports DPR, CSS dimensions, and backing dimensions. `glowSize=48` is not converted to dp.

## Composition semantics

The source perimeter fragment emits `finalRgb` and `finalAlpha = a`. With WebGL `SRC_ALPHA / ONE_MINUS_SRC_ALPHA` over transparent, the drawing buffer contains premultiplied RGB `finalRgb * a` and alpha `a²`. The Android compositor carries that intermediate alpha through the source Overlay blend over the actual PhotoSession photo rather than treating `a` as the final photo-composite alpha.

The 16-node accent pass first screens each halo/core into a transparent accumulator, including the Canvas `globalAlpha` carry-over, then applies Overlay over the perimeter-composited photo. The bottom-center flourish is omitted. The source's undefined reverse smoothstep is translated only as `1.0 - smoothstep(0.3, 2.8, dBox)`.

## Build and test results

| Gate | Status | Evidence |
|---|---|---|
| Control-plane Admission | `PASS` | Exact candidate run `40669933102`; six layers and 733 synchronized paths. |
| Android unit tests | `PASS` | Android CI run `40669933101`, exact Android implementation SHA above. |
| Camera runtime harness contract tests | `PASS` | Android CI run `40669933101`. |
| Debug APK build | `PASS` | Android CI run `40669933101`, `:app:assembleDebug`. |
| AndroidTest APK compile | `PASS` | Android CI run `40669933101`, `:app:assembleDebugAndroidTest`. This is compilation only, not device execution. |
| Packaged launcher/recognition assets and model trace | `PASS` | Android CI run `40669933101`. |
| Android lint | `PASS` | Android CI run `40669933101`, `:app:lintDebug`. |
| Initial baseline runtime APK artifact | `AVAILABLE` | Artifact ID `12807628530`, name `YuJian-runtime-apks-667f0601e619e6de9da0501df62660fd1c80dc60-MODEL_CROP_M1_v0.2`. The artifact ZIP digest is not an APK digest. |
| Initial baseline `app-debug.apk` SHA-256 | `VERIFIED` | `1fe5b10a47478c00876fb26c159cc9470df5d52212aa7e5ecf6632aa46eff6b8`; matched both the artifact's `SHA256SUMS` and an independent streamed hash of the APK entry. |
| Local Gradle build/tests | `NOT_RUN` | The local workspace has no Gradle wrapper or Gradle executable; exact-source Android CI provided the build and test results above. |

## Runtime and visual gates

| Gate | Status | Evidence |
|---|---|---|
| GLES compile/link log and runtime backend | `NOT_RUN` | Android build CI does not instantiate the recognition renderer. Candidate logs GLES version, renderer, Shader compile/link, photo bounds, and coordinate scale when executed. |
| Source browser DPR / CSS / backing dimensions | `NOT_RUN` | No reachable exact-source AI Studio preview is available. |
| Coordinate mapping and Overlay alpha comparison | `NOT_RUN` | Candidate formulas are implemented, but reference geometry and source-browser pixels are unavailable. |
| Seven-timestamp comparison and complete recording | `NOT_RUN` | Requires a usable source browser and Android runtime capture. |
| API 28 physical-device installation/runtime | `NOT_RUN` | No attached physical API 28 device. The PR Android runtime job was skipped; emulator execution would not satisfy the physical-device gate. |
| Lifecycle runtime cases | `NOT_RUN` | Session-key replacement, page resume/pause, disposal, and terminal phase handling are implemented; photo switch, cancel/exit, retry, success, and failure still need device capture. |
| Detector / Classifier output comparison | `NOT_RUN` | The implementation diff does not edit the recognition pipeline or model input path; no same-input runtime trace has been captured. |

The lifecycle follow-up requires a fresh exact-head Android CI run before its APK and test evidence can replace the initial baseline evidence above.

## Non-app workflow note

On the initial PR candidate, Design Manager Pages run `40669933158` failed its design-only changed-file scope gate because application code was included in a PR that also added this evidence report under `design/`. The report has been moved to `harness/evidence/`; the resulting PR file diff contains no `design/**` changes. The failure did not execute or evaluate the Android renderer.

**Acceptance:** Do not mark visual `PASS`, freeze, or task completion until the exact Android source SHA and APK are tested on an API 28 physical device and compared with the exact-source browser at 0, 300, 600, 900, 1200, 1800, and 2600 ms.
