# DM02 · 2026-10-16 高保真资源差异报告

- Inventory: `release/design_manager/DM02_HiFi_Asset_Inventory.json` (schema 1.0)
- Source scan: `2d7a75efba0ca1b66fc9be1e0860072a5f1b8a5d` → `ebac477330350972ae05613fe7e8717f467eea8f`; final main `72329c026cb4a42f4af3bc885f9f9b36c004ef2d` (initial inventory snapshot `ae7f492a649d20dc23e7f04bcffa49749c7d9e70`)
- Changed visual files scanned: 15; inventory records including absent expected outputs: 20
- Classification is provenance-based. Presence in GitHub does not itself establish Frozen authority.

## Existing and Authority-delta visual files

| Inventory | Design Manager | Visual / evidence file | Bytes | SHA-256 | Page / State or scope |
|---|---|---|---:|---|---|
| ARCHIVED | DEPRECATED | `design/pages/recognition/first_use_camera_guide/archive/P02_Landscape_Horizontal_Fish_Two_Page_Freeze_Superseded_20261016.png` | 2001322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | Not an active visual authority; kept byte-for-byte as archive. |
| ARCHIVED | DEPRECATED | `design/pages/recognition/first_use_camera_guide/archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` | 2042852 | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | Not an active visual authority; kept byte-for-byte as archive. |
| ARCHIVED | DEPRECATED | `design/pages/recognition/first_use_camera_guide/archive/P03_Landscape_Horizontal_Fish_Superseded_20261016.png` | 2001322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | Not an active visual authority; kept byte-for-byte as archive. |
| FROZEN | FROZEN | `design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png` | 1988159 | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` | P01 |
| FROZEN | FROZEN | `design/pages/recognition/first_use_camera_guide/frozen/P02_First_Use_Camera_Guide_Frozen.png` | 2042852 | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` | P02 |
| FROZEN | FROZEN | `design/pages/recognition/first_use_camera_guide/frozen/P03_First_Use_Camera_Guide_Frozen.png` | 2001322 | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` | P03 |
| FROZEN | FROZEN | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Overview_V1.png` | 2123657 | `2cf0ea161360888248fb42227d2a2511df64aa94a4c4d3af7c7ffd3f06a0aeea` | recognition_main_journey_overview_v1 |
| FROZEN | FROZEN | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Overview_V1.svg` | 176060 | `fcb7a89215760b08901bdb94c2181dc5f40bc8ff0af0324b8ed0c8b705e0db6b` | recognition_main_journey_overview_v1 |
| REVIEW | PARTIAL | `design/system_v3/00_Product_Master_Flow/assets/Recognition_New_States_HiFi_V1.png` | 649652 | `8cc7c04a4e15bf35ca0f307d0b95940d05901ead0cf6c66742a9f25d4d2e33e5` | recognition_new_states_hifi_v1 |
| REVIEW | PARTIAL | `design/system_v3/00_Product_Master_Flow/assets/Recognition_New_States_HiFi_V1.svg` | 163765 | `c47c2fa800d201d331c72c3aa6037404887d2567d7526445ecd6cded9ca1b9de` | recognition_new_states_hifi_v1 |
| FROZEN | OUTSIDE RECOGNITION ROUTE | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_difference_visual.png` | 17140 | `3160cac14df4e46705d6fecaeabe5bf07b6a28347ac31cda6363432a9305ea37` | brand_title_difference_visual_png |
| FROZEN | OUTSIDE RECOGNITION ROUTE | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_extraction_preview.png` | 17746 | `bbb4e039c3df2bc79db3efcc28c59d7260de7234361d0d1ff9b369db272025fe` | brand_title_extraction_preview_png |
| FROZEN | OUTSIDE RECOGNITION ROUTE | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_overlay_comparison.png` | 51703 | `68a50e23c14a248d9655e980cf14ca4fff1c8c0cb250585a0e3054ee1263167f` | brand_title_overlay_comparison_png |
| FROZEN | OUTSIDE RECOGNITION ROUTE | `design/system_v3/06_Asset_System/derived/brand_title_primary/brand_title_primary.png` | 11763 | `2dd41f5138e88e3bd48178dd26a15dfbb29c434536e4e3cc887fe2db13bdfddc` | brand_title_primary |
| FROZEN | OUTSIDE RECOGNITION ROUTE | `design/system_v3/06_Asset_System/derived/normal_home_camera_primary_v1/normal_home_camera_primary_v1.png` | 53887 | `fb6417e372d08328ac8559d004d1c6f5b03fc17551646be46031df641660e8f2` | normal_home_camera_primary_v1 |

### Status mapping

