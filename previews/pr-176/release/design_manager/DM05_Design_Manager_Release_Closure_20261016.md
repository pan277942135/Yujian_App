# DM05 · Design Manager 高保真发布收口报告

Date: 2026-10-16 (UTC)

Repository: `pan277942135/Yujian_App`

Production: <https://pan277942135.github.io/Yujian_App/design/manager/>

Final design-site source / deploy SHA: `72329c026cb4a42f4af3bc885f9f9b36c004ef2d`

## 1. 发布基线

| Item | Exact evidence | Status |
|---|---|---|
| Effective design Authority | PR #151 merge `ebac477330350972ae05613fe7e8717f467eea8f`; the current-main guide mapping is P01/P02/P03. | PASS |
| Site implementation PR | PR #154, `090e7b0b2423d8cc988ae1f787461af157e8c535` → merged at `06d9574ac832e01a96be51dc37e30cf183b57b0d`. | Merged |
| Browser readiness fix | PR #155, `1d8e77c150ca4bbb89a56ade6675274fae24ab69` → merged at `72329c026cb4a42f4af3bc885f9f9b36c004ef2d`. | Merged |
| Site-source `main` SHA | `72329c026cb4a42f4af3bc885f9f9b36c004ef2d`. | PASS |
| Pages deployment | [Actions run 38927674288](https://github.com/pan277942135/Yujian_App/actions/runs/38927674288); build and deploy jobs both completed `success`. | PASS |
| Deploy SHA / build marker | `72329c026cb4a42f4af3bc885f9f9b36c004ef2d`; verified in HTML meta and `build.json`. | PASS |
| Design Governance | Main run `38927674363`; PR #154 run `38925600331`; PR #155 run `38927018987`. | PASS |
| Control Plane Admission | Main run `38927674312`; PR #154 run `38925605680`; PR #155 run `38927019019`; local admission also passed. | PASS |
| PR #145 | Draft / Open / Unmerged; base `main@ae7f492a649d20dc23e7f04bcffa49749c7d9e70`; live head `f498c2ff85d174b9385ca7b13811fc2016a993c7`. | Preserved |
| PR #153 | Draft / Open / Unmerged; head `1d463ebff473c402725ffaa118908ad147d3967a`; `CONTROL_PLANE_PROPOSAL_PENDING_MAIN_AUTHORIZATION`. | Not applied |
| Published site version | `design-manager-build` meta and `build.json` both show `72329c026cb4a42f4af3bc885f9f9b36c004ef2d`. | PASS |

PR #153 proposes a two-page first-use guide, which differs from this task's explicit current-main P01/P02/P03 resource set. It was not merged or used to change the live Registry. Its Capture landscape candidate is separately recorded below because its actual SHA does not match the current-main expected value.

This closure report is evidence under `release/design_manager/`; its later documentation-only merge does not alter the deployed Manager source. The production version remains bound to the Pages run and SHA above.

## 2. 高保真资源矩阵

Online verification downloaded every visual file currently present on `main`: 15/15 returned HTTP 200 and matched its source SHA-256. The live verifier also checked the five expected missing paths as HTTP 404. `Design Manager Deep Link` points to the current route; historical and out-of-feature assets intentionally have no Recognition page route.

| Resource / ID | Authority path | Source SHA-256 | Online SHA-256 | Design state | Manager deep link | Result |
|---|---|---|---|---|---|---|
| P01 | `design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png` | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` | Same | FROZEN · 941×1671 · 1,988,159 bytes | [P01](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p01) | PASS |
| P02 | `design/pages/recognition/first_use_camera_guide/frozen/P02_First_Use_Camera_Guide_Frozen.png` | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | Same | FROZEN · 941×1671 · 2,042,852 bytes | [P02](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p02) | PASS |
| P03 | `design/pages/recognition/first_use_camera_guide/frozen/P03_First_Use_Camera_Guide_Frozen.png` | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | Same | FROZEN · 941×1672 · 2,001,322 bytes | [P03](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p03) | PASS |
| Journey overview PNG | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Overview_V1.png` | `2cf0ea161360888248fb42227d2a2511df64aa94a4c4d3af7c7ffd3f06a0aeea` | Same | FROZEN_BUSINESS_OVERVIEW · 5120×2880 · 2,123,657 bytes; not a single-page Hi-Fi | [Overview](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/main_journey_overview) | PASS |
| Journey overview SVG | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Overview_V1.svg` | `fcb7a89215760b08901bdb94c2181dc5f40bc8ff0af0324b8ed0c8b705e0db6b` | Same | FROZEN_BUSINESS_OVERVIEW · 5120×2880 · 176,060 bytes | [Overview](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/main_journey_overview) | PASS |
| New States review PNG | `design/system_v3/00_Product_Master_Flow/assets/Recognition_New_States_HiFi_V1.png` | `8cc7c04a4e15bf35ca0f307d0b95940d05901ead0cf6c66742a9f25d4d2e33e5` | Same | REVIEW → PARTIAL · 2800×1600 · 649,652 bytes | [Review](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/new_states_review) | PASS |
| New States review SVG | `design/system_v3/00_Product_Master_Flow/assets/Recognition_New_States_HiFi_V1.svg` | `c47c2fa800d201d331c72c3aa6037404887d2567d7526445ecd6cded9ca1b9de` | Same | REVIEW → PARTIAL · 2800×1600 · 163,765 bytes | [Review](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/new_states_review) | PASS |
| Archived P02 landscape | `design/pages/recognition/first_use_camera_guide/archive/P02_Landscape_Horizontal_Fish_Two_Page_Freeze_Superseded_20261016.png` | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | Same | ARCHIVED → DEPRECATED · 941×1672 · 2,001,322 bytes | — Historical only | PASS |
| Archived P02 portrait | `design/pages/recognition/first_use_camera_guide/archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | Same | ARCHIVED → DEPRECATED · 941×1671 · 2,042,852 bytes | — Historical only | PASS |
| Archived P03 landscape | `design/pages/recognition/first_use_camera_guide/archive/P03_Landscape_Horizontal_Fish_Superseded_20261016.png` | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | Same | ARCHIVED → DEPRECATED · 941×1672 · 2,001,322 bytes | — Historical only | PASS |
| Brand-title diff evidence | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_difference_visual.png` | `3160cac14df4e46705d6fecaeabe5bf07b6a28347ac31cda6363432a9305ea37` | Same | Source APPROVED; outside Recognition route | — | PASS |
| Brand-title extraction evidence | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_extraction_preview.png` | `bbb4e039c3df2bc79db3efcc28c59d7260de7234361d0d1ff9b369db272025fe` | Same | Source APPROVED; outside Recognition route | — | PASS |
| Brand-title overlay evidence | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_overlay_comparison.png` | `68a50e23c14a248d9655e980cf14ca4fff1c8c0cb250585a0e3054ee1263167f` | Same | Source APPROVED; outside Recognition route | — | PASS |
| Brand-title primary | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png` | `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` | Same | FROZEN; outside Recognition route | — | PASS |
| Normal Home camera primary | `design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png` | `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` | Same | FROZEN; outside Recognition route | — | PASS |
| Capture Ready portrait | `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png` | Expected `6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c` | — (404) | Freeze recorded; import PENDING → BLOCKED_INTEGRITY · 941×1672 · 2,184,165 expected bytes | [Portrait gate](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/capture_portrait) | BLOCKED |
| Capture Ready landscape | `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png` | Current-main record: `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18` (63 characters) | — (404) | Freeze recorded; import PENDING → BLOCKED_INTEGRITY · 1672×941 · 2,079,370 expected bytes | [Landscape gate](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/capture_landscape) | BLOCKED |
| Capture landscape proposal candidate | Same path as above; exists only on PR #153 | Candidate actual SHA `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18f` | Not deployed | REVIEW only; actual 64-character SHA differs from current-main 63-character identity | — Not current Authority | BLOCKED |
| Camera permission visual | `湖畔相机权限提示界面.png` | Expected `1feced3a5d6f7a7d3ef968edcf8a5a834d216fab40707cf84c31658be5417d06` | — (404) | Contract FROZEN; source MISSING · 941×1672 · 1,794,959 expected bytes | [Permission gate](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/camera_permission_visual) | BLOCKED |
| Master Board PNG | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Master_Board_V1.png` | Not recorded | — (404) | Board schema FROZEN_REVIEW_BOARD; Manager PARTIAL; import gate `BLOCKED_CONNECTOR_UPLOAD_STEP` · 5120×2880 | [Master Board](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/master_board_v1) | BLOCKED |
| Master Board SVG | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Master_Board_V1.svg` | Not recorded | — (404) | Board schema FROZEN_REVIEW_BOARD; Manager PARTIAL; import gate `BLOCKED_CONNECTOR_UPLOAD_STEP` · 5120×2880 | [Master Board](https://pan277942135.github.io/Yujian_App/design/manager/#page/recognition_flow_v1_2/hifi/boundary_state_release_gate/master_board_v1) | BLOCKED |

The first three guide SHA values above are the user's stated P01/P02/P03 identities and are the approved live sources under the current-main Registry. The five missing current-main paths are intentionally absent from the page preview; their status text is not a replacement visual.

## 3. 规范和 Authority 更新矩阵

| Authority area | Change / unchanged source | Closure |
|---|---|---|
| Page Contract | Added `design/manager/RECOGNITION_HIFI_PRESENTATION_CONTRACT_V1.md` for Manager route identity, Page/State metadata, source display, entry/trigger/input, actions/next, back/cancel/retake, missing-source behavior and Runtime separation. Existing Capture, Processing and Result product contracts remain authoritative and were not rewritten. | PASS |
| First-use guide navigation | Updated `design/registry/experience_registry_v1.json` additively. Existing `recognition_flow_v1_2 → 07 · 首次拍照教学 → P01/P02/P03` IDs stay stable; overview and boundary routes are added. | PASS |
| Other navigation / Registry | Recognition Result RR01–RR08 and Normal Home NH01–NH07 stay registered. Source `navigation.json`, V3.1 Page Registry and Effective Authority Registry were not version-bumped because their approved content did not change. | PASS |
| Motion | Effective sequence remains `CAPTURED ≥220ms → DETECTING ≥320ms → OUTLINE ≥350ms → CLASSIFYING ≥250ms → RESULT_READY`, with both minimum beat and real pipeline event required. V1.2 visual-motion reference remains separate; no thresholds or new durations were authored. | PASS |
| Responsive | Existing product matrices remain in force for 320/360/411dp, safe areas / WindowInsets, portrait / landscape, font scales and keyboard avoidance. Manager browser checks cover 1440/768/390/360/320 CSS px. | PASS |
| Accessibility | Manager controls have accessible names/focus states, 44 CSS px minimum targets, modal close/Escape/backdrop behavior, focus restoration and reduced-motion handling. Existing Capture/Result TalkBack and 44×44dp product contracts remain unchanged; Android Runtime is not claimed. | PASS |
| Asset and SHA registry | Existing P01/P02/P03, overview and review identity records are byte-checked. Missing sources remain missing. Current-main Capture landscape SHA is 63 characters and the candidate SHA on PR #153 is 64 characters; neither was silently rewritten or imported. | BLOCKED |
| Feature Matrix / Unified Contract / Evidence Gate | No content change was needed for the Manager-only routes; current Control Plane Admission validates the six canonical layers. | PASS |
| PR #153 guide proposal | Two-page guide is Draft / Open / Unmerged and conflicts with the explicit three-page current-main target. | NOT APPLIED |

The Recognition Processing V3.1 state machine remains the effective timing source. The older V1.2 motion document retains its separate frozen visual pacing table and was not merged with the V3.1 durations. First-use/permission/Result motion receives no new duration in this presentation-only release.

## 4. 测试证据

| Evidence | Result / trace |
|---|---|
| Static validators | `design_manager.py validate`, `summary`, navigation guard, hi-fi release validator and Control Plane Admission all PASS. |
| Negative integrity checks | Missing required Frozen source and mutated Frozen byte each cause validator failure; invalid Hash and absent-source preview do not fall back or render a substitute. |
| Browser / responsive | Final Pages run verifies direct Hash, refresh, back/forward, image readiness, zoom/Fit/Escape/focus, 1440/768/390/360/320 widths, review/missing states and no HTTP/console/page errors. |
| Production visuals | 15 present visual files: 15 HTTP 200 and all online SHA values match source. Five expected missing files: 5 HTTP 404 as registered. |
| Recognition contract links | 13/13 Registry-linked contract paths returned HTTP 200; guide `manifest.json` and `navigation.json` returned HTTP 200. |
| Direct live P02 view | Browser shows build `72329c026cb4`, route `#page/recognition_flow_v1_2/hifi/capture_first_use_guide/p02`, image complete at 941×1671, `object-fit: contain`, and the expected manifest SHA. |
| CI | PR #154: Design Governance `38925600331`, Control Plane Admission `38925605680`, Android CI `38925601026` all PASS. PR #155: `38927018987`, `38927019019`, `38927018984` all PASS. Main: Design Governance `38927674363`, Control Plane Admission `38927674312` PASS. |
| Deploy / screenshots | [Pages run 38927674288](https://github.com/pan277942135/Yujian_App/actions/runs/38927674288) passed build/deploy, SHA verification, live browser checks and evidence upload. Artifact IDs/digests and screenshot filenames are in DM04. |
| Failure and repair | Run `38926382780` had a browser image-load race on P02 (zero natural dimensions at read time). Source SHA check passed. PR #155 added image readiness wait; final run passed. |
| Runtime separation | P01–P03 Design status is FROZEN; Android Runtime status remains NOT_RUN. |

The final Pages run uploaded `github-pages` artifact `12056738754` (188,934,315 bytes; `sha256:8cd27c8798addf41b4c2db9b2ec46231660f5fe85dd6e263b27abee68c7ad0a2`), browser screenshot artifact `12057012967` (`sha256:7f92dac1f29d665ac2dd02ee8e2b719c57a6f990b808b2a11337749fc8c02d74`), and live screenshot/verification artifact `12056788832` (`sha256:863266d8fda011d669128069e2635fbfe7ffd619edb5f4251a263ef4b7a6959b`). The live screenshots include `desktop-1440.png`, `tablet-768.png`, `mobile-390.png`, `lightbox-1440.png` and `overview-1440.png`.

## 5. Final Gate

| Gate | Status | Reason |
|---|---|---|
| Design Manager Pages deployment | PASS | Production is on the exact successful Pages run SHA; resources, route behavior, screenshots and live links are verified. |
| Control Plane Admission / Design Governance | PASS | Local and GitHub checks pass; PR #145 stays Draft/Open/Unmerged. |
| `DESIGN_MANAGER_HIFI_PUBLISH` | **BLOCKED** | Five current-main target files are absent from production. The Capture landscape proposal candidate also fails exact SHA identity against current main (64 vs 63 characters). |
| Android Runtime for P01/P02/P03 | NOT_RUN | Design FROZEN state is not Runtime evidence. |

`DESIGN_MANAGER_HIFI_PUBLISH = BLOCKED`.

### Exact blockers and next actions

1. **Capture Ready portrait:** canonical path absent; approved identity is recorded, import remains PENDING. Obtain the exact original bytes and validate the recorded SHA/dimensions before import.
2. **Capture Ready landscape:** canonical path absent. Current-main manifest/Registry contain a 63-character SHA. PR #153's draft candidate is 2,079,370 bytes at 1672×941 but hashes to a different 64-character SHA ending `b18f`. Resolve and approve a valid SHA through the canonical Control Plane process before importing. Do not update the PNG or silently repair the Registry value.
3. **Camera permission PNG:** source missing at `湖畔相机权限提示界面.png`; obtain the contract-declared original and validate SHA before making a preview.
4. **Master Board PNG/SVG:** `BLOCKED_CONNECTOR_UPLOAD_STEP`; import the approved standalone outputs from their canonical source. Do not use the Journey Overview or a recreated HTML image as a substitute.
5. **Guide proposal:** PR #153 remains a separate Draft / Open / Unmerged Control Plane proposal for a two-page guide. Resolve it through its own authorization process before any future guide child IDs or mappings change.

Re-run the hi-fi release validator, Governance, Control Plane Admission and Pages live checks after those sources and authority decisions are resolved. Keep the current P01/P02/P03 route until an authorized main Authority change says otherwise.
