# Work M1 · Baseline Report

**Task:** `YUJIAN_M1_PARALLEL_DELIVERY_V1`  
**Repository:** `pan277942135/Yujian_App`  
**Baseline verified:** 2026-10-18 09:12 Asia/Shanghai  
**Canonical baseline:** `main@0d3238e8a41b87363e9a17b04c9cc45fca4c426d`  
**Stage 0 Gate:** `PASS` — baseline, P0 register, and three track scopes recorded.

## Remote state

| Item | Verified state |
|---|---|
| `main` | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` (`merge(work): consolidate YuJian Android Work and recognition QA into main`) |
| PR #145 | Closed and merged at `cc642f198b6641108589076eb70de600f0e47d3a`; included in the current main consolidation. |
| PR #150 | Closed and merged at `2d7a75efba0ca1b66fc9be1e0860072a5f1b8a5d`. |
| PR #153 | Closed, not merged. Its two-page proposal did not enter main. |
| PR #160 | Closed and merged at the current canonical main SHA. It integrated #145/#157/#158/#159. |
| PR #162 | Existing Draft/Open P02-removal implementation, based on this exact main SHA; head `43b8267167d8a948754fe3df7d20edf072955cf9`. Keep Draft and unmerged. |

Repository Control Plane Admission was run on the canonical checkout and passed: 6 layers; 21 static files; 15 features; 20 journeys; 22 routes; 79 child surfaces; 45 tasks; 0 Feature Matrix gaps; 0 Unified Contract gaps. The local checkout is exactly the reported SHA.

## Current CI and artifacts

- Exact-main Android CI run [39016335427](https://github.com/pan277942135/Yujian_App/actions/runs/39016335427): build, JVM tests, Camera harness contract tests, APK and AndroidTest APK assembly, asset/model packaging, and lint passed. Its seven API28 runtime jobs are still queued; the run is not an M1 runtime pass.
- Separate Camera Runtime Harness P0 run [39016335503](https://github.com/pan277942135/Yujian_App/actions/runs/39016335503) is still queued at the same SHA.
- Four installable/bundled APK artifacts exist on run 39016335427 and are baseline-only. They are not an M1 release APK.
- PR #162 head run [39084647628](https://github.com/pan277942135/Yujian_App/actions/runs/39084647628), Design Governance [39084647640](https://github.com/pan277942135/Yujian_App/actions/runs/39084647640), and Control Plane Admission [39084647595](https://github.com/pan277942135/Yujian_App/actions/runs/39084647595) passed on `43b8267`. Its branch-push Android CI run [39084644829](https://github.com/pan277942135/Yujian_App/actions/runs/39084644829) is pending; no API28 result is claimed.
- This environment has JDK 17 but no local `adb` or emulator. API28 acceptance must come from the approved CI runner.

## Source and evidence audit

- The main branch still registers the original three-page guide. The user-approved two-page authority is already implemented in PR #162: P01 portrait phone + horizontal fish; P02 landscape phone + horizontal fish. The old vertical-fish P02 is inactive there and retained only in the archive. Do not duplicate those existing images or replace their original bytes.
- The Android guide policy on main records `ASSET_SOURCE_BLOCKED`; the runtime hero and three error-example image references are null. The Frozen full-page PNGs are visual authorities, not runtime backgrounds, so their presence does not close this runtime image-source gap.
- Recognition Result initializes save controls idle (`saveRequested=false`; a loading destination is selected only after a save request). FishRecordDetail A-side selects evidence-fit media with a backdrop and verified-letterbox trimming. Both fixes still need exact-head API28 screenshots and bounds evidence because the previous device reports predate this baseline.
- Recognition Result's numeric metadata editor uses a non-scrollable `ModalBottomSheet` without explicit IME-aware sizing/inset handling. This overlaps the user's previously confirmed keyboard-height defect and remains a P0 implementation check.
- Guest save now derives a stable record ID from `clientRecordId` and uses synchronous preference `commit()`; the authenticated path validates/read-backs by client record ID. Runtime coverage of both save and return destinations remains absent.
- The Journey Acceptance Matrix is globally `NOT_RUN`. The 66-case catalog has 66 `NOT_RUN` cases; the 10 E2E scenarios are also `NOT_RUN`. That 66-case catalog explicitly excludes post-save flow and FishRecordDetail A/B, so it cannot prove the requested end-to-end path.

The detailed, field-complete gap list is [M1_GAP_REGISTER.json](M1_GAP_REGISTER.json). It distinguishes implementation gaps, design authority drift, and evidence gaps; Track A/B/C may update each status only with exact-SHA evidence.

## Track scopes and ownership

| Track | Start point | Ownership | Acceptance |
|---|---|---|---|
| A · Android | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` | `app/src/main/**`, excluding the guide files owned by PR #162; capture/processing/result/save/detail navigation, Result IME fix, prior CTA/detail visual fixes, and unit tests. | Functional flow and recovery paths pass; no change to model/API/frozen contracts; commit and source SHA recorded. |
| B · Design & assets | Existing PR #162, based on `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` | Audit its active asset paths, exact bytes/SHA, registry/manifest/Feature Matrix/contract synchronization, and visual delta. Do not regenerate P01 or the reused landscape P02; do not duplicate or resurrect vertical-fish P02. | Two active guide pages resolve consistently; Design Governance and source/hash checks pass; design/runtime gaps are explicit. Keep PR #162 Draft/unmerged. |
| C · QA / Runtime / CI | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` | `app/src/androidTest/**` additions outside PR #162-owned guide tests, QA execution reports/catalog extensions, exact-head CI/runtime evidence, and device screenshots/bounds/Pixel Diff. | Approved tests execute with exact source/APK SHA; API28 Pixel_2 1080×1920 gates are reported without cross-SHA evidence reuse. |

The M1 integration branch is `work/m1-parallel-delivery-v1`; track worktrees are isolated. Stage 1 begins from the baseline above. Each later integration records its new HEAD and reruns the applicable CI/runtime gate.

## Release disposition

`M1_RELEASE_PASS` is **not** supported at baseline. P0 gaps are listed in the register; the current main runtime jobs remain queued and all declared journey execution cases remain `NOT_RUN`.
