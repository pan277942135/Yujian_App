# M1 Design & Asset Report — Track B

**Status:** Source/package map recorded; API28 Runtime acceptance `NOT_RUN`

**Branch:** `work/m1-track-b-design`

**PR #162 baseline head:** `43b8267167d8a948754fe3df7d20edf072955cf9`

**Canonical base SHA:** `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`

**Two-page authority sync commit:** `8679bf0bdb10ee8d57444c5c3d2655982398f0a1`

**Design review item:** Frozen P01/P02 composites retain the historical 03/03 counter/dots while the active M1 guide contract is two pages. C01 is now effective for M1 by explicit `YUJIAN_M1_PARALLEL_DELIVERY_V1` directive; the immutable visual mismatch remains recorded for design review and is not silently accepted as visual parity.

**C01 mapping:** P01 remains the former P01 PNG byte-identically; P02 uses the former P03 PNG byte-identically; the former vertical-fish P02 is archive-only. The exact raw source identities are listed below.

PR #162 remains Draft/Open and unmerged pending repository review. The M1 directive makes the V1.2 two-page sequence effective for this delivery and supersedes the prior three-page contract for M1 only. It retained P01 and P02 as the only active pages, kept the former vertical-fish P02 archive-only, and synchronized the Page Contract, Registry, Feature Matrix, source manifest, navigation, and Android guide policy. No Frozen image bytes were modified.

## Frozen page assets

