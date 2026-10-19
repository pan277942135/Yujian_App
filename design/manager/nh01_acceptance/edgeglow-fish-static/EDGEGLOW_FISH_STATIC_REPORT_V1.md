# EdgeGlowView Fish Static Prototype V1

**Task:** `YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1`  
**PR:** #166 · `design/recognition-processing-handoff-p0`  
**Scope:** Static browser rendering study. No Android product code or recognition behavior was changed.

## Gate status

| Gate | Status | Evidence and limit |
|---|---|---|
| `STATIC_FISH_CONTOUR_RENDERING` | `PASS` | The selected registered photo and manual Path load on the public page in original-photo coordinates. The contour is visibly toggleable and the three canvases share 1152×1536 FIT/no-crop geometry. This pass is for this manual fixture only. |
| `EDGEGLOW_VISUAL_QUALITY` | `REVIEW_REQUIRED` | Bloom, core and static path-following comet render on the fish. A human still needs to judge edge tracing, texture retention and optical balance. Automated/source checks are not a visual-quality pass. |
| `CLOUD_ACCEPTANCE_CENTER` | `PENDING_RECOVERY` | A later full-site Pages deployment replaced the composed artifact and the EdgeGlow child route returned 404. A fresh PR #166 composition/deployment is required before this gate can return to PASS. |
| `ANDROID_RUNTIME` | `NOT_RUN` | Canvas 2D is a Web adaptation, not Android API 28 rendering. |

The contour remains `MANUAL_REFERENCE_CONTOUR`, not a detector result, production Mask, or `FishSubjectResult`. Overall M1/Figma/Android gates are unaffected and are not marked PASS.

## Live route and source identity

- Public HTTPS route: [EdgeGlowView Fish Static Prototype](https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/).
- It is linked under **Recognition Processing → EdgeGlowView Fish Static Prototype** in the existing NH01 Acceptance Center. The center was composed from branch `design/nh01-cloud-structured-parity-v1`, SHA `06fd4a5e048bcfd4e963c84303efebc932f98982`; no Center source file was changed.
- PR source used by successful push/deploy run #29: `3bb85e58bad51c550d8b9bfe5d8d92697c296709`. Live page badge and deployed `build-info.json` identify this SHA and the Center SHA.
- Run #29 passed the exact-PR checkout, center branch/SHA guard, composition, renderer/source audit, Pages upload, deploy and live HTTPS/photo identity check. Source audit artifact ID: `12179865519`; live smoke artifact ID: `12178942078`.
- The current PR head after this report update may advance by a documentation-only commit; the renderer source and tested route remain unchanged by the report edit.

### Pages replacement incident — 2026-10-19

- The user's screenshot showed GitHub Pages' actual `404 File not found`; a cold reload of the exact public route reproduced it. The NH01 Acceptance Center parent route still returned HTTP 200.
- GitHub's deployment listing shows the composed EdgeGlow deployment from PR SHA `36ac340c81aef77df731f1dd6ddb7fec045fcf0f` was created at `2026-10-19T10:17:16Z` (deployment `7171168058`). Two minutes later, a full-site deployment from the NH01 source branch at SHA `06fd4a5e048bcfd4e963c84303efebc932f98982` was created at `2026-10-19T10:19:58Z` (deployment `7171216398`).
- Both workflows publish to the repository's single GitHub Pages site. The later NH01 artifact does not contain PR #166's child route, so it replaced the composed artifact and caused the 404. This is a deployment-order/source-composition issue, not a malformed URL or a missing route in PR #166.
- Recovery is to rerun the existing PR #166 composition and Pages deployment after the NH01 deployment, then cold-load and verify the child URL. This restores the route while preserving the current Acceptance Center files in the composed output.
- **Persistence limitation:** a future full-site deployment from the NH01 source branch can replace this PR-only route again until that deployment source also composes the additive route. This report does not modify the NH01 branch or change its deployment workflow; that cross-Work change is outside PR #166's assigned scope.

## Photo, contour and coordinate contract

- Photo: `design/system/fish_media_display_v1/assets/real_catch_source.jpg`.
- Registry: `design/system/fish_media_display_v1/real_photo_cases.json`; Android test alias is byte-identical.
- SHA-256: `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`; 292,222 bytes; 1152×1536; EXIF orientation 1.
- All three views use the same photo and natural pixel coordinate space with FIT/contain and no crop. The hand-traced path uses original-photo pixels and approximate bounds `[58, 292, 435, 1445]`.
- The path retains the visible head, body, side fins and tail. It is labeled manual in the fixture, UI, parameter export and build metadata. A production BBox/Mask for this image is not available and was not fabricated.