- P01/P02/P03 are the existing single-page `FROZEN` visual children; their manifest SHA and dimensions are checked independently.
- Three files under `first_use_camera_guide/archive/` are `ARCHIVED` and map to manager `DEPRECATED`; they are not current sources.
- Overview PNG/SVG are `FROZEN_BUSINESS_OVERVIEW`; their manager label is `FROZEN` for the overview asset only, never for a product page.
- New States PNG/SVG are `REVIEW` / manager `PARTIAL`; superseded guide artwork is not promoted, and the remaining additive review is explicitly identified.
- Five approved Normal Home / asset-system images are in the scanned delta but outside Recognition page/state navigation. Their inventory records keep the underlying source status and assign no Recognition manager state.

## Missing expected source binaries

| Inventory | Design Manager | Expected visual | Expected bytes / dimensions | Expected SHA-256 | Authority / blocker |
|---|---|---|---|---|---|
| MISSING | BLOCKED_INTEGRITY | `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Portrait_Horizontal_Fish_Frozen.png` | 2184165 / 941×1672 px | `6d67d03045f5f3dfb66adad10921d7456a2c82620b7b68f156cb98be74f1908c` | CAPTURE_READY_PORTRAIT_HORIZONTAL |
| MISSING | BLOCKED_INTEGRITY | `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png` | 2079370 / 1672×941 px | `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18` | CAPTURE_READY_LANDSCAPE_HORIZONTAL |
| MISSING | MISSING | `湖畔相机权限提示界面.png` | 1794959 / 941×1672 px | `1feced3a5d6f7a7d3ef968edcf8a5a834d216fab40707cf84c31658be5417d06` | design/system_v3/08_Page_Contracts/recognition/capture/Camera_Permission_Denied_Visual_Contract_V1.md |
| MISSING | PARTIAL | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Master_Board_V1.png` | not recorded / 5120×2880 px | `not recorded` | recognition_main_journey_master_board_v1 |
| MISSING | PARTIAL | `design/system_v3/00_Product_Master_Flow/assets/Recognition_Main_Journey_Master_Board_V1.svg` | not recorded / 5120×2880 px | `not recorded` | recognition_main_journey_master_board_v1 |

## Handling and closure

- No duplicate guide pages were created: the existing P01/P02/P03 registry children remain the canonical routes.
- Capture Ready images are freeze-approved in their manifest, but both binary imports are `PENDING`; the manager shows `BLOCKED_INTEGRITY` and no image.
- The camera-permission contract is Frozen but its declared PNG is absent; the manager shows `MISSING`. OS permission UI remains platform-owned.
- Master Board JSON/MD remain its machine-readable status source; the expected standalone PNG/SVG are absent behind `BLOCKED_CONNECTOR_UPLOAD_STEP`. The manager can show the schema table only, not a substitute board.
- The release report must keep `DESIGN_MANAGER_HIFI_PUBLISH` at `BLOCKED` until the published target binaries exist under their canonical paths and their approved hashes validate.

## Recheck of the unmerged Control Plane proposal

PR #153 (`control/first-use-guide-two-page-20261016`, head `1d463ebff473c402725ffaa118908ad147d3967a`) is Draft / Open / Unmerged and is explicitly marked `CONTROL_PLANE_PROPOSAL_PENDING_MAIN_AUTHORIZATION`. Its visual delta was scanned separately so it cannot be confused with effective `main` inventory:

| Proposal-only finding | Branch evidence | Current `main` / production disposition |
|---|---|---|
| Capture landscape PNG at `design/pages/recognition/capture/frozen/Recognition_Capture_Ready_Landscape_Horizontal_Fish_Frozen.png` | Exists only on PR #153; 2,079,370 bytes, 1672×941, actual SHA-256 `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18f` (64 characters). | Absent from `main`; production URL returns 404; current Manager state stays `BLOCKED_INTEGRITY`. Current `main` manifest and Registry each record `c5d7f126ca4820f4299e8e5a27be64b26bc3aaeec026e6bd8bedac2c1c33b18` (63 characters). The candidate hash differs, so no import was made. Resolve the canonical SHA through Control Plane review before importing. |
| First-use P02 reassignment | PR #153 renames the former active P03 image to `P02_Landscape_Horizontal_Fish_Frozen.png` without changing its bytes (SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`) and proposes a two-page sequence. | Not effective. Current `main` continues to serve the approved P01/P02/P03 mapping and hashes supplied for this release. |
| Former active P02 visual | PR #153 removes the active `P02_First_Use_Camera_Guide_Frozen.png` identity; the same bytes are already kept in the current archive (SHA-256 `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f`). | Current `main` active P02 stays unchanged until the proposal completes its required Control Plane review and merge. |

The current release follows the effective `main` Authority and does not import or activate PR #153 content. The proposal must be resolved through its own governance path before any future guide navigation or state IDs change.