| Page | Active Frozen path | Dimensions | Bytes | SHA-256 |
|---|---|---:|---:|---|
| P01 | `design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png` | 941×1671 | 1,988,159 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` |
| P02 | `design/pages/recognition/first_use_camera_guide/frozen/P02_Landscape_Horizontal_Fish_Frozen.png` | 941×1672 | 2,001,322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |

The focused asset validator recomputed these raw PNG hashes, dimensions, byte counts, and the former vertical P02 archive identity. The archive asset remains archive-only.

## Canonical Figma sync

The canonical target is [Recognition Main Journey Master Board V1](https://www.figma.com/design/VjN7iFviVN9aKeLv8kJivM/Recognition-Main-Journey-Master-Board-V1?node-id=6-2), file key `VjN7iFviVN9aKeLv8kJivM`, page `0:1`, board `6:2`.

| Guide mapping | Existing board node | Figma source label after sync | Binary fill |
|---|---|---|---|
| P01 / former P01 | MB-03 source node `6:21` | `dc731cca…` | Not uploaded |
| P02 / former P03 original bytes | MB-04 source node `6:28` | `1a300848…` | Not uploaded |
| Former vertical-fish P02 | Archive asset; not active | `fb092235…` retained in repo archive | Not uploaded / not active |

Live metadata was inspected before and after the sync. The existing P02 source label changed from the vertical-fish SHA `fb092235…` to the approved P02 source SHA `1a300848…`; the former MB-05/P03 node `12:2` is labeled ARCHIVE and moved beyond the board’s active 5120 px canvas. Its retained source identity says those bytes are now active as P02. The P01/P02 nodes remain source placeholders because binary import did not complete. The upload endpoint timed out at the network proxy; no image fill or Figma binary import is claimed. The board screenshot after metadata sync and the queried node metadata are the evidence for this sync.

Captured Figma evidence is stored under `docs/qa/recognition_main_journey/figma_c01_sync/`: `MASTER_BOARD_C01_after_sync.png`, `MB04_P02_C01_source_mapping.png`, and `ARCHIVE_MB05_former_P03_C01.png`. The Figma views still show blank source placeholders, consistent with `binary_import_status: NOT_IMPORTED`.

## Processing timing authority for M1 (C02)

M1 timing follows the V3.1 `State_Machine_V3_1.json`: minimum visual beats CAPTURED ≥220 ms, DETECTING ≥320 ms, OUTLINE ≥350 ms, and CLASSIFYING ≥250 ms, each gated by its real pipeline event. The prior V1.1 2.8 s narrative is not an M1 requirement. V1.2 continues to supply the visual-state mapping. Source files and global registries remain unchanged. See [M1_DESIGN_AUTHORITY_RECONCILIATION_C02.md](M1_DESIGN_AUTHORITY_RECONCILIATION_C02.md) and [M1_DESIGN_AUTHORITY_REGISTRY.json](M1_DESIGN_AUTHORITY_REGISTRY.json).

The global `Page_Registry_V3_1.json` and `Effective_Authority_Registry_V3_1.json` were deliberately left unchanged because they govern shared V3.1 authority and have no delivery-scoped exception field. C02 is an additive M1 mapping only; it is not Android runtime evidence.

## Independent photo-only runtime sources

All four sources are 1448×1086 PNGs. Each source has one canonical Android `drawable-nodpi` WebP runtime path and is shared by both P01 and P02.

| Role / error | Source PNG: bytes / SHA-256 | Canonical Android WebP: bytes / SHA-256 |
|---|---|---|
| Hero / correct example | `design/pages/recognition/first_use_camera_guide/runtime_sources/generated/guide_horizontal_hero_v1_source.png` · 2,482,673 · `57d296e7d9d73e387f524510c09de9d14e96083239b3ee86a49e963be74b69e4` | `app/src/main/res/drawable-nodpi/recognition_guide_horizontal_hero_v1.webp` · 140,046 · `9cfac4c30d7a1eb1baf3f9add58a2670605cc80fc2a4fa674681483534688522` |
| Error / fish head outside frame | `design/pages/recognition/first_use_camera_guide/runtime_sources/generated/guide_horizontal_error_head_outside_v1_source.png` · 2,344,213 · `cfe75be11956586b716b9554d1170f303992c0d4384caca3875a7654747c2631` | `app/src/main/res/drawable-nodpi/recognition_guide_horizontal_error_head_outside_v1.webp` · 115,210 · `957e2d3b00b237da5d2346ffc217512219fa9130dad7793c7e81e7e5b469c489` |
| Error / fish too small | `design/pages/recognition/first_use_camera_guide/runtime_sources/generated/guide_horizontal_error_too_small_v1_source.png` · 1,965,611 · `bb9beee3270fafa2f29f3a30deb87d0b27a1ba16c395e1bf35dcb55ea629f5e3` | `app/src/main/res/drawable-nodpi/recognition_guide_horizontal_error_too_small_v1.webp` · 51,036 · `ebb6261d83775e413b248637f2d0e3e431d04d2e9cde9c35895aa48ed411a26c` |
| Error / excessive occlusion | `design/pages/recognition/first_use_camera_guide/runtime_sources/generated/guide_horizontal_error_occluded_v1_source.png` · 2,543,245 · `b71da16fe4e92b8a6f2e804370ee576d88fb1b26ed7cf90ce7bb91a05ccf8722` | `app/src/main/res/drawable-nodpi/recognition_guide_horizontal_error_occluded_v1.webp` · 140,502 · `e1b581933c5730c0f1b64d0e3eb462bd23eb470cfffdfd4b318587cb947fb345` |

**Provenance and rights/source disposition:** The originals were generated with the built-in OpenAI ImageGen tool for this task. The exact prompt set was not retained in artifact metadata; the source manifest records that limitation and does not reconstruct prompts. No external stock or third-party image source was used. Inclusion in this authorized repository work is approved; no broader ownership or licensing claim is made, and no external image license is required for these generated originals.

The manifest records source and packaged WebP paths, resource IDs, dimensions, byte counts, and SHA-256 values. Static verification proves the PNG and WebP file identities in this source tree. It does not prove the compiled APK contains those exact WebP bytes; the revised Android component test checks that at runtime but could not be executed in this environment.

## Frozen-to-runtime delta

- Frozen PNGs remain the page-level visual authority and were not cropped, edited, recompressed, or packaged as runtime photo pixels.
- The existing Android Hero region now displays the separate horizontal Hero photo; each of the three existing error regions displays its mapped sample photo. The photos are shared between P01/P02; policy labels, orientation, navigation, and existing overall layout remain in place.
- **Design review blocker:** the frozen composites still contain the historical `01/03` (P01) and `03/03` (P02) page counters and three-dot indicators. Android guide UI uses the active two-page P01/P02 count and two dots. The design authority must resolve this discrepancy; Frozen PNG bytes remain unchanged.
- Runtime sample placement uses the current Compose Hero crop and compact error thumbnails. Exact visual comparison against the Frozen composition awaits the production CameraX route evidence.

## Focused checks

| Check | Result |
|---|---|
| `python3 scripts/verify_first_use_camera_guide_assets.py` | PASS — Frozen PNG and archive identities plus four source/package mappings verified; Runtime remains `NOT_RUN` |
| `python3 scripts/verify_design_manager_navigation.py` | PASS |
| `python3 scripts/verify_design_manager_hifi_release.py` | PASS |
| `python3 scripts/validate_feature_matrix.py` | PASS — 15 features, 20 journeys, 22 routes, 0 open gaps |
| `python3 scripts/validate_phase_task_state_machine.py` | PASS — 10 phases, 45 tasks, 20 journeys |
| `python3 scripts/validate_unified_contract.py` | PASS — 0 structural errors, 0 open gaps |
| `python3 scripts/verify_android_journey_integration.py` | PASS — P13 Android journey integration static checks |
| M1 authority matrix schema and raw-source audit | PASS — 65 rows, required authority/quality fields present; 21 existing Frozen paths rehashed; missing paths explicitly blocked/missing; runtime quality remains `NOT_RUN` |
| `python3 scripts/design/verify_page_contract_v3.py` | FAIL — source HEAD `9ccfec0f22f4b6b78d277e00b70c4ab36b20d3a6`; stale hard-coded expected count 658 vs 669 current registry entries; see Static validator discrepancy |
| `python3 scripts/validate_control_plane_registry.py` | PASS — 6 layers, 21 files, 4 authority roots |
| M1 C01/C02 authority registry check | PASS — active Frozen hashes, archive mapping, source references, and M1 timing values verified |
| Figma node metadata readback | PASS — P02 source label and archived former P03 node match C01; PNG fills remain `NOT_IMPORTED` |
| Figma PNG upload | BLOCKED — network proxy CONNECT timed out; no Figma binary import is claimed |
| Frozen P01/P02 diff against implementation base | PASS — no Frozen PNG bytes changed |
| `git diff --check` | PASS |
| Android guide policy unit test / Compose instrumentation test | NOT RUN — this checkout has no `gradlew`, no installed `gradle`, and no `adb` |

The revised Compose instrumentation test asserts all four drawable resources resolve with their expected dimensions, byte counts, and SHA-256 values; confirms both pages share the same four photo bindings; captures P01/P02 component evidence only; and asserts that no P03 screenshot is written. These assertions are code changes, not test results.

## Remaining gate

`runtime_acceptance` remains `NOT_RUN`, and M1 gate P0-003 remains open for exact-head API28 Pixel_2 production CameraX screenshots, bounds, Pixel Diff, and acceptance of the exact tested APK/source SHA. No APK packaging, Runtime PASS, visual parity PASS, or Release PASS is claimed in this report.


## M1 authority matrix

The machine-readable and human-readable matrices are [`M1_DESIGN_AUTHORITY_MATRIX.json`](M1_DESIGN_AUTHORITY_MATRIX.json) and [`M1_DESIGN_AUTHORITY_MATRIX.md`](M1_DESIGN_AUTHORITY_MATRIX.md). The matrix contains **49 page/state rows and 16 core-component rows (65 records total)** across Empty Home, Capture/permissions/guide/Picker, Processing, Result/recovery/edit/save, FishRecordDetail A/B, conditional session-expired recovery, and shared components. Every record maps its Design Manager/feature route, effective authority, original Frozen asset path/raw SHA when bytes exist, page contract, Shared Foundation paths, runtime target, source status, and known gap. The matrix is derived from canonical base `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`, PR #162 HEAD `43b8267167d8a948754fe3df7d20edf072955cf9`, and Track B branch pre-audit HEAD `9ccfec0f22f4b6b78d277e00b70c4ab36b20d3a6`; the audit was then applied locally. PR #163 live head `81aefb1c0eae08df5ea6f451fc7c65be89a9c191` is a reviewed reference, not this checkout SHA. Raw SHA values were recomputed from the current checkout; declared-only values remain separate when bytes are absent.

### Authority and source gaps

- **C01 effective for M1:** V1.2 P01/P02 is effective by the explicit `YUJIAN_M1_PARALLEL_DELIVERY_V1` task decision and supersedes the historical three-page contract for M1. This is M1 work-branch scoped; PR #162 remains Draft/Open/unmerged and no global/main authority is changed.
- **Capture `BLOCKED_INTEGRITY`:** the portrait and landscape Capture Frozen PNGs are referenced by the Capture HiFi manifest but absent from this checkout. The permission-denied raster is listed by filename and SHA in the Page Registry without a repository path; raw bytes cannot be verified. The Capture manifest SHA values are declarations only.
- **Detail A `MISSING`:** no canonical A-side full-page Frozen PNG or source hash is registered. The manager map points to a core visual reference thumbnail, which the matrix excludes. The A-side source therefore cannot be used as a pixel-diff target.
- **Technical Failure `MISSING`:** the business contract requires an independent technical-recovery outcome, but no dedicated Frozen visual authority is registered. The matrix does not substitute No Fish or Image Quality artwork.
- **Processing visual mapping:** the V1.2 visual set has three product states. `IMAGE_RECOGNIZING` uses EARLY/LATE keyframes; these are not separate product states. The four source Frozen PNGs map to the first four stable QA target IDs while M1 C02 timing follows V3.1 minimum beats plus real pipeline events.
- **Review statuses:** the directly readable Processing, Result, selector, metadata, Home and Detail assets are linked as full-size source originals. Local raw SHA matches their declared hashes where declared; the Asset Integrity Register still has baseline `BLOCKED_ACCESS`/visual-review gaps for several sources, so design/runtime visual acceptance remains open.
- **C01 Frozen counter:** P01/P02 raw SHA identity is verified, but each composite retains 03/03 counter/dots despite active M1 P01/P02. Frozen bytes remain unchanged pending design authority resolution.
- **Figma:** metadata mapping is present in the existing master board, but source image binaries remain `NOT_IMPORTED` after the network proxy upload timeout; no image import or visual parity claim is made.

### QA target mapping

The matrix binds guide screenshots to `P01_compose_component.png` and `P02_compose_component.png`; Processing to `01_image_recognizing_early.png`, `02_image_recognizing_late.png`, `03_fish_located.png`, and `04_species_recognizing.png`; and Result to `05_result_high.png` through `09_error_image_quality.png`. These are target IDs only. Rows whose Frozen source is missing or still under review explicitly carry `BLOCKED_INTEGRITY`, `MISSING`, or `REVIEW`; none is represented as Runtime PASS. `runtime_acceptance` remains `NOT_RUN`.


## PR #163 L2/L4 and quality-matrix audit

Reviewed live PR #163 comment `6244627483` against PR #163 Draft/Open/unmerged HEAD `81aefb1c0eae08df5ea6f451fc7c65be89a9c191`, current PR #162 Draft/Open/unmerged HEAD `43b8267167d8a948754fe3df7d20edf072955cf9`, and canonical main base `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`. The local Track B branch is not the PR #163 worktree; no claim is made that these edits are in PR #163 or #162, or on `main`. C01 is effective only inside the M1 work branch until reviewed/merged under repository policy.

- **L2 relationship coverage:** Feature Matrix and Unified Contract contain 15 features, 20 journeys, 22 routes, and 79 child-surface relationships. Feature↔Route and Feature↔Child Surface ownership match bidirectionally. Feature↔Journey has one unresolved mismatch: J18 is owned by `home_normal_v1` and `my_catches_v2` in Feature Matrix but only `home_normal_v1` in Unified Contract. L2 is therefore `REVIEW_REQUIRED`; both values remain in the JSON matrix. M1 scope covers 7 features, J01–J20, 9 route refs, and 41 ownership edges across 40 distinct child IDs. Owner-qualified matrix bindings show 23 exact row links and 18 feature-scope-only links.
- **Feature Matrix journey conflict J18:** Feature Matrix maps J18 to `home_normal_v1` and `my_catches_v2`; Unified Contract maps it only to `home_normal_v1`. No source file was changed in this Track B audit; resolve under the existing contract authority process.
- **L2 implementation/test traceability:** rows include existing Android implementation paths and test references. These are path links, not proof that each state has a complete assertion or that tests ran. State-specific unbound surfaces and missing authorities remain review gaps.
- **L4/quality fields:** rows now include token/component/motion/logic authorities, Gate IDs, Android commit/source SHA/version, APK SHA, Geometry, Pixel Diff, Motion, Logic, evidence URL/path, reviewer status, and final verdict. Exact runtime-bound values remain null/`NOT_RUN`; Gate B/C/D and all runtime quality results remain `NOT_RUN`, no APK or Run ID is asserted.
- **Gate A:** not fully passed. Capture Frozen originals and FishRecordDetail A-side sources are missing from this checkout; Technical Failure has no dedicated Frozen authority; the B-side Memory page is PARTIAL with `hifi_count=0`; several source entries remain REVIEW/BLOCKED_INTEGRITY. Canonical Unified Contract gate `G-FIRST-USE-GUIDE-ASSET` still records `USER_AUTHORIZED_THREE_PAGE_V1`. That global registry is unchanged; the M1 two-page decision is scoped to this work branch and does not claim a main/global authority update.
- **C01/C02 scope:** C01 P01/P02 is only an M1 branch-local decision; PR #162 is not merged, and `main` still reflects the canonical baseline. Processing timing now resolves to V3.1 `State_Machine_V3_1.json` minima 220/320/350/250ms plus real events; V1.1 2.8s is superseded for M1.
- **Unchanged evidence limits:** Frozen PNG bytes were not changed; Figma source-image binary import remains `NOT_IMPORTED`; visual Runtime remains `NOT_RUN`.

### Static validator discrepancy

`python3 scripts/design/verify_page_contract_v3.py` fails on Track B source SHA `9ccfec0f22f4b6b78d277e00b70c4ab36b20d3a6` with `effective Asset Registry expected 658 assets, found 669`. The validator hard-codes 658; `design/system_v3/06_Asset_System/Asset_Registry_V3_FINAL.json` contains 669 entries, 669 unique asset IDs, and 669 unique paths. Legacy `Asset_Registry_V3.json` contains 658 rows but 657 unique IDs because `pages_recognition_processing_references_ai_v1_v1_md` appears twice. The FINAL registry contains 12 unique IDs absent from legacy and has no duplicate IDs, accounting for 669 unique entries. This is a stale validator cardinality check. The registry and validator were left unchanged. Track A separately reported the same failure string on source SHA `82c49b8d7ced6568ee2d04e3ecbd1c10a06cc018`; that run is not reused as Track B evidence.

Audit checks above were run on the Track B working tree rooted at `9ccfec0f22f4b6b78d277e00b70c4ab36b20d3a6`; audit changes in this checkout are documentation/matrix-only. No exact-head Runtime or CI execution is asserted.

The expanded machine-readable matrix is the row-level ledger, with 65 records (49 page/state, 16 core component). The matrix is branch-local on `work/m1-track-b-design`; it is not part of PR #162 or #163 until explicitly integrated. No Gate A–D acceptance or Release PASS is claimed by this design audit.


## Latest exact-SHA checkpoint

## M1 authority checkpoint

At PR #163 source ffe285aff3818435815f769532fa221d3157917e, the current implementation still exposes the three-page guide. The approved P01/P02 assets already exist byte-identically in Draft PR #162 and were not regenerated; the raw P02 source SHA is `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`. PR #162 remains open/draft/unmerged, and the published Design Manager route could not be loaded by the available web surface in this audit. GitHub canonical main remains the live inspectable source and retains the three-page registration; therefore C01 is not reported as effective on main or implemented in the current candidate.

Figma source node metadata was synchronized on the PR #162 proposal, but PNG binary fills are still `NOT_IMPORTED`. Original GitHub Frozen bytes and hashes remain the visual source; no thumbnail or generated image is substituted. Frozen P01/P02 assets preserve their original SHA values. No API28 visual acceptance is available.
