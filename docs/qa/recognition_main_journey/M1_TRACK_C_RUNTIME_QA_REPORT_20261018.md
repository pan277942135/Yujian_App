# M1 Track C Runtime QA Report

**Status:** `BLOCKED` — static QA checks pass; the approved visual source matrix and exact-SHA API28 runtime result are still missing from this baseline checkout. The V3.1 immediate-result / V1.2 Resolve authority conflict independently keeps M1 release at `REVIEW_REQUIRED`.
**Canonical baseline SHA:** `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`
**Working branch:** `work/m1-track-c-qa`
**Initial Save Catch journey commit SHA:** `09534eec7f1cb2bf0567f6b0827905cd93344ee7`
**P01/P02 guide QA synchronization commit SHA:** `6cd9c5e55000e7ed86ab79526db380a957a5ab9e`
**Report date:** 2026-10-18 (UTC)

## Latest M1 timing authority correction

The latest approved M1 timing authority is State_Machine_V3_1, not the earlier V1.1 timing proposal or V1.2's legacy state pacing. The C02/C03 follow-up aligns the runtime evidence contract, collector, catalog, review document, and test fixture to the exact minima **220/320/350/250ms** and requires real Pipeline event ordering before presenting each next state. It rejects fixed total, fast-result, and fish-focus-stability thresholds. `total_ms` is trace-consistency data only. The canonical artifacts are `recognition_processing_timing.txt`, `recognition_production_flow_trace_m1_v3_1.json`, and `recognition_motion_trace_m1_v3_1.json`.

The V1.2 motion document remains a separate diagnostic authority because it requires a 200ms RESOLVE handoff while V3.1 requires immediate RESULT_READY and defines no Resolve state. QA records its path/SHA and observed values without treating them as V3.1 timing assertions. The exact-SHA finalizer fails closed while the collision is unresolved. The GCP self-hosted runtime records remain historical; this branch has no exact-SHA API28 execution or APK evidence. Hosted runner migration is tracked separately and is not claimed by this report.

## M1 C02/C03 QA harness synchronization

For M1 timing acceptance, `design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json` is authoritative (SHA-256 `fa4f9f7ea371dd46f8a92be8fc0398bee465c05356a8d1d5623a46120d07de02`). The active minima are CAPTURED 220ms, DETECTING 320ms, OUTLINE 350ms, and CLASSIFYING 250ms. Every transition must also wait for its corresponding real Pipeline event, classifier-eligible fish focus, or real result. The validator checks exact presentation deltas and event ordering; it does not impose a fixed total, fast-result window, ETA, or fish-focus stability duration. Total duration is checked only for trace consistency.

`Recognition_Processing_Motion_Spec_V1_2.md` (SHA-256 `00df6a7c28a76182a44f8f2008918dc533f5640114496b132a759d72e61ddc7c`) separately requires a 200ms RESOLVE handoff, while V3.1 defines RESULT_READY as immediate and has no Resolve state. This authority collision remains `REVIEW_REQUIRED`; Resolve timing is diagnostic only, and the exact-SHA finalizer rejects M1 release eligibility until a formal resolution is recorded. PROC-07 remains `BLOCKED_PARAMETER` with no numeric inference timeout.

The M1 evidence contract binds authority path/SHA, QA catalog version/SHA, exact source commit SHA, GitHub Actions run ID, API 28, app/test APK SHA-256 values verified against `SHA256SUMS`, device profile, and artifact SHA-256 values. Required processing artifacts are the canonical TXT, M1 V3.1 motion and production-flow JSON, the production text flow trace, and captured M1 V3.1 video. The finalizer binds evidence only to a `PASS` `recognition-frozen` result with matching source SHA, API level, run ID, APK hashes, and source matrix; it also rejects finalization while the Resolve authority collision remains unresolved. The visual authority matrix path is `M1_DESIGN_AUTHORITY_MATRIX.json`; missing source rows, mismatched Frozen assets, or known gaps remain `REVIEW_REQUIRED`. Matrix presence alone never records a visual PASS.

