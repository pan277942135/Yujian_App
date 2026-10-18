# M1 Release Gate Report

Task: YUJIAN_M1_PARALLEL_DELIVERY_V1  
PR: [#163 — Draft/Open/Unmerged](https://github.com/pan277942135/Yujian_App/pull/163)  
Disposition: BLOCKED  
Current published source under review: 6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e

## Gate results

| Gate | Result | Evidence or blocker |
|---|---|---|
| Six-layer Admission and recovery | PASS at `6d16bb7` | Refreshed private state and exact-hash restore rehearsal; Master, Work, synchronization (616 paths) and full PRODUCT_WORK Admission passed. Raw state remains private. |
| C1 scope and protected-source diff | PASS at `6d16bb7` | M1 run 39093186784's scope enforcement step passed; local scan also passed 55 declared paths with zero unexpected/protected paths. |
| Candidate Build and Unit tests | FAIL | Hosted M1 run 39093186784 failed at SDK license pipe before Gradle. Android CI run 39093186726 passed 211 unit tests and assembled Debug APK, then failed AndroidTest compilation; no artifact was uploaded. The license-pipe and AndroidTest fixes are local and await a new SHA. |
| Hosted API28 Preflight | NOT_RUN | M1 run 39093186784 skipped Preflight after Build Once failed. KVM, Pixel 2, API28, display, install, CameraX and artifact upload have not executed. |
| Formal Android Runtime | NOT_RUN | No exact-SHA gate artifacts, screenshots, bounds, Pixel Diff, Motion Trace or MP4. |
| C01 two-page guide | REVIEW_REQUIRED | PR #162 remains Draft; audited PR #163 product code remains three-page while Track C QA expects P01/P02. |
| C02 V3.1 timing | REVIEW_REQUIRED | Source and validator align statically; real trace missing; V1.2 Resolve conflict remains; PROC-07 is BLOCKED_PARAMETER. |
| Catalog, E2E and save/detail | NOT_RUN | 66 base and 10 E2E cases plus supplemental save/detail have not executed on candidate SHA/APK. |
| Visual, Motion, Logic, Accessibility | NOT_RUN or REVIEW_REQUIRED | Matrix contains 65 design/asset rows; missing/review authorities remain; no exact-SHA native evidence or independent acceptance. |
| APK SHA and Runtime Run ID | NOT_RUN | CI assembled an app APK at 6d16bb7 but AndroidTest compile failed before staging/upload; no installable artifact or APK SHA is available. Runtime Run ID remains absent. |
| N009 human release approval | NOT_RUN | No release approval requested or granted. |

## Release decision

M1_RELEASE_PASS is not supported. Runs 39093186726 and 39093186784 are failure evidence at source `6d16bb7`, not Runtime evidence. The Android CI run assembled an APK inside the runner but failed AndroidTest compile before uploading any artifact. No downloadable APK exists. The SDK license pipeline and AndroidTest compile fixes require a new exact-SHA rerun. Release remains blocked until build/unit issues are repaired, API28 Preflight and exact-SHA Runtime gates actually pass, applicable P0/P1 defects close, independent review completes and N009 human approval is recorded.


## Latest exact-SHA checkpoint

## Latest exact-SHA release gate

Candidate source: ffe285aff3818435815f769532fa221d3157917e. PR #163 remains Draft/Open/Unmerged.

| Gate | Result | Evidence |
|---|---|---|
| Local PRODUCT_WORK Admission | PASS | Actual M1 worktree Admission at ffe285aff3818435815f769532fa221d3157917e; distinct from GitHub canonical-promotion Run 39093844689. |
| C1 scope/Frozen protection | PASS | Hosted Run 39093844697 scope check at exact SHA. |
| Android build, JVM/unit, harness contract, AndroidTest compile | PASS | Android CI Run 39093846259. |
| Build Once APK pair | PASS | Hosted Run 39093844697; app SHA `bd98a57334d278e9a3a5129e9d344d8e0de4faf058b7294ee614921664058b3e`, AndroidTest SHA `249334802bd6ffe7084b9b96b604123db3b7b171640eb86b18344fa3a51749bb`. |
| Hosted API28 Preflight | BLOCKED_INFRA | KVM character/read/write assertion failed; evidence artifact 12123731965. |
| Formal API28 Runtime / N001 | NOT_RUN | Runtime job is skipped after failed Preflight. |
| C01 two-page guide | REVIEW_REQUIRED | PR #162 remains Draft; main and PR #163 source remain three-page. |
| C02 real motion trace | REVIEW_REQUIRED | V3.1 event gate checks pass statically; trace/PNG/MP4 absent; PROC-07 timeout unapproved. |
| P0/P1 closure, independent visual acceptance | BLOCKED / REVIEW_REQUIRED | P0 Runtime/design-source gaps and independent review remain open. |
| N009 human release approval | NOT_RUN | No release approval requested or granted. |

An installable `M1_TESTABLE_APK` is available for exact source ffe285aff3818435815f769532fa221d3157917e; this is not a Runtime-accepted APK and does not support `M1_RELEASE_PASS`.
