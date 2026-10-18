# M1 Implementation Report

Task: YUJIAN_M1_PARALLEL_DELIVERY_V1
Parent PR: [#163 — Draft/Open/Unmerged](https://github.com/pan277942135/Yujian_App/pull/163)
Published integrated candidate: `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`; local repair worktree is based on this exact source SHA.

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

The integrated published candidate is `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`. Hosted Run 39092555676 passed the declared scope enforcement step but failed SDK manager lookup before Gradle and APK generation. Android CI Run 39092555645 ran 211 unit tests and failed one incorrect CAPTURED timeline assertion; APK assembly did not run. The local repair resolves SDK manager from the configured Android SDK tree and corrects the test timeline without relaxing assertions. Both changes await publication and exact-SHA CI. No candidate APK or APK SHA-256 exists.

## Exact-SHA candidate `6d16bb7`

On `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`, Android CI Run 39093186726 passed 211 JVM unit tests and Camera Runtime contract tests and assembled the app Debug APK, but AndroidTest compilation failed on three source references/imports; no APK artifact was staged. Hosted Runtime Run 39093186784 resolved `sdkmanager` but failed the license pipe under pipefail before Build Once; API28 jobs skipped. Four small fixes (license-pipe exit handling and three AndroidTest compile corrections) are staged for the next SHA. Control Plane Admission/recovery and C1 exact-scope checks passed at 6d16bb7; no Runtime or downloadable APK exists.


## Latest exact-SHA checkpoint

## Latest exact candidate checkpoint — ffe285aff3818435815f769532fa221d3157917e

- Android CI Run 39093846259: PASS; JVM/controller and camera contract tests, Debug APK, AndroidTest APK compile, packaging and lint succeeded.
- Hosted Run 39093844697: Build Once PASS and generated the installable APK pair. App SHA-256 `bd98a57334d278e9a3a5129e9d344d8e0de4faf058b7294ee614921664058b3e`, AndroidTest SHA-256 `249334802bd6ffe7084b9b96b604123db3b7b171640eb86b18344fa3a51749bb`.
- Hosted API28 Preflight failed KVM access; formal API28 Runtime is SKIPPED. No Runtime, screenshot, Motion Trace or Pixel Diff evidence exists.
- C01 remains separate: PR #162 Draft at 43b8267 contains two pages; PR #163 and main remain on the three-page code/contract.
- C02 follows the latest comment's V3.1 values 220/320/350/250ms plus real pipeline events. Real trace and PROC-07 timeout remain unresolved.


## Exact-source implementation and CI checkpoint — 85dfe92

The product candidate at 85dfe9217e879e0f87d19b41801beb05e502aaa2 passed Android CI Run 39095194583 and Build Once Run 39095194693, including JVM/controller checks and AndroidTest compilation. Build Once artifact 12124820081 is retained. Hosted API28 Preflight failed at KVM access; no Runtime was executed. The exact two-page guide remains unintegrated in PR #163; no new Android implementation is claimed in this checkpoint.
