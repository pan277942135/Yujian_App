# EdgeGlowView Fish Static Prototype V1

**Task:** `YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1`  
**PR:** #166 · `design/recognition-processing-handoff-p0`  
**Scope:** Static browser rendering study. No Android product code or recognition behavior was changed.

## Gate status

| Gate | Status | Evidence and limit |
|---|---|---|
| `STATIC_FISH_CONTOUR_RENDERING` | `PASS` | The selected registered photo and manual Path load on the public page in original-photo coordinates. The contour is visibly toggleable and the three canvases share 1152×1536 FIT/no-crop geometry. This pass is for this manual fixture only. |
| `EDGEGLOW_VISUAL_QUALITY` | `REVIEW_REQUIRED` | The Web static study separates blue/cyan + warm-gold photo-edge lighting from warm-gold Fish Focus. The seven source-backed path segments and corner light need human review for visibility, contour alignment and balance. Automated/source checks are not a visual-quality pass. |
| `CLOUD_ACCEPTANCE_CENTER` | `PASS` for code build `4281a3206466`; stronger Receiving Light revision pending | Live Chrome confirms the route and registered photo load; the current browser screenshot showed the corner field remained too weak. The `0.13` Receiving Light revision described below is awaiting deployment. |
| `ANDROID_RUNTIME` | `NOT_RUN` | Canvas 2D is a Web adaptation, not Android API 28 rendering. |

The contour remains `MANUAL_REFERENCE_CONTOUR`, not a detector result, production Mask, or `FishSubjectResult`. Overall M1/Figma/Android gates are unaffected and are not marked PASS.

## Live route and source identity

- Public HTTPS route: [EdgeGlowView Fish Static Prototype](https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/edgeglow-fish-static/).
- It is linked under **Recognition Processing → EdgeGlowView Fish Static Prototype** in the existing NH01 Acceptance Center. The current center was composed from branch `design/nh01-cloud-structured-parity-v1`, SHA `13387efee9c2785930a07a8ff9fbda0e70e76445`; no Center source file was changed. The center branch advanced from the earlier `06fd4a5` pin; its diff contains only an Android test change, with no Acceptance Center static files changed.
- Latest recovery source: PR code/workflow SHA `0bfc6c0452ed58db46c371cb431a062196ecad49`; NH01 source SHA `13387efee9c2785930a07a8ff9fbda0e70e76445`. The current cold requests to the public page and `build-info.json` returned HTTP 200; metadata matches both SHAs and the registered photo SHA.
- Push run ID `39241912112` passed the exact-PR checkout, current-center branch/SHA guard, composition, renderer/source audit, Pages upload, deploy and live HTTPS/photo identity check. The report update below triggers another composition and deployment so the committed report and live build remain aligned.
- The current PR head after this report update may advance by a documentation-only commit; the renderer source and tested route remain unchanged by the report edit.

### Pages replacement incident — 2026-10-19

- The user's screenshot showed GitHub Pages' actual `404 File not found`; a cold reload of the exact public route reproduced it. The NH01 Acceptance Center parent route still returned HTTP 200.
- GitHub's deployment listing shows the composed EdgeGlow deployment from PR SHA `36ac340c81aef77df731f1dd6ddb7fec045fcf0f` was created at `2026-10-19T10:17:16Z` (deployment `7171168058`). Two minutes later, a full-site deployment from the NH01 source branch at SHA `06fd4a5e048bcfd4e963c84303efebc932f98982` was created at `2026-10-19T10:19:58Z` (deployment `7171216398`).
- Both workflows publish to the repository's single GitHub Pages site. The later NH01 artifact does not contain PR #166's child route, so it replaced the composed artifact and caused the 404. This is a deployment-order/source-composition issue, not a malformed URL or a missing route in PR #166.
- The first recovery attempt failed the stale-center SHA guard because the live NH01 branch had advanced to `1949fa34096c5709f914e11107d38c74a6747160`; no Pages upload or deployment occurred in that failed attempt. PR #166's workflow pin was updated to this verified current source SHA.
- Push run #35 then passed and restored the composed Acceptance Center. After deployment, a cold request to the exact child URL and to `build-info.json` both returned HTTP 200; metadata identified PR SHA `d9607b3e3853305b09a6473ce3db2a6821c9f73e`, center SHA `1949fa34096c5709f914e11107d38c74a6747160`, and the registered photo SHA.
- **Persistence limitation:** a future full-site deployment from the NH01 source branch can replace this PR-only route again until that deployment source also composes the additive route. This recovery does not modify the NH01 branch or its deployment workflow; that cross-Work change remains outside PR #166's assigned scope.

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

## Photo-edge light field

