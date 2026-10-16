# DM01 · Design Manager 发布基线审计

- Final audit date: 2026-10-16 (UTC)
- Repository: `pan277942135/Yujian_App`
- Pages source `main` / deploy baseline: `72329c026cb4a42f4af3bc885f9f9b36c004ef2d`
- Required Control Plane Admission on this branch: `PASS`
- Production: <https://pan277942135.github.io/Yujian_App/design/manager/>

## Live repository and Authority state

| Item | Rechecked state | Release disposition |
|---|---|---|
| `main` | `72329c026cb4a42f4af3bc885f9f9b36c004ef2d` | Final source and Pages deploy SHA. |
| PR #145 | Draft / Open / Unmerged; base `main@ae7f492a649d20dc23e7f04bcffa49749c7d9e70`; live head `f498c2ff85d174b9385ca7b13811fc2016a993c7` | Product/control work remains isolated and untouched. |
| PR #150 | Merged at `2d7a75efba0ca1b66fc9be1e0860072a5f1b8a5d` | Work-state validator repair. |
| PR #151 | Merged at `ebac477330350972ae05613fe7e8717f467eea8f` | Current effective V3.1 design Authority and Recognition guide P01–P03. |
| PR #152 | Merged at `ae7f492a649d20dc23e7f04bcffa49749c7d9e70` | Control Plane admission repair; no visual asset changes. |
| PR #153 | Draft / Open / Unmerged; head `1d463ebff473c402725ffaa118908ad147d3967a`; base `ae7f492a649d20dc23e7f04bcffa49749c7d9e70` | Pending Control Plane proposal that changes the guide to two pages. It is not effective on `main` and was not applied to this release. |
| PR #154 | Merged at `06d9574ac832e01a96be51dc37e30cf183b57b0d` | Design Manager hi-fi UI, route, Registry and release validators. |
| PR #155 | Merged at `72329c026cb4a42f4af3bc885f9f9b36c004ef2d` | Browser image-readiness race fix; no product or visual Authority changes. |

The effective guide Authority on `main` retains three stable children P01/P02/P03, with hashes and paths recorded in the current guide manifest and Registry. PR #153's branch reports `CONTROL_PLANE_PROPOSAL_PENDING_MAIN_AUTHORIZATION`, retires the active P03 ID and changes the active guide to P01/P02. This audit follows the explicit current-main Authority; it does not merge, cherry-pick, or reinterpret that draft proposal.

## Project Master State and Control Plane

`AGENTS.md` directs all Work through `CONTROL_PLANE_REGISTRY.yaml`, which resolves the canonical static registry from `main` and allows `PROJECT_MASTER_STATE.yaml` / `WORK_EXECUTION_STATE.json` to remain branch-local. No `PROJECT_MASTER_STATE.yaml` exists on `main`.

The live PR #145 branch contains a snapshot with `execution_baseline_sha=4202d9d3ec8476360426506659b1fea6233b9667`, base `main@ae7f492...`, and `DRAFT_OPEN_UNMERGED`. That snapshot is older than the live PR head `f498c2ff...` and the current `main@72329c0...`; the live repository and PR metadata take precedence. The snapshot and PR branch were read-only during this release.

`CONTROL_PLANE_REGISTRY.yaml` on the release baseline declares the six-layer single entry point. `python3 scripts/validate_control_plane_admission.py` passes on this `design/` proposal branch in `CONTROL_PLANE_CHANGE` mode; the static registry, Feature Matrix, Phase/Task Machine, and Unified Contract validators all report PASS. No Control Plane file was changed by the Design Manager release.

## Local, Registry, and production comparison

| Area | Current `main` / Registry | Production after final deploy | Result / handling |
|---|---|---|---|
| First-use guide | Existing `recognition_flow_v1_2 → 07 · 首次拍照教学 → P01/P02/P03`; manifest, navigation and page/state hashes agree. | Existing submenu and each direct Hash load; original image bytes match all three approved hashes. | Kept the same route and child IDs; no duplicate guide page. |
| Journey overview | Approved overview PNG/SVG registered as `FROZEN_BUSINESS_OVERVIEW`, explicitly not a single-page visual. | Independent preview route, full image, source link and linked registered routes. | Added the Manager route and retained its scope disclaimer. |
| New-state board | PNG/SVG source is review-only; superseded guide art is not active. | Real review source shown as `PARTIAL`; not labeled Frozen. | Added an explicit review route and state mapping. |
| Capture / permission / Master Board targets | Five output binaries are absent on `main`; current manifests provide expected identities or import gate. | Expected missing paths return 404 and Manager displays the recorded missing/integrity state without an image or broken link. | No substitute artwork; exact paths and next import action are in DM02 / DM05. |
| Specs and contracts | Current Authority links resolve from the Registry; canonical product contracts remain unchanged. | All 13 Recognition contract links checked against Pages returned HTTP 200. | Exposed links in the metadata panel; no competing product contract created. |
| Preview controls and metadata | Before release, Manager did not provide this release's image dialog and complete metadata view. | Original-ratio preview, zoom, Fit Window, source link, metadata and stable routes are present. | Implemented in the Manager-only presentation contract. |
| Browser evidence | Prior workflow did not provide live-byte/browser-route evidence. | Pages workflow verified the published SHA, source bytes, responsive routes and interactions. | Build/deploy evidence is linked in DM04/DM05. |

## Pages deployment comparison

| Checkpoint | Deploy source / marker | Outcome |
|---|---|---|
| Initial audit before release PR | `ebac477330350972ae05613fe7e8717f467eea8f` | Existing site loaded, but the build marker lagged current `main`; no journey overview route. |
| Final production deployment | `72329c026cb4a42f4af3bc885f9f9b36c004ef2d` | Pages run `38927674288` succeeded; build marker and `build.json` equal the deployed commit. |
| Final live byte/browser checks | Run `38927674288`, deploy job | P01/P02/P03, overview and review assets match source SHA; responsive/browser gate and evidence upload pass. |

## Remaining release blockers

The Manager deployment itself is `PASS`. The separate `DESIGN_MANAGER_HIFI_PUBLISH` gate remains `BLOCKED` because the current effective `main` / production release is missing the Capture Ready portrait PNG, Capture Ready landscape PNG, camera permission PNG, and Master Board PNG/SVG. Their current legal Manager states are `BLOCKED_INTEGRITY`, `MISSING`, and `PARTIAL` as appropriate; no missing source is presented as a Frozen image. PR #153 contains a landscape Capture Ready candidate only on its unmerged proposal branch, but its actual 64-character SHA ends in `b18f` while current `main` records a 63-character expected SHA ending in `b18`; it is not safe to import until that Authority mismatch is resolved through the Control Plane.
