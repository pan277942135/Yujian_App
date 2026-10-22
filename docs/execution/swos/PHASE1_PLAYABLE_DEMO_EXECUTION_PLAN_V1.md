# SWOS Phase 1 — Playable Demo Execution Plan V1

**Status:** `WAITING_FOR_PHASE1_AUTHORIZATION`
**Depends on:** PR #186 contract adoption on `main`, current approved SWOS V1 / Processing V1.3 / Page V3.2 / continuity V1 / RR09 V3 refs, and a confirmed review photo/geometry bundle.
**Phase 0 disposition:** Planning only. No demo, Android code, Pages deployment, API 28 run or APK was started.

## Entry gates

1. PR #186 is reviewed and its control-plane contracts are adopted on `main`; the implementation branch starts from that adopted SHA.
2. Product/design review confirms the SWOS water-light direction and the static Result title placement under the linked contracts.
3. The input bundle in [`Phase1_SWOS_Playable_Demo_Input_Contract_V1.md`](../../../design/pages/recognition/phase1/Phase1_SWOS_Playable_Demo_Input_Contract_V1.md) is available: registered original photo set, transform/`photoContentRect`, real detector/classifier trace, and provenance-tagged subject geometry.
4. Missing production contour data is explicitly represented as BBox-only or `BLOCKED`; manual reference assets remain `REVIEW_PROXY_NOT_PRODUCTION`.

## Work packages

| Package | Work | Evidence / gate |
|---|---|---|
| P1-A · Web playable prototype | Build a browser-reviewable SWOS timeline with one monotonic clock, the four Processing internal phases, real-event gates, Reduce Motion, play/pause/replay/seek/speed, layer toggles, viewport presets, and Processing → Result continuity. Keep photo, water layers and trusted subject geometry in one `RecognitionImageTransform` / `photoContentRect`. | Exact-SHA Chromium playback; phase keyframes and recording; all registered photo fixtures; provenance label on every geometry input. `WEB_PROTOTYPE_PASS` is separate from visual approval. |
| P1-B · Optical calibration review | Calibrate water color, alpha, blur, width, caustic contrast and ripple scale against the registered multi-photo matrix. Preserve photo details and irregularity; inspect bright water, dark water, edge subjects, crop/letterbox, multiple fish and quality failures. | Review board with source SHA, renderer SHA, per-photo keyframes and a human decision. Only approved values may be frozen in a later versioned contract. |
| P1-C · Fish Focus and continuity review | Exercise trusted mask, valid BBox-only, invalid/missing geometry and stale-image cases. Transfer only the trusted residual-gold source to the Chinese-title region on eligible HIGH results. | Separate Production / Review-only / Blocked evidence; verify non-success routes, one-shot behavior, first-presented-Result-frame T0, CTA/save/edit/back availability and Reduce Motion. |
| P1-D · Android/API 28 implementation | After web direction and contract inputs are accepted, update the exact Android files listed in [`ANDROID_STAGE1_IMPLEMENTATION_GAP_REPORT_V1.md`](ANDROID_STAGE1_IMPLEMENTATION_GAP_REPORT_V1.md): runtime projection, assets/controller/tests, Processing renderer, handoff and RR09 title/clock. Reuse current recognition output and trust gates; do not alter model thresholds, confidence semantics or routing. | Separate JVM, API 28 device/runtime, accessibility, visual, lifecycle/performance and APK-provenance gates on one exact implementation SHA. Browser success cannot satisfy any Android gate. |

## Execution order and stop rules

1. Re-read live `main`, the adopted contract SHA and current admission rules; synchronize the implementation branch only after main-first adoption.
2. Verify each source asset and geometry input before building the Web prototype. If identity, transform or production provenance is missing, keep that case blocked and continue only with clearly labelled review fixtures.
3. Implement P1-A and collect browser evidence. Do not make a screenshot-only page look like a playable prototype; verify pause, seek and layer synchronization in a real browser.
4. Review P1-B against the full photo matrix. If the field only works on one image, keep calibration open; do not freeze single-photo values.
5. Review P1-C and the cross-page handoff. Do not claim Production Fish Focus without trusted production geometry.
6. Start P1-D only after the Web/visual direction and source contracts are accepted. Android coding, API 28 testing, APK construction and deployment are outside the present Phase 0 authorization.

## Required final evidence bundle

- Contract and implementation source SHAs, photo/geometry manifests and SHA-256 values.
- Chromium run ID, logs, screenshots, timeline recording and responsive results.
- Separate visual-review decision with named states `PASS`, `REVIEW_REQUIRED`, `BLOCKED` or `NOT_RUN`.
- Android run ID/device/API, frame/performance/lifecycle evidence and APK SHA only after P1-D is authorized and executed.
- A report that distinguishes `WEB_PROTOTYPE_PASS`, `VISUAL_REVIEW_REQUIRED`, `ANDROID_NOT_RUN` and `APK_NOT_BUILT` as separate states.

**Phase 1 is not started by this plan.** Wait for PR #186 adoption and explicit Phase 1 entry; do not begin Android implementation from this document alone.