The first static page revision omitted the light around the photo perimeter. This revision adds the existing AI Edge Field as a separate, editable Web layer over the same verified photo; it does not use a Frozen screenshot as a background.

### Frozen-reference color-role and perimeter correction — 2026-10-19

Visual review of `02_AI_Understanding_Frozen.png`, `03_Fish_Highlight_Frozen.png`, and `04_Fish_Identifying_Frozen.png` establishes two separate color roles: blue/cyan and warm gold trace the photo perimeter and corners; the fish-body contour, bloom, core, and comet are warm gold. The earlier EdgeGlow renderer incorrectly applied a blue-to-gold gradient to the fish and the live page remained on a build without the perimeter field. The correction removes blue from Fish Focus entirely, changes the contour guide to warm gold, and keeps blue/cyan controls under Photo Edge Field only. Fish Focus defaults are narrowed to a 28 px bloom at 62% and a 3 px core at 92% to preserve the fish scale and texture in the registered 1152×1536 fixture. These remain Web review parameters; no Frozen pixel or Android runtime pass is implied.

- Local Receiving Light uses four clipped corner gradients with radial falloff and no full-photo tint. After the first deployed correction still looked too weak, the current Web candidate uses Android's runtime alpha `0.13` and raises Primary Outer/Mid Glow to `0.05` / `0.10` before multipliers. Alpha `0.13` exceeds the Frozen Rendering Contract cap `0.07`; this is an explicitly provisional visual candidate under `RP-A01`, not a Frozen-approved parameter.
- The four primary paths are `B1`, `G1`, `B3`, and `G3`; the three secondary fragments are `B2`, `G2`, and `B4`. Geometry, widths, weights, seeds and visible ratios come from Android `RecognitionAmbientField.kt` at Git blob `1969409c2f903a16ea38ed2f73b705daa424c050`. The three secondary seed values are explicitly Android runtime values tracked as `RP-A03`, not Frozen seeds. The static preview draws each visible segment only; it does not connect paths into a complete perimeter frame.
- FIT/no-crop photo coordinates are reused for the receiving gradients, edge paths and fish contour. Overhang control points remain outside `[0,1]` and are clipped by the canvas bounds.
- The Web preview defaults to Receiving Light alpha `0.13`, matching Android `RecognitionAmbientField.kt`; the Frozen Rendering Contract allows up to `0.07` (`RP-A01`). The UI and JSON preserve this discrepancy. Human review must judge whether the stronger corner light better matches the Frozen reference before this value is considered for native adoption.
- The Receiving Light, Primary Islands and full Edge Field each have working toggles; intensity and blue/gold colors update immediately and reset with the rest of the controls. PNG and JSON export include the current visible render and field parameters.

### Default parameters

| Layer | Default | Render role |
|---|---:|---|
| Outer Bloom | 28 px, 62% | Broad and mid blurred warm-gold `screen` strokes around the fish contour |
| Core Highlight | 3 px, 92% | Pale-gold soft under-stroke plus sharp warm-gold contour |
| Static Comet | 14% path arc, head at 21%, falloff 1.55 | Warm-gold path-distance tail with pale-gold head |
| Fish Focus colors | `#FFD887`, `#FFE7AE` | Warm gold and pale gold; no blue fish-body highlight |
| Photo-edge colors | `#BFEAF2`, `#F6D99B` | Blue/cyan and gold are confined to the photo perimeter field |
| AI Edge Field | Receiving Light `0.13` alpha (Android runtime candidate; exceeds Frozen contract cap `0.07`); four primary + three secondary path segments | Four local edge gradients and seven disconnected static path segments; secondary seeds are Android runtime values (`RP-A03`), no particles or motion |

The controls independently toggle the contour, Receiving Light, Primary Islands, overall Edge Field, fish bloom, core and comet. Width, intensity, tail length/position/falloff and the separate perimeter/fish palettes redraw immediately. Reset restores these defaults. Comparison supports side-by-side, 50% Overlay and Before/After Wipe.

## Live verification and exports

The following interaction/export results were captured before the Frozen-reference color correction and apply only to that earlier build. They do not verify the corrected palette or latest cloud deployment; those are recorded separately below after the new source is published.

Manual live-page checks were performed on the deployed renderer (build `4952ab80860d`, whose route code is unchanged at build `3bb85e58bad5`) and again on the run #29 build:

