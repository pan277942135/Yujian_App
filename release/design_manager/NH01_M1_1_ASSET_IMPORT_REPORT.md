# NH01 M1.1 Asset Import Report

**Task:** `M1_1_NH01_FIGMA_NATIVE_VISUAL_CLOSURE_V1`
**Observed:** 2026-10-18
**Status:** `REVIEW_REQUIRED` — background import is blocked by current Figma access; no visual PASS is claimed.

## Exact source assets

| Asset | GitHub source at the PR baseline | Dimensions | Source SHA-256 | Figma state |
|---|---|---:|---|---|
| Morning Lake background | `design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png` | 941×1672 | `5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7` | Target 41:32 remains unverified; last successful pre-attempt readback was empty. HTTP 413 from the consumed raw upload URL. |
| Brand title | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png` | 155×80 | `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` | Node 41:34 read back with IMAGE fill, FIT. Figma internal imageHash: `8d5ca69fee77b0e0e6589df6cd779701057373d5`; this is not the source SHA-256. |
| Camera artwork | `design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png` | source SHA verified | `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` | Not imported; planned only after background and component source access. |
| NH01 Frozen page | `design/system/core_visual_v1/reference/normal_home_v1.png` | 1080×1920 | `6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377` | Remains external read-only comparison source; never imported as a page fill. |

Hashes were recalculated from the checked-out repository at PR head `96d0fb96684973526285ec0ff373ab86a3620278`. The Frozen files were not modified.

## Work Chrome transfer check

- The exact GitHub raw URL for Morning Lake was opened in Work Chrome. The observed title was `Morning_Lake_Master_V1.png (941×1672)` and the original image rendered. This confirms browser network access to the source bytes; it does not confirm a local browser Downloads path.
- The existing Figma file URL displayed `Site Unavailable` and `Unable to access this site.` A single reload returned the same page.
- Because the Figma app did not load, node 41:32, the Fill panel, and the OS file chooser were not reached. File chooser state is `NOT_REACHED`; a browser-local file path is `NOT_VERIFIED`.
- No browser or OS upload permission prompt appeared. That is not evidence of a permission denial. Earlier Figma connector write preflight and native node writes succeeded, but browser-account state is not verified by that connector result.
- Ctrl+S on the raw-image tab did not surface a save dialog through the available browser tab controls. The GitHub page's `Download raw file` button was activated, but the CUA page state provided no download completion/path signal. Inspecting `chrome://downloads/` was rejected by browser URL policy (`protocol is not allowed; only http/https are allowed`), so file presence and the local Downloads path remain `NOT_VERIFIED`.

## Failed and successful transfer paths

| Path | Actual result | Action |
|---|---|---|
| Single-use Figma raw URL for Morning Lake | HTTP 413, `Request Entity Too Large` (OpenResty) | Consumed URL will not be retried or recreated. |
| Single-use Figma raw URL for brand | HTTP 405, `Not Allowed` (OpenResty) | Consumed URL will not be retried or recreated. |
| Figma connector `figma.createImage(bytes)` for brand | Success; node 41:34 read back as IMAGE / FIT | Proven route. Source bytes were the exact local GitHub PNG; Figma internal imageHash is recorded separately from source SHA. |
| Figma connector now | Starter-plan tool-call limit reached | No paid upgrade was attempted. No further Figma connector calls are represented as completed. |
| Work Chrome Figma UI | `Site Unavailable` / `Unable to access this site.` | Native browser import cannot proceed until this Figma page is accessible. |

## Next viable route

The next proven transfer is the already successful `figma.createImage(bytes)` route, applied to exact Morning Lake source bytes and node 41:32 after Figma connector availability returns. Then read back the node's IMAGE fill, verify the visual render, export the 1080×1920 frame, and run the existing visual thresholds. If the Figma browser becomes available first, use the GitHub `Download raw file` action in that same browser, verify the downloaded file's source SHA through the file chooser selection, then use the instructed Fill → Image → Upload from computer flow. Do not retry a consumed one-use URL, attempt the blocked internal Chrome URL, or treat GitHub raw visibility as a local-file chooser test.

## Current gate

`background import = BLOCKED_ENV`
`brand fill = IMAGE_FILL_READBACK_CONFIRMED; post-import visual export = NOT_RUN`
`camera = NOT_IMPORTED`
`avatar = REVIEW_REQUIRED; authority conflict unresolved`
`fresh Figma export / ROI / Glyph / Pixel Diff = NOT_RUN`
`independent QA = NOT_RUN`