**C02/C03 status: `BLOCKED` / API28 runtime `NOT_RUN`.** The old self-hosted GCP runs listed below are historical and cannot authorize M1 release after that runner pool expired. No API28 runner execution for the current Track C SHA has occurred. The local fake-ADB contract suite verifies harness classification and evidence-validation routing only; a 219ms synthetic CAPTURED beat is rejected as `FAIL_EVIDENCE` against the exact 220ms V3.1 minimum. A separate synthetic trace with no Resolve observation passes timing-contract validation but remains blocked at the missing Design System matrix. No synthetic fixture is allowed to emit a runtime PASS. The approved Design System matrix is also absent from this baseline checkout, so visual authority remains blocked. No product runtime failure or PASS is inferred from these static checks.

## Scope

This Track C increment adds a controlled instrumentation path for the explicitly excluded post-save journey, updates the approved `recognition-frozen` test list to execute it, and synchronizes active guide QA inputs/tooling to the approved two-page P01/P02 flow. It does not change product Kotlin or frozen images. The catalog remains at 66 base cases and 10 E2E scenarios, all `NOT_RUN`. The journey begins at the production Result composable and then composes the production Home and FishRecordDetail screens with the real guest archive repository. It does not claim the camera, inference pipeline, `YujianApp` route host, or B-side generation service as exercised.

The B-side is a local READY fixture written onto the record just saved by the test. This allows the same stable record ID to exercise first reveal, A/B flip, top Back, re-entry on A, and Android system Back. The test does not treat this fixture as generated product output.

## Catalog and coverage audit

The approved catalog remains unchanged in counts: **66 base cases and 10 E2E scenarios**, all recorded `NOT_RUN`. The catalog version is now 1.2.2 for the latest M1 V3.1 timing assertions; E2E count and statuses are unchanged. Its declared scope is `EMPTY_HOME_CAMERA_FIRST_GUIDE_CAPTURE_PROCESSING_RESULT_RETURN`; its explicit exclusions include `FishRecordDetail A/B` and `post-save flow`. Existing coverage is component-level for Result, guest persistence, Home recent-record ID navigation, and FishRecordDetail A/B behavior. No existing case connects those behaviors in a single Save → Home → recent record → detail → Back route.

The new test is supplemental and does not alter catalog totals or execution statuses:

| Supplemental ID | Coverage | Test | Status |
|---|---|---|---|
| `M1-SUPP-SAVE-DETAIL-01` | Result Save Catch → persisted guest record → Home recent card with same stable ID → FishRecordDetail first READY reveal and A/B flip → top Back → same record re-entry on A → Android system Back to Home | `app/src/androidTest/java/com/yujian/ai/RecognitionSaveCatchJourneyContractRuntimeTest.kt` | `NOT_RUN` — Android test APK not rebuilt for this source SHA; no local device/SDK |

Activity recreation is **not claimed**. Existing `RecognitionFirstCaptureGuideRuntimeTest.guidePageAndVisibilitySurviveSaveableStateRestoration()` uses Compose `StateRestorationTester`, which validates saveable component state, not recreation of the production Activity. The temporary synthetic Activity host was removed because it could not establish production IdentifyScreen behavior. J18 Activity recreation remains `NOT_RUN` pending a supported production Activity/API28 run.

## Two-page guide QA synchronization

The approved active guide is P01/P02: P01 uses a portrait phone with a horizontal fish example, and P02 uses a landscape phone with a horizontal fish example. Active QA inputs now use that sequence. `test_case_catalog_v1.json` is version 1.2.2 and `e2e_scenarios_v1.json` remains version 1.2.0; counts remain **66/10**, and every execution status remains `NOT_RUN`. `TEST_CASE_REVIEW_V1.md` reflects the same sequence and V3.1 state minima. GUIDE cases point to the approved two-page handoff. The former `WORK_TASK_FIRST_USE_CAMERA_GUIDE_V1.md` is marked historical/superseded and its P03 instructions are retained only as provenance.

The `camera-runtime-v1` gate and its contract test now collect and require runtime screenshots, Compose-component screenshots, overlays, and diffs for P01/P02 only. CAMERA permission reset/grant checks, the remaining camera evidence set, failure classification, visual source approvals, exact APK/source binding, API28 profile, thresholds, and required test classes are unchanged. The visual evidence builder maps P01 to its existing SHA-256 `dc731cca5470ef2154bb06172b9836e823ea780b0833f04e34caaf883e465c50`, and active P02 to the landscape/horizontal-fish Frozen source SHA-256 `1a3008483ac5708a0649501cf2aef6c799d47b95bcc2c1b1272fa64e68f7f9b3`. The source-closure validator requires exactly two page records and preserves all per-page independent-photo, provenance/rights, screenshot-hash/dimensions, production-route, and independent-review requirements. No tolerance or evidence threshold was reduced. No Frozen binary was changed.