## EdgeGlowView adaptation

- Upstream: [yu-day/EdgeGlowView](https://github.com/yu-day/EdgeGlowView), source file `edgeglowview/src/main/java/com/github/edgeglowview/EdgeGlowView.kt).
- Inspected commit: `46ce28221ed30bcd7763ff13ecdd96171756eb60`.
- License: MIT, Copyright (c) 2025 WANG YU. Notice is preserved in [`UPSTREAM_NOTICE.md`](UPSTREAM_NOTICE.md).
- The upstream three-paint/path-measure approach is adapted to the manual fish SVG Path using Canvas 2D strokes and SVG `getTotalLength()`/`getPointAtLength()` arc sampling. A bounded offscreen layer renders the glow. This is static and has no animator, particles or state machine.

### Default parameters

| Layer | Default | Render role |
|---|---:|---|
| Outer Bloom | 52 px, 72% | Broad and mid blurred `screen` strokes around the contour |
| Core Highlight | 8 px, 92% | Soft under-stroke plus sharp gradient stroke |
| Static Comet | 14% path arc, head at 21%, falloff 1.55 | Path-distance tail with blue-to-gold alpha falloff |
| Colors | `#58c8f4`, `#f2cc78`, `#fff5d8` | Blue, warm gold and core white |

The controls independently toggle the contour, bloom, core and comet. Width, intensity, tail length/position/falloff and colors redraw immediately. Reset restores these defaults. Comparison supports side-by-side, 50% Overlay and Before/After Wipe.

## Live verification and exports

Manual live-page checks were performed on the deployed renderer (build `4952ab80860d`, whose route code is unchanged at build `3bb85e58bad5`) and again on the run #29 build:

- Anonymous page load; photo verified as `6e9550…`, 1152×1536, FIT/no crop.
- Contour toggle off/on visibly removed/restored the cyan path.
- Outer Bloom off removed the broad halo; Core off visibly reduced the sharp edge highlight; each layer control changed its own enabled state.
- Outer width changed from 52 to 82 px and the visible readout changed to `82 px`; reset restored `52 px`.
- Overlay and Wipe modes switched. Wipe at 68% updated the caption to `原图 68% / EdgeGlow 32%`.
- PNG export was triggered from the live page. The resulting review file is 1152×1536, 2,144,046 bytes, PNG signature valid, SHA-256 `e4f559c7d00ef9ea30331ed76c4507ee62df83711071100234fde8b5d3561985`. Compared with the exact source photo, changed pixels are localized to bounds `[0, 228, 502, 1509]`; the photo texture remains present outside the fish effect region.
- JSON export is wired to a JSON Blob and was triggered in the live page. The attached review snapshot `EdgeGlowView_Fish_Static_Parameters.json` is a schema-equivalent snapshot made from the page’s current defaults, fixture and deployed build identity; the browser download bytes themselves were not captured in this CUA session.

### Browser CI limitation

The full Playwright smoke script remains in `scripts/smoke_edgeglow_fish_static.cjs` and covers source identity, contour geometry, independent pixel changes, reset, Overlay/Wipe, PNG/JSON downloads and responsive layouts. When temporarily re-enabled in workflow run [#27](https://github.com/pan277942135/Yujian_App/actions/runs/39235524794), the runner terminated its Chromium step with exit code `143` before the script emitted assertions or artifacts; PR run #28 was cancelled at the same step. This is recorded as **not passed**, not treated as evidence. The workflow was restored to the passing source-audit/live-deploy path so deployment does not depend on that runner process. Run #29 is the passing current source/deployment verification; it did not run the Playwright interaction suite.

## Open acceptance items

1. Human review of the hand-traced head, fin, body and tail alignment.
2. Human review of Bloom/Core separation, texture visibility and comet falloff. Keep `EDGEGLOW_VISUAL_QUALITY=REVIEW_REQUIRED` until reviewed.
3. Production contour/Mask is unavailable for this image.
4. Browser blur, compositing and anti-aliasing still require separate Android API 28 runtime validation before native adoption.
5. Full CI browser capture did not complete because the runner terminated Chromium with code 143; no CI screenshot artifact is claimed.
