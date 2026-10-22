# DM06 · First-use Camera Guide V1.2 — Two-page Closure

**Date:** 2026-10-17  
**Repository:** `pan277942135/Yujian_App`  
**Branch:** `fix/recognition-guide-two-page-authority-v1`  
**Status:** IMPLEMENTED ON BRANCH · CI / RUNTIME ACCEPTANCE PENDING  
**Active spec:** `design/pages/recognition/first_use_camera_guide/First_Use_Camera_Guide_Spec_V1_2.md`

## 1. Decision

The active first-use guide has exactly two pages:

| Page | Phone | Fish | Action |
|---|---|---|---|
| P01 | Portrait | Horizontal | 下一张 |
| P02 | Landscape | Horizontal | 开始拍摄 |

The former portrait-phone / vertical-fish P02 is no longer an active visual page. The former P03 landscape PNG is promoted byte-identically to active P02. The former P03 active path is removed. Stable Master Board IDs after the retired MB-05 slot are not renumbered.

## 2. Binary path disposition

| Role | Path | Git blob SHA | SHA-256 |
|---|---|---|---|
| Active P01 | `design/pages/recognition/first_use_camera_guide/frozen/P01_First_Use_Camera_Guide_Frozen.png` | `3f73f744104777beee29bd2370ecac1f40197a1d` | `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50` |
| Active P02 | `design/pages/recognition/first_use_camera_guide/frozen/P02_Landscape_Horizontal_Fish_Frozen.png` | `2f46b2f2906f258eef8ca67181fc049a55d045af` | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |
| Archive only | `design/pages/recognition/first_use_camera_guide/archive/P02_Portrait_Vertical_Fish_Superseded_20261016.png` | `9c2dd981a04792c6ac51e3925727b4e4e6704034` | `fb092235fd043a42aa8250aba9dfdb91a78e96549dd5a63f8e89cf7433685f8f` |
| Archive provenance | `design/pages/recognition/first_use_camera_guide/archive/P03_Landscape_Horizontal_Fish_Superseded_20261016.png` | `2f46b2f2906f258eef8ca67181fc049a55d045af` | `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3` |

GitHub path checks performed on this branch:
- Active P02 landscape path exists and points to the former P03 blob.
- Former active `P02_First_Use_Camera_Guide_Frozen.png` path returns 404.
- Former active `P03_First_Use_Camera_Guide_Frozen.png` path returns 404.
- Both former blobs are retained under `archive/` for provenance; neither archive path is an active visual authority.

The Git blob SHA is unchanged across the P03-to-P02 promotion, proving byte identity at the Git object level. The asset verifier must still independently recompute SHA-256, dimensions and byte counts from raw PNG files in CI.

## 3. Updated surfaces

- Frozen spec V1.2, freeze decision, README, navigation and active asset manifest.
- Experience Registry and Effective Authority Registry.
- Master Board JSON/Markdown: P01/P02 active; former MB-05/P03 retired; later IDs remain stable.
- Main Journey asset manifest and business contract.
- Feature Matrix, Phase/Task State Machine and Unified Contract Registry.
- Android guide enum, policy, Compose pager, page indicator, reference geometry and unit / Compose / camera runtime tests.
- Design Manager asset validators and historical release reports marked as historical.

## 4. Acceptance status

| Gate | Status | Evidence |
|---|---|---|
| Active path migration | PASS | GitHub Contents API checks on the branch |
| Git blob byte identity | PASS | Active P02 blob equals former P03 blob; archived P02 blob equals the former active P02 blob |
| Registry / manifest sequence | PASS — static inspection | Active sequence contains P01/P02 only |
| Android source and tests updated | IMPLEMENTED | Source/test diffs are in this branch |
| Unit / Compose / API28 runtime CI | PENDING | Must be read from exact-head PR checks |
| Runtime visual parity / screenshot evidence | NOT_RUN | Must not be inferred from frozen design assets |
| Main branch update | NOT MERGED | Requires PR review/merge; main remains unchanged by this branch |

## 5. Release rule

Do not mark the task fully complete until the exact-head PR checks pass, including the asset verifier and Android unit / instrumentation jobs. If CI fails, fix the branch and rerun it. Keep Runtime visual parity and device evidence explicitly separate from the design freeze.