This branch is QA-only. At canonical baseline `0d3238e8a41b87363e9a17b04c9cc45fca4c426d`, product authority, guide implementation, and their instrumentation assertions still contain the former three-page P03 sequence. PR #162 (`fix/recognition-guide-two-page-authority-v1`, observed head `43b8267167d8a948754fe3df7d20edf072955cf9`) remains the product-code/test integration dependency; it must be integrated before the active two-page runtime gate can pass. The new evidence builder expects `P02_Landscape_Horizontal_Fish_Frozen.png` at the approved P02 SHA; that renamed/registered path is not present in this Track C baseline checkout and must arrive with Track B asset/registry synchronization. The baseline's `P03_First_Use_Camera_Guide_Frozen.png` bytes are not changed or silently reused as an active page by this branch.

Remaining active P03 references are outside Track C QA ownership: `design/system_v3/08_Page_Contracts/recognition/capture/Recognition_Capture_V3_Authority.md`; design files `design/pages/recognition/first_use_camera_guide/README.md`, `Freeze_Decision_20261016.md`, `manifest.json`, `navigation.json`, and `asset_import_report.json`; baseline `RecognitionFirstCaptureGuidePolicy.kt`, `RecognitionFirstCaptureGuide.kt`, and `RecognitionFirstCaptureGuideReferenceSpace.kt`; baseline guide assertions in `RecognitionFirstCaptureGuideRuntimeTest.kt` and `RecognitionCameraRuntimeTest.kt`; and `scripts/verify_design_manager_hifi_release.py`, `scripts/verify_design_manager_live.py`, `scripts/verify_first_use_camera_guide_assets.py`, `scripts/verify_design_manager_browser.mjs`, and `scripts/verify_design_manager_navigation.py`. These remain product/design integration dependencies, not evidence of a runtime failure. Historical/archive-only references include the superseded Work Task's retained three-page body, the handoff's mention of “former P03 image bytes” as source provenance, dated `WORK_MAIN_CONSOLIDATION_20261017.md` notes about the then-current contract, `First_Use_Camera_Guide_Spec_V1_1.md`'s former P03 image description, and the untouched deprecated P02/P03 Frozen bytes kept for provenance. The active `EVIDENCE_SOURCE_CLOSURE_REPAIR_V1.md` now explicitly requires exactly two guide pages.

## Validation performed

| Command / source | Result | Interpretation |
|---|---|---|
| `python3 scripts/validate_recognition_test_case_catalog.py` | PASS — 66 cases, 10 E2Es, 4 primary user states | Catalog consistency only; not runtime acceptance |
| `python3 scripts/verify_recognition_design_closure_v1_1.py` | PASS in the prior V1.1 QA state — 9 Frozen references, 7 ambient paths | Historical source-closure check only; superseded for M1 timing and not runtime acceptance |
| `python3 scripts/validate_recognition_qa_test_users.py` | PASS — dataset V1.1.0, manifest SHA-256 `161dd937a71ea935b7409665de833581b036567bf68ee677679f8620a07b5e69` | Manifest validation only |
| `python3 -m unittest discover -s tests -p 'test_recognition_test_case_catalog.py'` | PASS — 21 tests | Catalog policy/unit checks only |
| `python3 -m unittest discover -s scripts -p 'test_runtime_evidence_source_closure.py'` | PASS — 7 tests | Synthetic source-closure validator tests, including rejection of retired/extra page records; no product runtime evidence |
| `python3 scripts/android_runtime/tests/test_recognition_m1_authority_map.py` | PASS — 4 tests for component-row uniqueness, string/list target mapping, review gaps, and missing-matrix blocking | Static authority-map semantics only; matrix source completeness still requires B's real matrix |
| `bash scripts/android_runtime/tests/test_contract.sh` | PASS — 4 authority-map tests plus nine fake-ADB classifications; a 219ms CAPTURED beat is rejected under the V3.1 220ms minimum as `FAIL_EVIDENCE` | Harness routing and negative timing assertion only; no Android device run or product PASS |
| Manual `FAKE_ADB_MODE=ready` evidence-contract exercise | `FAIL_EVIDENCE` with `M1_VISUAL_AUTHORITY_REVIEW_REQUIRED` after V3.1 trace/timing validation; the fixture's Resolve diagnostics are null/false and were not treated as V3.1 timing failures | Synthetic validator-routing evidence only; missing source matrix remains blocking; no runtime PASS |
| `python3 scripts/android_runtime/tests/test_recognition_m1_v3_1_finalizer.py` | PASS — a synthetic runtime-gate PASS is rejected while the authority collision remains unresolved | Finalizer fail-closed unit test only; does not validate device behavior |
| `bash scripts/android_runtime/tests/test_camera_runtime_gate.sh` | PASS | Camera-gate permission/evidence contract checks only |
| `python3 -m py_compile scripts/verify_recognition_m1_v3_1_runtime_evidence.py scripts/finalize_recognition_m1_v3_1_runtime_evidence.py` | PASS | Python syntax only; does not validate runtime evidence |
| `bash -n scripts/android_runtime/gates/recognition_frozen.sh` | PASS | Shell syntax only |
| `bash -n scripts/android_runtime/gates/camera_runtime_v1.sh` | PASS | Shell syntax only |
| `python3 -m json.tool` on the updated catalog and E2E JSON | PASS | JSON syntax only |
| `git diff --check` | PASS | Whitespace/conflict-marker check only |

