# Recognition Runtime Contract V1.1

Status: **FROZEN**

## Production state machine

`CAPTURED → DETECTING → OUTLINE → CLASSIFYING → RESULT / FAILURE`

The source state is the real recognition pipeline. Presentation code may hold a real phase for minimum perceptual duration, but may never invent a later phase.

## Separation of responsibilities

Recognition visual code:
- MAY render ambient field, status copy, halo/contour and transition fade;
- MAY hold a completed result for the frozen visual story;
- MUST NOT change detector, crop, classifier, confidence, quality gate or result routing;
- MUST NOT require segmentation for classification.

Detector / classifier:
- remain the sole source of semantic recognition state and prediction.

Subject segmentation:
- is visual-only;
- failure degrades contour A→B/C;
- never blocks recognition result.

## Frozen copy

| Phase | Title | Subtitle |
|---|---|---|
| CAPTURED | 正在准备识别 | AI 已获取这张照片 |
| DETECTING | 正在理解这张照片 | 寻找这次鱼获的线索 |
| OUTLINE | 已定位到鱼体 | 正在分析这次鱼获 |
| CLASSIFYING | 正在认识这条鱼 | 分析鱼体特征 |
| FAILURE | 识别没有完成 | 请重新拍摄或选择照片 |

RESULT is routing-only and does not display a fifth processing status card.

## Real-photo invariant

- Processing starts on the actual selected/captured bitmap.
- The bitmap is opaque from the first Recognition frame.
- Visual overlays are additive only.
- Gallery/camera handoff must not flash a previous stale image.

## Fish focus availability

- DETECTING: focus OFF.
- OUTLINE / CLASSIFYING: focus requires a real primary bbox.
- Subject contour can arrive asynchronously.
- Before contour is ready, bbox halo is acceptable.
- Once a valid contour is ready, Level A may replace Level B without changing phase.

## Timing

Authority: `processing/motion/Recognition_Processing_Motion_Spec_V1_1.md`.

Nominal: 350 + 600 + 600 + 1250 = 2800ms.
No V1 fast-result compression.

## Test hooks

- `phaseOverride` and `visualClockOverrideMs` are deterministic visual-test controls only.
- `onVisualPhasePresented` is read-only timing instrumentation.
- Production semantics must be unchanged when these are absent.

## Terminal states

High / Medium / Low / No Fish / Image Quality must match the frozen PNG references.
Technical failure uses generic user-safe copy and must not expose:
- .onnx / .tflite filenames;
- stack traces;
- Java exception names;
- filesystem paths;
- null/undefined/debug internals.
