# M1 Implementation Report

Task: YUJIAN_M1_PARALLEL_DELIVERY_V1
Parent PR: [#163 — Draft/Open/Unmerged](https://github.com/pan277942135/Yujian_App/pull/163)
Published integrated candidate: `1d98f48d59132d3d1ea9e226d0e32342459eb002`; local repair worktree is based on this exact source SHA.

## Integrated worktree scope

| Work line | Reused or changed work | Source evidence | Current result |
|---|---|---|---|
| Android implementation | Recognition V3.1 event timing, real-result handoff, detector focus mapping and Result IME handling | Track A branch head 82c49b8d7ced6568ee2d04e3ecbd1c10a06cc018; changes are present in candidate worktree | Source/static contract checks pass on Track A branch; no Gradle build or Android runtime in this workspace |
| QA and Runtime harness | V3.1 timing authority/validators, active two-page guide test contract, and Save Catch → Home → Detail A/B supplemental test | Track C head 69fcf47e215d47a7f055ebcc095625b0c3d4b942 | Catalog 66/10 static validation passes; fake-ADB/harness tests pass; device execution NOT_RUN |
| Design and assets | Reused 65-row Design Authority Matrix and asset audit; no Frozen source binary copied or regenerated | Track B worktree head f98012c0085796750b1dfbc37c87519697f94c38 | Missing/review/blocked authority rows remain; deployed Design Manager page access BLOCKED_ACCESS |
| Control Plane and recovery | Six-layer Admission at live PR source and private state restore rehearsal; C1 scope manifest/validator added | Admission PASS after restore at source 81aefb1c0eae08df5ea6f451fc7c65be89a9c191; candidate scope diff local PASS | Next remote SHA needs refreshed private checkpoint and Admission rerun |
| Runtime migration | Hosted Ubuntu/API28 Pixel 2 Build Once and Preflight workflow drafted | .github/workflows/m1-hosted-api28-runtime.yml | NOT_RUN; no Run ID/APK SHA |

## Product boundaries

No model, backend, API, database contract, shared design token, or Frozen PNG/SVG was modified. The old guide's three-page app code is still present at the audited PR #163 source; C01's two-page implementation remains Draft in PR #162 and is not represented as merged. The QA contract expects P01/P02, so that product/test mismatch remains a release blocker. N010 in the static task machine still requires three pages and is reported as an authority conflict rather than silently edited.

Recognition timing follows State_Machine_V3_1.json: 220/320/350/250ms minimum beats and real Pipeline event gating. V1.1's 2.8s target is not an M1 threshold. V1.2 Resolve remains a visual conflict; PROC-07 remains BLOCKED_PARAMETER.

## Build and feature self-test status

No integrated Build Once APK has been created for the candidate worktree. No APK SHA-256, Runtime Run ID, screenshot, Pixel Diff, bounds capture, Pipeline Trace, or Motion MP4 is available for this candidate. Historical Track A/Track C branch test results are not treated as a result for a different SHA. See M1_RUNTIME_QA_REPORT.md for individual gate classifications.

## Integration validation and repair evidence

The candidate scope guard passes locally against the canonical baseline with 55 changed paths, zero unexpected paths and zero protected paths. PyYAML and `bash -n` pass for all migrated/new workflow files. Harness fake-ADB contract tests, Camera Runtime gate tests, V3.1 contract/finalizer checks, catalog structure and source-closure tests pass locally. An initial malformed YAML insertion in `android.yml` and a scope scan that rejected transient Python bytecode are preserved in `M1_LOCAL_REPAIR_EVIDENCE.md`. These checks are not a Build Once or Runtime run, and are bound only to this dirty worktree parent `81aefb1`; a new remote SHA needs its own CI.

## Exact-SHA build findings

The integrated published candidate is `1d98f48d59132d3d1ea9e226d0e32342459eb002`. Hosted Run 39092555676 passed the declared scope enforcement step but failed SDK manager lookup before Gradle and APK generation. Android CI Run 39092555645 ran 211 unit tests and failed one incorrect CAPTURED timeline assertion; APK assembly did not run. The local repair resolves SDK manager from the configured Android SDK tree and corrects the test timeline without relaxing assertions. Both changes await publication and exact-SHA CI. No candidate APK or APK SHA-256 exists.