Local Android build and instrumentation are unavailable: this checkout has no `gradlew`, `gradle`, `adb`, `emulator`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`. The new Kotlin test has **not** been compiled or run locally.

## Existing GitHub Actions evidence

Both observed runs are bound to the canonical baseline SHA, not to the Track C changes below:

| Run ID | Workflow | SHA | Observed state | What it establishes |
|---|---|---|---|---|
| `39016335427` | Android CI | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` | Build job `success`; API28 jobs were queued when checked 2026-10-18; runner pool subsequently expired | Historical baseline unit tests, harness contract checks, debug APK assembly, Android test APK compilation, packaging checks, and lint only. It did not compile the new test or provide API28 runtime evidence for this branch. |
| `39016335503` | Camera Runtime Harness P0 | `0d3238e8a41b87363e9a17b04c9cc45fca4c426d` | Build job `success`; `inventory` was queued when checked 2026-10-18; runner pool subsequently expired | Historical baseline runtime APK pair and instrumentation APK compilation only. It does not validate this branch's added test or yield a completed camera runtime result. |

The overall workflow runs remain pending; a successful build job is not an Android Runtime PASS. No screenshot, `boundsInWindow`, Pixel Diff, APK SHA-256, or runtime report is attributed to a newer SHA in this report. The new test must be compiled and executed after the test, gate, and report commits are integrated into the same candidate SHA.

## Release-gate result and remaining gaps

**M1 Track C status: `BLOCKED` / runtime `NOT_RUN`.** No product test failure was observed in this workspace; the missing evidence is an infrastructure/candidate-SHA limitation, not a product PASS or FAIL.

- **P0:** End-to-end camera permission, first-use guide, capture, inference, result, save, and detail flow has no completed exact-SHA API28 runtime evidence here. The new supplemental journey is also `NOT_RUN`.
- **P0:** Product guide code/authority/tests at the canonical baseline still encode P03; the approved two-page product implementation and its exact-SHA tests must be integrated. The active E2E catalog and camera-runtime gate are now P01/P02-aligned.
- **P0:** The expected active P02 Frozen path `P02_Landscape_Horizontal_Fish_Frozen.png` is not yet present in this Track C checkout; Track B must deliver its approved registration/import before the visual builder can execute.
- **P1:** Production Activity recreation (J18), permission deny/re-entry, exception/retry branches, metadata keyboard behavior, short/long display, safe-area, screenshots, `boundsInWindow`, Pixel Diff, and key-motion evidence were not executed for this candidate.
- **P2:** No non-blocking RFX optical quality upgrades were evaluated in Track C.

The release gate remains closed until the test compiles, the required exact-head API28 jobs complete, the full product route and visual evidence are captured against the same APK/source SHA, and all required P0 cases are closed.
