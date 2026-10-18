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


## Stage 0 P0 acceptance supplement

The original baseline snapshot and its Stage 0 PASS remain historical. The current supplement checkpoint is REVIEW_REQUIRED. PR #163 was last remotely observed OPEN/DRAFT/UNMERGED at 81aefb1c0eae08df5ea6f451fc7c65be89a9c191. The six-layer branch Admission passed after private state restore at that audited SHA, and the initial missing-state failure remains preserved. The next candidate SHA still requires a refreshed private checkpoint and Admission run.

C01 remains open: PR #162 holds the two-page guide change, while the PR #163 product guide at its audited remote head still uses three pages. Track C QA now expects P01/P02. C02 code and QA updates align to V3.1 statically; exact Runtime trace is absent and the V1.2 Resolve collision remains. C03 hosted API28 preflight and formal Runtime have not run. See M1_ACCEPTANCE_CONFLICT_REGISTER.json and M1_GAP_REGISTER.json.

The M1 Design Authority Matrix and scope manifest have been reused in the candidate worktree. No PR is merged, no Frozen asset was modified, and no Release PASS is claimed. All three M1 tracks remain active.

## Exact-SHA CI checkpoint

PR #163 remains Draft/Open/Unmerged. The published candidate `1d98f48d59132d3d1ea9e226d0e32342459eb002` passed the hosted scope step, then Build Once failed at Android SDK manager lookup in Run 39092555676. API28 preflight/runtime jobs were skipped and no APK artifact exists. Android CI Run 39092555645 failed one of 211 unit tests; no APK steps executed. These are retained failure records, not release evidence. Minimal SDK lookup and test-timeline repairs are in progress locally and require a new exact-SHA rerun. The P0 gap register and release report carry the current blocker status.

## 6d16bb7 exact-source checkpoint

At `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`, full Control Plane Admission and private checkpoint recovery passed; hosted Control Plane and QA Test Users workflows also passed. Hosted API28 Run 39093186784 failed before Preflight at SDK license-pipeline exit handling. Android CI Run 39093186726 passed all unit and harness contract tests, assembled Debug APK, then failed AndroidTest compilation and uploaded no APK. The test and workflow fixes are recorded in `M1_LOCAL_REPAIR_EVIDENCE.md`; current candidate and release remain blocked pending a new exact-SHA run.


## Latest exact-SHA checkpoint

**Audit refresh:** current remote PR #163 remains Draft/Open/Unmerged at ffe285aff3818435815f769532fa221d3157917e; canonical main remains 0d3238e8a41b87363e9a17b04c9cc45fca4c426d. The earlier Stage 0 inventory is still the source of historical baseline facts; the new P0 compliance acceptance remains REVIEW_REQUIRED and does not inherit the earlier baseline gate.

- Android CI 39093846259: PASS on ffe285aff3818435815f769532fa221d3157917e; JVM/unit, camera runtime contract checks, Debug APK, AndroidTest APK compilation, packaging and lint completed.
- Hosted Runtime 39093844697: Build Once PASS and APK pair uploaded. API28 Preflight FAIL/BLOCKED_INFRA at the KVM character/read/write assertion. Formal Runtime SKIPPED.
- QA Test Users 39093844684 and canonical-promotion Admission 39093844689: PASS. The latter does not substitute for local PRODUCT_WORK Admission, which passed at ffe285aff3818435815f769532fa221d3157917e.
- PR #162 is still Draft/Open/Unmerged at 43b8267167d8a948754fe3df7d20edf072955cf9; main and PR #163 still have the three-page product contract. C01 is not reported as integrated.
- Build Once app APK SHA-256: bd98a57334d278e9a3a5129e9d344d8e0de4faf058b7294ee614921664058b3e; AndroidTest APK SHA-256: 249334802bd6ffe7084b9b96b604123db3b7b171640eb86b18344fa3a51749bb. This is a testable build only; no Runtime or Release PASS is claimed.


## Latest completed run — source 85dfe92

PR #163 remained Draft/Open/Unmerged. The latest completed tested source for this checkpoint was 85dfe9217e879e0f87d19b41801beb05e502aaa2; the next workflow-only commit adds exact-SHA Page Contract validation and exposes APK hashes in logs.