- Anonymous page load; photo verified as `6e9550…`, 1152×1536, FIT/no crop.
- Contour toggle off/on visibly removed/restored the cyan path.
- Outer Bloom off removed the broad halo; Core off visibly reduced the sharp edge highlight; each layer control changed its own enabled state.
- Outer width changed from 52 to 82 px and the visible readout changed to `82 px`; reset restored `52 px`.
- Overlay and Wipe modes switched. Wipe at 68% updated the caption to `原图 68% / EdgeGlow 32%`.
- PNG export was triggered from the live page. The resulting review file is 1152×1536, 2,144,046 bytes, PNG signature valid, SHA-256 `e4f559c7d00ef9ea30331ed76c4507ee62df83711071100234fde8b5d3561985`. Compared with the exact source photo, changed pixels are localized to bounds `[0, 228, 502, 1509]`; the photo texture remains present outside the fish effect region.
- JSON export is wired to a JSON Blob and was triggered in the live page. The attached review snapshot `EdgeGlowView_Fish_Static_Parameters.json` is a schema-equivalent snapshot made from the page’s current defaults, fixture and deployed build identity; the browser download bytes themselves were not captured in this CUA session.

### Browser CI limitation

The full Playwright smoke script remains in `scripts/smoke_edgeglow_fish_static.cjs` and covers source identity, contour geometry, independent pixel changes, reset, Overlay/Wipe, PNG/JSON downloads and responsive layouts. When temporarily re-enabled in workflow run [#27](https://github.com/pan277942135/Yujian_App/actions/runs/39235524794), the runner terminated its Chromium step with exit code `143` before the script emitted assertions or artifacts; PR run #28 was cancelled at the same step. This is recorded as **not passed**, not treated as evidence. The workflow was restored to the passing source-audit/live-deploy path so deployment does not depend on that runner process. Run #35 is the passing recovery source/deployment verification; it did not run the Playwright interaction suite.

## Open acceptance items

1. Human review of the hand-traced head, fin, body and tail alignment.
2. Human review of Bloom/Core separation, texture visibility and comet falloff. Keep `EDGEGLOW_VISUAL_QUALITY=REVIEW_REQUIRED` until reviewed.
3. Production contour/Mask is unavailable for this image.
4. Browser blur, compositing and anti-aliasing still require separate Android API 28 runtime validation before native adoption.
5. Full CI browser capture did not complete because the runner terminated Chromium with code 143; no CI screenshot artifact is claimed.


### Repeated public 404 and recovery — 2026-10-19

- After the first recovery report, the exact public child route again returned HTTP 404; build-info.json also returned 404. The Acceptance Center parent and Design Manager parent still returned HTTP 200. A fresh Chrome reload showed GitHub Pages' actual 404 page. This disproved the prior wording that treated the route as persistently available.
- I did not identify a specific later deployment run that removed the route. The replacement risk is verified in repository source: the NH01 Design Manager Pages workflow copies the center branch's whole design tree to _site and can deploy through the same design-manager-pages environment, while the additive EdgeGlow route exists only in PR #166. A center-only full-site deployment can therefore omit the route.
- The live NH01 branch was rechecked with git ls-remote at 13387efee9c2785930a07a8ff9fbda0e70e76445. PR #166's composition workflow pin was updated to that exact SHA in commit 0bfc6c0452ed58db46c371cb431a062196ecad49.
- Push workflow run [39241912112](https://github.com/pan277942135/Yujian_App/actions/runs/39241912112) passed composition, source audit, Pages deployment and live HTTPS/photo identity verification. A fresh external request returned HTTP 200 for the route. build-info.json reports status CLOUD_PREVIEW_COMPOSED, PR source 0bfc6c0452ed58db46c371cb431a062196ecad49, center source 13387efee9c2785930a07a8ff9fbda0e70e76445, and photo SHA 6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef. A fresh Chrome reload also rendered the prototype and completed the photo identity check.
- The live route is restored now, but not guaranteed to survive a future NH01 full-site Pages publish until that deployment composes the additive route. No NH01 source branch, workflow, production Design Manager content, Frozen asset, or PR #165 was changed. Durable integration remains outside Work B's PR #166-only scope.
- PR #166 remains Draft/Open/Unmerged. EDGEGLOW_VISUAL_QUALITY=REVIEW_REQUIRED; ANDROID_RUNTIME=NOT_RUN.

### User visual review — 2026-10-19

- A fresh Chrome view of the exact route showed `buildSha=bba61bda4da9` and the previous blue-to-gold fish contour, while PR #166 had advanced to `372c7b0e1af38419125fc66ea21f7b9146dd4fa6`. The active route was stale; the missing perimeter field was not a browser rendering setting.
- Frozen reference review confirmed blue/cyan + gold on the photo perimeter and warm gold on the fish. This correction is now in PR source; recheck the live `build-info.json` and rendered image before calling the cloud route current.
- The visual-quality gate stays `REVIEW_REQUIRED`; Android runtime stays `NOT_RUN`.
