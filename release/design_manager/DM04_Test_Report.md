# DM04 · Design Manager 回归测试报告

Date: 2026-10-16 (UTC)

Tested source / production: `main@72329c026cb4a42f4af3bc885f9f9b36c004ef2d`
Final Pages run: [38927674288](https://github.com/pan277942135/Yujian_App/actions/runs/38927674288)

## Local and governance checks

| Command / gate | Result | Evidence |
|---|---|---|
| `python3 scripts/design_manager.py validate` | PASS | 16 shared systems and 15 page modules validate. |
| `python3 scripts/design_manager.py summary` | PASS | Existing module statuses render; no status semantics changed. |
| `python3 scripts/verify_design_manager_navigation.py` | PASS | NH01–NH07, Recognition main journey, P01–P03 and Recognition Result mappings retained. |
| `python3 scripts/verify_design_manager_hifi_release.py` | PASS | Source SHA/dimensions, Registry, navigation, missing/review semantics, invalid route and negative integrity cases. |
| `python3 scripts/validate_control_plane_admission.py` | PASS | Six-layer static Control Plane admission on the release branch; no Control Plane file changed. |
| `node --check design/manager/app.js` | PASS | Manager JavaScript syntax. |
| `node --check scripts/verify_design_manager_browser.mjs` | PASS | Browser suite syntax. |
| Pages and Experience Governance workflow YAML parse | PASS | Both workflow files parse on PR #154. |
| `git diff --check` | PASS | No whitespace errors. |
| Main Design Governance | PASS | Run `38927674363`. |
| Main Control Plane Admission | PASS | Run `38927674312`. |

## DM-T acceptance matrix

| ID | Requirement | Result | Evidence |
|---|---|---|---|
| DM-T01 | P01–P03 SHA and dimensions | PASS | Production run downloaded each source; online bytes equal the approved manifest and Registry hashes (941×1671, 941×1671, 941×1672). |
| DM-T02 | Registry / Navigation references complete | PASS | Existing guide manifest/navigation and Registry cross-references are validated; legacy P01/P02/P03 IDs remain unchanged. |
| DM-T03 | Every displayed Frozen image exists | PASS | All rendered Frozen guide images are present and hash-checked. Missing expected Capture/permission/Master Board sources render status only, with no replacement image. |
| DM-T04 | Menus and nested navigation | PASS | Recognition overview, existing `07 · 首次拍照教学` children and boundary gate remain reachable; NH and Result route guards pass. |
| DM-T05 | Direct Hash routes | PASS | Chromium opened P01/P02/P03 and other stable routes; refresh and browser history checks pass. |
| DM-T06 | Original-resolution preview and zoom | PASS | Chromium confirms original natural dimensions, `object-fit: contain`, source links, zoom, Fit Window, Escape close and focus restoration. |
| DM-T07 | REVIEW/MISSING are not FROZEN | PASS | New States stays `PARTIAL`; absent assets remain `BLOCKED_INTEGRITY` / `MISSING` / `PARTIAL`; invalid routes show an explicit error, with no Frozen fallback. |
| DM-T08 | Desktop / tablet / phone response | PASS | Chromium tested 1440, 768, 390, 360 and 320 CSS px; no horizontal overflow. |
| DM-T09 | Existing page navigation regression | PASS | Static navigation guard retains NH01–NH07 and RR01–RR08; current Recognition subroutes and legacy guide IDs remain. |
| DM-T10 | Pages Artifact resource completeness | PASS | Build job verified Registry, route targets and hi-fi artifact resources before uploading `github-pages`. |
| DM-T11 | Real GitHub Pages access | PASS | Deployed SHA, build marker, original PNG/SVG bytes and expected-missing 404 paths verified online; 15 present visual files matched SHA, and 13 Recognition contract links returned HTTP 200. |
| DM-T12 | Authority and Governance have no conflict | BLOCKED | Control Plane and Governance workflows pass, but current-main Capture landscape expected SHA is malformed (63 characters) and differs from the 64-character candidate found only on unmerged PR #153. No Authority hash was changed or bypassed. |

## Negative and integrity checks

- The release validator's temporary-tree test fails when a required Frozen guide asset is removed.
- The same validator fails when a Frozen image byte is mutated; repository source bytes are not changed by the test.
- Browser tests reject invalid Hash fallback, missing-source fake images, and links to absent target assets.
- Manual source audit: current-main Capture landscape manifest and Registry value is `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18` (63 characters). PR #153 carries a 2,079,370-byte, 1672×941 source with SHA-256 `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18f` (64 characters). They differ; candidate is not an authorized byte match for current main.

## CI, deployment and failure history

PR #154 checks all passed: Design Governance `38925600331`, Control Plane Admission `38925605680`, and Android CI `38925601026`. PR #155 checks all passed: Design Governance `38927018987`, Control Plane Admission `38927019019`, and Android CI `38927018984`. Android CI here is a repository check; it is not Android Runtime acceptance.

The first post-merge Pages attempt (`38926382780`) deployed the site and passed live asset SHA verification but its browser step read P02's natural dimensions before the image finished loading (`complete=false`, width/height 0). The source download and independent production check confirmed P02 was present at 941×1671 with the approved SHA. PR #155 made the browser test wait for `img.complete && naturalWidth > 0`. The final run `38927674288` then passed both build and deploy jobs, including live asset bytes and live browser routes.

The final run uploaded:

- `github-pages`, artifact `12056738754`, 188,934,315 bytes, digest `sha256:8cd27c8798addf41b4c2db9b2ec46231660f5fe85dd6e263b27abee68c7ad0a2`.
- Browser build screenshots, artifact `12057012967`, digest `sha256:7f92dac1f29d665ac2dd02ee8e2b719c57a6f990b808b2a11337749fc8c02d74`.
- Live release screenshots and verification JSON, artifact `12056788832`, digest `sha256:863266d8fda011d669128069e2635fbfe7ffd619edb5f4251a263ef4b7a6959b`. Screenshots: `desktop-1440.png`, `tablet-768.png`, `mobile-390.png`, `lightbox-1440.png`, `overview-1440.png`.

A redundant local rerun of the full live verifier did not finish within the local network window and was stopped. It is not used as evidence; the deployed Pages run's live verifier passed, and separate online requests independently checked all 15 present visual SHA values plus the 13 contract-link HTTP statuses.

Live contract-link check used HTTP GET from the release shell because direct browser navigation to a `.md` target returned a local `ERR_BLOCKED_BY_CLIENT`; that browser error was not an HTTP response from Pages. The independent production HTTP check returned `200` for all 13 Registry-linked Recognition contract paths. `manifest.json` and `navigation.json` also returned `200`.

## Runtime boundary and remaining action

P01/P02/P03 are Design `FROZEN`; their Android Runtime status remains `NOT_RUN`. No Runtime status is inferred from the browser suite or image availability.

The manager publication is deployed and `PASS`. The complete hi-fi publication gate is `BLOCKED` until the four current-main absent sources are resolved and the Capture landscape expected SHA conflict is corrected through the canonical Control Plane process. The four absent outputs are Capture Ready portrait, Capture Ready landscape, camera permission PNG, and Master Board PNG/SVG (five files total). Do not import or rewrite the landscape candidate until the current Authority SHA is resolved by an authorized main change.