- Android CI Run 39095194583: PASS; build, JVM/unit, camera runtime contract checks, AndroidTest compilation, packaging and lint passed. API28 CI jobs were SKIPPED.
- M1 Hosted API28 Runtime Run 39095194693: Build Once PASS; API28 Preflight FAIL at KVM access; formal Runtime SKIPPED. Artifact 12124820081 digest `ab298c711af40ef00cc9820e6e60926687b67ed67736d1454a2c15b18c253c97`.
- QA Test Users Run 39095194548 and canonical-promotion Admission Run 39095194580 passed. The Admission result does not prove product-branch dynamic Work State.
- The exact APK hashes are recorded inside the Build Once artifact manifest; no API28 device result or Release PASS is claimed.


## Live-head Stage 0 checkpoint — b97f01f6790cea2e2f1cb4bfef07a38086223020

PR #163 is still Draft/Open/Unmerged. The canonical baseline remains `main@0d3238e8a41b87363e9a17b04c9cc45fca4c426d`.

- Android CI Run [39095681153](https://github.com/pan277942135/Yujian_App/actions/runs/39095681153) passed on `b97f01f6790cea2e2f1cb4bfef07a38086223020`; the Page Contract V3 validator ran and passed. API28-dependent jobs were skipped.
- Hosted API28 Runtime Run [39095681151](https://github.com/pan277942135/Yujian_App/actions/runs/39095681151): Build Once and APK pair upload passed. KVM Preflight failed; API28 emulator preflight and formal Runtime were skipped. This is `BLOCKED_INFRA`, not Runtime PASS.
- QA Test Users Run [39095681144](https://github.com/pan277942135/Yujian_App/actions/runs/39095681144) passed. Canonical-promotion Admission Run [39095681147](https://github.com/pan277942135/Yujian_App/actions/runs/39095681147) passed but does not validate private PRODUCT_WORK state.
- Installable test APK artifact [12124671626](https://github.com/pan277942135/Yujian_App/actions/runs/39095681151/artifacts/12124671626); app SHA-256 `b6d37938cdc34230415b7aea7fbf36536803e3908944c6a9665367d7c01c0890`, AndroidTest SHA-256 `356dfcffa5ef98ef2d1f496a2a2835b97e50776a714dab358d71ac03c7844f02`. Artifact ZIP SHA-256 `86b7fda5c6d542d1c57eb9076add52d1934d855aaa20ea9355364b65370ab77b`.
- Current private state checkpoint is stale at `ffe285aff3818435815f769532fa221d3157917e`; exact-head PRODUCT_WORK Admission remains NOT_RUN. Raw state stays private.
- The J17–J20 mapping conflict is recorded as P0; PR #162 remains Draft and is not effective on main or PR #163.


## Current stage plan and gates — live head b97f01f6790cea2e2f1cb4bfef07a38086223020

1. **Stage 0 · Baseline / Control Plane audit.** Canonical main baseline is `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`; this checkpoint audited PR #163 head `b97f01f6790cea2e2f1cb4bfef07a38086223020`. M1_SIX_LAYER_COMPLIANCE_AUDIT_V1 and M1_QUALITY_ENFORCEMENT_V1_1 are recorded in the existing Gap Register. L1–L6 and Gate A–D remain separate.
2. **Stage 1 · Parallel implementation.** Track A integrates its isolated Android commit into the existing M1 branch after diff ownership review; Track B closes C01 and J17–J20 through existing design/governance PRs without modifying Frozen bytes; Track C continues exact-SHA runner/runtime tests and reports the KVM blocker. Work owns integration and cross-branch conflict resolution. Independent tasks proceed.
3. **Stage 2 · Integrated exact-SHA acceptance.** After any product/source or workflow change, run CI and applicable Runtime on the new HEAD; bind source SHA, APK SHA, runner, Run IDs and each gate separately. Execute applicable N001–N010, J01–J20, 66 base and 10 E2E plus save/detail scenarios. Preserve each failure and allow at most initial attempt + 2 repairs per failing Gate.
4. **Release gate.** Require all applicable P0 defects closed, Gate A–D evidence, exact-SHA API28 runtime, design/logic/motion/accessibility acceptance, and N009 human approval. Until every required gate passes, disposition is `BLOCKED` or `REVIEW_REQUIRED`; testable APK may be delivered with limitations.

