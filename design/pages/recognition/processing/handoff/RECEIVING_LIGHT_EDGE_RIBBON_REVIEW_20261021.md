# Receiving Light Edge-Ribbon Review — 2026-10-21

## Decision

**Status: `REVIEW_REQUIRED`; no visual PASS is claimed.** The browser capture failure caused by selecting the first (sometimes blank) asynchronous canvas draw has been addressed in the capture harness. A real Chromium run now completes and emits the baseline/candidate PNG evidence. The candidate still requires human visual judgment before any dynamic-motion or Android work.

## Exact evidence

- Repository: `pan277942135/Yujian_App`
- PR: [#166](https://github.com/pan277942135/Yujian_App/pull/166), Draft / Open / Unmerged
- Capture run: [#39563381466](https://github.com/pan277942135/Yujian_App/actions/runs/39563381466) — `build-and-test: SUCCESS`, Pages deploy: `SKIPPED`
- Tested source SHA: `be6f672eced29ac7d570302ca0a874e7f397f3ce`
- Capture implementation commit: `b6094a68690f5ef6cb76b1a7b270bde9f4a24ed9`
- Acceptance Center SHA: `6bac75e63d8ca2448d33d5dbd6d1a71e9a548188`
- Source photo SHA-256: `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`
- Evidence artifact: [EdgeRibbon Review PNGs + report (artifact 12328842460)](https://github.com/pan277942135/Yujian_App/actions/runs/39563381466/artifacts/12328842460)

## Capture-harness root cause and fix

The old harness accepted the first intercepted canvas draw as the Receiving Light layer. The renderer loads the SVG asynchronously, so the first composite can legitimately be transparent before the `onReady` redraw. The harness now records alpha coverage for each intercepted draw, waits for a draw with nonzero alpha, selects that draw, and includes capture metadata in timeout diagnostics. The nonzero-alpha acceptance assertion remains intact.

## Geometry candidate and measured checks

The Review-only candidate replaces the path-stroke shape with eight photo-edge-anchored perimeter segments split around the frozen Quiet Gaps. It uses inward-normal gradients, tapered endpoints and a local Gaussian feather. It is not a production renderer change.

| Check | Result |
| --- | --- |
| Viewports | 390×844, 320×640, 1080×1920 captured |
| Candidate peak Alpha | 17/255 in all three viewports |
| P1 base Alpha | 0.07, unchanged |
| P1 output cap | 17/255, unchanged |
| Depth | 18%, unchanged |
| Candidate-vs-baseline mean RGB delta | 0.863688 (390×844 composite sampling) |
| Frozen Quiet Gaps / photo mapping / source-over | Assertions passed |
| Visual pass | **Not claimed** |
| Android Runtime / API 28 / APK | **NOT RUN** |

Pixel change and successful capture prove the candidate rendered; they do not prove the requested visual effect. The photo composite must be reviewed for broad, soft blue/gold receiving illumination rather than a visible border or thin arcs.

## Deployment and CI safety follow-up

- Run [#39562911009](https://github.com/pan277942135/Yujian_App/actions/runs/39562911009) unintentionally entered the Pages deploy job because the review commit did not include the workflow's `[no-pages-deploy]` opt-out. The deploy action succeeded, but live verification failed with HTTP 404 on the expected evidence route.
- The previous verified Design Manager Pages release was restored by rerunning the deploy job from [run #39449784360, attempt 2](https://github.com/pan277942135/Yujian_App/actions/runs/39449784360). Verification passed for published SHA `ad35dc9fc533f13e9d07efcb2d91a470c3bca18d`, original asset hashes, and browser routes.
- Workflow guard commit `be6f672eced29ac7d570302ca0a874e7f397f3ce` now requires explicit `[allow-pages-deploy]` opt-in; the validation run above skipped deployment.
- Android workflow ignore paths were fixed in commit `49ceff9703d3c1bddd8f3aa018814ef660ad22f7` so web-only Receiving Light capture scripts do not trigger Android/APK work. This report commit is marked `[no-android-runtime]`; it is documentation only and does not authorize Android execution.

## Next decision

Review the linked real PNG artifact first. If the edge ribbon still reads as a frame or uniform strip, the next candidate should introduce **non-uniform along-edge field strength and variable inward depth** while retaining P1 Alpha/depth caps, frozen Quiet Gaps, photo coordinates, palette, and source-over. Do not continue Alpha tuning, deploy, merge PR #166, or start Android/API 28/APK without the required visual review.
