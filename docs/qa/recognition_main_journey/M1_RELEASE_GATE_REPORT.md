# M1 Release Gate Report

Task: YUJIAN_M1_PARALLEL_DELIVERY_V1  
PR: [#163 — Draft/Open/Unmerged](https://github.com/pan277942135/Yujian_App/pull/163)  
Disposition: BLOCKED  
Current published source under review: c69196920a2f3d5fa04a6fd9985e3dadd10f6a3c

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


## Exact-SHA release status — 85dfe92

At source 85dfe9217e879e0f87d19b41801beb05e502aaa2, Android CI Run 39095194583 and Build Once Run 39095194693 passed. C1 scope/Frozen guard passed. API28 Preflight is BLOCKED_INFRA at KVM access; formal Runtime is NOT_RUN. PR #162 remains Draft and current main/PR #163 guide code remain three-page. The Build Once artifact 12124820081 supplies a testable APK pair, but its ZIP digest is not an APK digest. M1_RELEASE_PASS remains unsupported; no independent acceptance or N009 approval exists.


## Release checkpoint — exact source b97f01f6790cea2e2f1cb4bfef07a38086223020

| Gate | Result | Exact-SHA evidence |
|---|---|---|
| Android CI / Build / Page Contract | PASS | Run 39095681153 completed; Page Contract V3 registry/reference validator and Android build checks passed. |
| Build Once / APK provenance | PASS | Run 39095681151; app SHA `b6d37938cdc34230415b7aea7fbf36536803e3908944c6a9665367d7c01c0890`, AndroidTest SHA `356dfcffa5ef98ef2d1f496a2a2835b97e50776a714dab358d71ac03c7844f02`; artifact 12124671626, ZIP SHA `86b7fda5c6d542d1c57eb9076add52d1934d855aaa20ea9355364b65370ab77b`. |
| PRODUCT_WORK Admission | NOT_RUN | Private state is stale at ffe285a; Run 39095681147 is canonical-promotion admission only. |
| API28 hosted Preflight | BLOCKED_INFRA | Run 39095681151 failed KVM verification; artifact 12124194596. |
| API28 formal Runtime / N001 and applicable N gates | NOT_RUN | API28 emulator preflight was skipped; formal runtime job SKIPPED. Runtime-not-run artifact 12124388894. |
| J01–J20 / 66 base + 10 E2E / Save and Detail | NOT_RUN | No exact-SHA execution on device. |
| Gate A / C01 / J17–J20 authority | REVIEW_REQUIRED | C01 proposal not integrated; J17–J20 mapping conflict remains. |
| Geometry / Motion / Logic / Accessibility | NOT_RUN | No native screenshot, bounds, Pixel Diff, trace, MP4, or device logic evidence. |
| Gate D / N009 human approval | NOT_RUN | No independent acceptance or Release approval. |

Disposition remains `BLOCKED`; the artifact is `M1_TESTABLE_APK` only. No `M1_RELEASE_PASS` is claimed. PR #163 remains Draft/Open/Unmerged; no main merge was performed.



## Current exact-SHA release gate — c69196920a2f3d5fa04a6fd9985e3dadd10f6a3c

| Gate | Result | Evidence |
|---|---|---|
| Exact-SHA PRODUCT_WORK Admission / recovery | REVIEW_REQUIRED / NOT_RUN | No current-SHA private state refresh, branch-local Admission run or recovery rehearsal is recorded. Run 39096212604 validates canonical-promotion only. |
| C1 scope/Frozen protection | PASS | Hosted Run 39096212590 passed the scope and protected-source check at `c69196920a2f3d5fa04a6fd9985e3dadd10f6a3c`. |
| Android CI / Page Contract / unit / AndroidTest compile / package / lint | PASS | Run [39096212587](https://github.com/pan277942135/Yujian_App/actions/runs/39096212587). |
| Build Once / APK provenance | PASS | Run [39096212590](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590); app SHA `7e5ea493277dd42a2a7a72cdbce0c2d5c48273b62b81d39efd26d5e880149d37`; AndroidTest SHA `988dc01f9c31ae6e8cd96856be7cda446b639afc6f0d96fe8f72d05eebbd0f46`; artifact [12124946578](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590/artifacts/12124946578), ZIP SHA `caac6f268a189328a053d93880f3e5b86495d4fa5f876376bc3224ab69fe7a71`. |
| Hosted API28 Preflight | BLOCKED_INFRA | KVM check failed before emulator startup; evidence [12124588053](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590/artifacts/12124588053). |
| Formal API28 Runtime and applicable N001–N010 gates | NOT_RUN | Runtime job was skipped; explicit NOT_RUN artifact [12125106065](https://github.com/pan277942135/Yujian_App/actions/runs/39096212590/artifacts/12125106065). No device screenshots/bounds/Pixel Diff/Trace/MP4. |
| C01, J17–J20 and hosted-runner Control Plane synchronization | REVIEW_REQUIRED | PR #162 remains Draft at `1d948b45a3ebc759acef72dd1f81769e9e832d0f`; its Android CI 39096653816 passed with runtime jobs skipped. No main merge occurred. |
| C02 V3.1 real timing trace / PROC-07 | REVIEW_REQUIRED / BLOCKED_PARAMETER | Static event/timing checks passed in Android CI; no API28 trace. Timeout parameter remains unapproved. |
| Visual / Motion / Logic / Accessibility and Gate D | NOT_RUN / REVIEW_REQUIRED | No exact-SHA API28 evidence or independent review exists. |
| N009 human release approval | NOT_RUN | No approval requested or granted. |

Disposition: `M1_TESTABLE_APK` only; `M1_RELEASE_PASS` is not supported. PR #163 remains Draft/Open/Unmerged; no main merge occurred.
