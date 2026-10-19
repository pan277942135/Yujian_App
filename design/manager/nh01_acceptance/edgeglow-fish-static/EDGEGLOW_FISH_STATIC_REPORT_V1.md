# EdgeGlowView Fish Static Prototype V1

**Task:** `YUJIAN_EDGEGLOW_FISH_STATIC_HIFI_V1`

**PR:** #166 · `design/recognition-processing-handoff-p0`

**Scope:** Static browser rendering study; no Android product or recognition behavior changes.

## Status

| Gate | Status | Evidence |
|---|---|---|
| `STATIC_FISH_CONTOUR_RENDERING` | `NOT_RUN` until browser overlay and Canvas geometry checks finish | `fixture.json`; workflow artifact will record the run result |
| `EDGEGLOW_VISUAL_QUALITY` | `REVIEW_REQUIRED` | The generated original/contour/glow PNGs are supplied for human optical review. Automated interaction success is not a visual quality pass. |
| `CLOUD_ACCEPTANCE_CENTER` | `PENDING_DEPLOY` until the Pages workflow's live check succeeds | Workflow `EdgeGlow Fish Static · NH01 Acceptance Center`; deployed route and source hashes will be recorded in PR evidence. |
| `ANDROID_RUNTIME` | `NOT_RUN` | Browser Canvas 2D does not execute Android `Canvas`, `PathMeasure`, or API 28 rendering. |

The contour in this prototype is an explicitly hand-traced visual reference, not a production mask. No production `FishSubjectResult` contour was available for this exact photo. `EDGEGLOW_VISUAL_QUALITY` must remain `REVIEW_REQUIRED` until a person inspects the delivered render.

## Reused photo and coordinate contract

- Source: `design/system/fish_media_display_v1/assets/real_catch_source.jpg`.
- Existing registry: `design/system/fish_media_display_v1/real_photo_cases.json` (`FROZEN_DESIGN`, `fish_bbox_available: false`).
- Android test alias: `app/src/androidTest/assets/recognition_real_catch_fixture.jpg`; the files are byte-identical.
- SHA-256: `6e955087108f7463eca2dbac489699a4942028ebe1b3d456ddfca523286385ef`.
- Dimensions: 1152 × 1536; EXIF orientation 1; 292,222 bytes.
- All three canvases use the photo's natural 1152 × 1536 coordinate space and `contain` with no crop. The Path uses those same original pixel coordinates, approximately `[58, 292, 435, 1445]`.
- Manual Path source is marked `MANUAL_REFERENCE_CONTOUR` in the fixture, UI, parameter JSON, build metadata and this report. It is never described as a detector result or a production mask.

The photo is reused in place from the repository's existing public real-catch test asset. The image contains a person and an existing platform watermark; this task adds no new photo copy or derived photo asset.

## EdgeGlowView source and web adaptation

- Upstream: [yu-day/EdgeGlowView](https://github.com/yu-day/EdgeGlowView).
- Source file: `edgeglowview/src/main/java/com/github/edgeglowview/EdgeGlowView.kt`.
- Inspected upstream commit: `46ce28221ed30bcd7763ff13ecdd96171756eb60`.
- License: MIT, `Copyright (c) 2025 WANG YU`; full notice is preserved in [`UPSTREAM_NOTICE.md`](UPSTREAM_NOTICE.md).

The upstream `EdgeGlowView.kt` constructs a rounded rectangle, draws three separate stroke paints and samples a closed path with `PathMeasure`/`getSegment()` for its comet. The browser prototype preserves this layered approach but uses the manual fish SVG path, Canvas 2D strokes and SVG `getTotalLength()` / `getPointAtLength()` sampling. The effect is static; no animator, particles or state machine are included.

### Default visual parameters

| Layer | Default | Rendering |
|---|---:|---|
| Outer Bloom | 52 px, 72% | Two low-alpha `screen` strokes, broad and mid blur radii derived from width |
| Core Highlight | 8 px, 92% | Soft under-stroke plus sharp gradient stroke following the fish Path |
| Static Comet | 14% arc length, head at 21%, falloff 1.55 | SVG path-distance segments; low-alpha tail to warm bright head |
| Colors | blue `#58c8f4`, gold `#f2cc78`, core `#fff5d8` | Editable inputs; changes redraw immediately |

The page exposes independent Bloom/Core/Comet/Contour toggles, Bloom and Core width/intensity, static tail length/location/falloff, three color controls, reset, a focused Overlay/Wipe comparison on the final view, PNG export and parameter JSON export.

## Existing Acceptance Center integration

The public center currently comes from branch `design/nh01-cloud-structured-parity-v1`, live build/source SHA `8481cecebd057f84858e5548163fbb61bb4d50e7`. The build script composes that exact center source with the route from PR #166 and adds one navigation entry under the existing Acceptance Center mode navigation. It does not modify the center source branch or create a second site. The deploy workflow fails closed if the center branch SHA has moved since the reviewed baseline.

The existing NH01 Visual Compare is bound to the Normal Home Frozen/Structured sources; it cannot compare this different photo/contour pair directly. The EdgeGlow route keeps three same-coordinate views and provides only the minimal 50% Overlay and Wipe modes for its own fish render. It reuses the Acceptance Center stylesheet and shell, not a second Acceptance Center.

## Test and evidence

`scripts/smoke_edgeglow_fish_static.cjs` exercises the Acceptance Center link, exact photo SHA and dimensions, contour path size/arc length, identical canvas coordinate dimensions, three layer toggles, width/length/position changes, deterministic reset, Side/Overlay/Wipe, PNG and JSON exports, and 390×844 / 800×360 / 1440×1180 layouts. The browser run saves:

- `01_Original_Photo.png`
- `02_Manual_Contour_Overlay.png`
- `03_EdgeGlow_Adapted.png`
- `EdgeGlowView_Fish_Static_Current_Render.png`
- `EdgeGlowView_Fish_Static_Parameters.json`
- `04_Responsive_390x844.png`
- `EdgeGlow_Fish_Static_Test_Report.json`

Workflow run ID, live URL, PR source SHA and generated artifact details are recorded after the cloud verification completes. The current source report must not be read as evidence of Android runtime or Frozen visual acceptance.

## Remaining review items

1. Human review of whether the hand-traced head, side fins, body and tail follow the photograph closely enough.
2. Human review of Bloom/Core separation, texture visibility and whether the comet reads as a gradual path-following tail.
3. Production contour/mask for this photo remains unavailable and is outside this static manual-path task.
4. Canvas blur/compositing, anti-aliasing and color response still require a separate API 28 Android Runtime test before any native adoption.
