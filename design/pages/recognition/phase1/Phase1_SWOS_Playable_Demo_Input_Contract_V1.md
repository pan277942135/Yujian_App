# Phase 1 Input Contract — SWOS Playable Demo V1

**Status:** `PROPOSED_FOR_MAIN_ADOPTION`
**Scope:** Required inputs and evidence boundary for a future playable Web/Android SWOS demo.
**Phase 0 disposition:** Contract only; no demo is implemented here.

## Required input bundle

1. A registered set of original real catch photos with path, SHA-256, dimensions, EXIF/orientation, source identity and license/permission status. Include bright-sky, dark-water, fish near each photo edge, more than one fish, crop/letterbox and image-quality cases.
2. The shared `RecognitionImageTransform`/`photoContentRect` output and resolved FIT/CROP mode for each photo; no demo-local geometry guess.
3. Actual detector BBox/confidence and classifier result traces from the existing recognition pipeline. Do not alter model, threshold, confidence meaning or routing.
4. Subject geometry output plus provenance and trust state. Production detector output, preview-engine visual geometry and manual reference annotations must be distinguishable. Manual contour/alpha is `REVIEW_PROXY_NOT_PRODUCTION` and cannot pass a Production Fish Focus gate.
5. The SWOS V1, Processing Motion V1.3, Processing Page V3.2, Processing→Result Continuity V1, RR09 V3 and Hero Media V1 contracts.
6. A normalized monotonic timeline input that is presentation-only and separately gated by actual pipeline events; it may not simulate successful recognition.

## Required demo controls and review evidence

The demo must expose play/pause/replay, synchronized timeline/frame inspection, playback speed, state selection, layer toggles, viewport presets and Reduce Motion. It must distinguish Production, review proxy and Blocked geometry inputs.

Capture exact-SHA browser and API 28 evidence separately. Browser/demo success is not Android runtime success. API 28 evidence must measure frame timing, resource use, low-performance degradation and lifecycle cleanup on the supported device matrix.

## Prohibited demo claims

- Do not use the 3650 ms nominal sum as an inference countdown or force a result at 3650 ms.
- Do not present a hand-drawn contour or BBox as production segmentation.
- Do not call a static screenshot a playable demo, a Chromium pass an Android pass, or a prototype color token a frozen production parameter.
- Do not change frozen photo assets, state/business decisions, thresholds, routing, CTA semantics or model inputs.
