# PR #331 Orb Visual Issue Ledger

**Work:** `YUJIAN_PR331_R03_R06_ITERATIVE_VISUAL_ENGINEERING_CLOSURE_V1`
**Registered parent task:** `YUJIAN_ORB_WATERLIGHT_WEB_ANDROID_FULL_LIFECYCLE_V3`
**Baseline SHA:** `762b656591be3129bf2ec02768a65109371fe4ab`
**Prior device evidence:** `264191.mp4` (`7d3fb4924f544122720575f9ba5fad788bcb6d50793d08798e2404b37540c872`)

| ID | Issue | Existing evidence | Status | Next action |
|---|---|---|---|---|
| R03-01 | Manual Stage02 diagnostic completion freezes unified Scene Clock at progress 100% | Controller pause/manual behavior is source-confirmed; 264191.mp4 shows diagnostic mode changes | CONFIRMED_DIAGNOSTIC_BEHAVIOR; production impact NOT_CONFIRMED | Keep wake tied to progress and continuous flow tied to existing clock; inspect production AUTO trace |
| R03-02 | MAX phase was progress-only and became constant at a fixed progress if Scene Clock paused | Baseline `OrbInteriorOpticalField.frame()` source; updated phase math and tests | SOURCE_MECHANISM_FIXED; GPU_PIXEL_CAUSALITY_OPEN | Iteration 001 adds shared Scene Clock phase and holds Stage02 semantic phase through the Stage03 80 ms fade; exact-source device pixels pending |
| R03-03 | MAX sampling coordinate was attenuated by `maxInteriorMix` before output mix also used it | Baseline AGSL source; updated AGSL source and source-contract test | SOURCE_MECHANISM_FIXED; VISUAL_DELTA_NOT_MEASURED | Iteration 001 samples at `wakeCoord`; same-progress PixelCopy and local NCC metrics pending |
| R06-01 | G01/A01 glass transmission applied `radialMask²` and later a radial-gradient fade | Baseline source; calculated normalized middle factor 0.0999 vs 0.3996 after correction, excluding body/source-alpha | SOURCE_AND_MATH_CONFIRMED; FINAL_PIXEL_CAUSALITY_OPEN | Iteration 001 removes duplicate scalar mask; validate Full-vs-without path captures |
| R06-02 | G02/A02/C01 body visibility may be limited by valid-run length, alpha, stroke or local contrast | 264191.mp4 is aggregate/manual; no PixelCopy ZIP or per-path output | UNLOCATED | Use exact-frame full-vs-without ablations plus captured path metrics and user visual review |
| QA-01 | Prior evidence metadata did not uniquely describe each mode-switched image | Baseline reused metadata and inferred `actualFluidApplied` | SOURCE_DEFECT_FIXED; RUNTIME_NOT_RUN | New per-capture ID, frame state, source/Png hashes and state-vs-pixel labels |
| QA-02 | Waiting one Compose frame did not verify target Android window submission | Baseline waited a Compose frame before PixelCopy | IMPLEMENTED_NOT_DEVICE_VALIDATED | New matching Orb draw marker + Android frame-commit callback; mismatch/timeout is a capture failure |
| QA-03 | Export had no final-pixel ablation for each R06 path | Baseline export was aggregate | IMPLEMENTED_NOT_DEVICE_VALIDATED | New `R06_FULL` vs path-excluded capture for G01/G02/A01/A02/C01 |
| QA-04 | Paused diagnostic had no fixed-progress optical sample series | Manual preview completion can pause clock | IMPLEMENTED_AS_PROJECTED_SAMPLES_NOT_SCENE_CLOCK_ADVANCE | Capture T0/T+500/T+1500 at same progress/identity/geometry; keep production AUTO evidence separate |
| QA-05 | Local Gradle tests cannot start in the isolated environment | Gradle 9.3.1 distribution is not cached; `services.gradle.org` is unreachable from this workspace; exact-head GitHub Actions completed on `2e11dea…` | BLOCKED_INFRA_LOCAL; EXACT_HEAD_CI_PASS_ON_2E11 | Use run `43671108469` for source/build evidence; collect API36 pixels separately |

## Recovery checkpoint — 2026-11-15

- PR #331 remains **OPEN / DRAFT / UNMERGED**; implementation source at the checkpoint is `2e11dea1761296cdd19625bfc45a6c47be1376d9`.
- Exact-head Android CI run [43671108469](https://github.com/pan277942135/Yujian_App/actions/runs/43671108469) succeeded. Unit tests, Recognition/Camera Harness, AndroidTest APK compile, lint, Release Kotlin compile, packaged resource/model checks and model-trace export passed. AndroidTest instrumentation and API36/device jobs were skipped by scope.
- Debug APK artifact [14144831040](https://github.com/pan277942135/Yujian_App/actions/runs/43671108469/artifacts/14144831040) contains `app-debug.apk` (144,397,930 bytes; SHA-256 `4bdbb1ab4347bb8e325ec24369e20e08cea747ad460038f3cb2d38e740de6a3e`); embedded build revision matches `2e11dea1761296cdd19625bfc45a6c47be1376d9`.
- The separately scoped Control Plane Admission workflow did not run: its pull-request trigger targets PRs based on `main`, while #331 targets `work/recognition-runtime-result-handoff-v1`. Baseline admission was recorded as passing; exact-head admission is NOT_RUN.
- No current-source PixelCopy ZIP or visual review is available. R03/R06 pixel contribution and human discernibility remain NOT_MEASURED.

## Iteration rule

Iteration 001 makes source-level progress but has no measured visual delta. Do not claim improvement from code, logs, or CI alone. Close a visual issue only when evidence is bound to the exact source SHA and identifies the first failing layer. Keep `R03_VISUAL_PASS`, `R06_VISUAL_PASS`, `GPU_PIXEL_PASS`, and `DEVICE_PASS` unclaimed until current-source device evidence and user visual review exist.


## Iteration 002 — retained production trace and current-run glass sampling

**Source baseline:** `10de57b467542596b55a9e96b7df1330fe1de1de`  
**Device evidence source:** `7da3604ac310348e9567268226792832ca59beef` (older APK; not evidence for this iteration’s source)  
**Evidence files:** `YuJian_20261115_Stage02_Device_QA_7da3604.md`, `orb-stage02-1794741649201.zip`, `SVID_20261115_191941_1.mp4`

| ID | Finding | Evidence level | Iteration 002 action | Status |
|---|---|---|---|---|
| QA-06 | The device ZIP reports zero production AUTO Stage02 frames after 7,880 general trace events were dropped; the export counted only the shared 4,096-row ring. | L2 for the older ZIP’s recorded counters; current-source mechanism confirmed by code | Retain AUTO Stage02 and MANUAL Stage02 events in separate bounded streams; export retained/dropped counts and nearest checkpoint references to real retained frames. | SOURCE_FIX_IN_EXACT_HEAD_CANDIDATE; DEVICE_RECAPTURE_PENDING |
| R06-03 | In the frozen A01 Stage02 0ms sample, the representative sample at about 0.4723D is outside the 0.47D mask while later samples in the same valid run are inside; selecting only the midpoint source alpha can underweight that drawable run. | L1/L2 local source/math evidence; not current-source GPU causality | Compute source alpha from the current valid drawable run, preserve the single per-pixel radial glass mask, and record representative plus run-level diagnostics. | SOURCE_FIX_IN_EXACT_HEAD_CANDIDATE; DEVICE_RECAPTURE_PENDING |
| R03-04 | The projected T0/T500/T1500 captures show time-dependent pixels, but the Stage02 frame was pinned and the projected clock is not proof of production AUTO motion. | L2 static/projected PixelCopy evidence; not L3 production dynamic proof | Keep the wake progress semantic clock separate from the shared continuous optical clock; preserve production AUTO frames independently in the trace. No further R03 intensity change is justified by current evidence. | PRODUCTION_CLOCK_PIXEL_VALIDATION_PENDING |

**Interpretation guardrails:** The prior ZIP’s zero AUTO frame count is a trace-retention failure, not proof that production Stage02 did not run. A PixelCopy image showing no glass transmission at one sampled phase is not proof that G01/A01 can never overlap the interior. Draw submission, retained runtime metrics, and shader READY remain below final pixel or human-visibility evidence.

**Next exact-source device evidence:** Install the APK whose source SHA equals this PR’s final HEAD; capture one normal AUTO Stage01–03 recording and one Debug evidence ZIP. Confirm the ZIP has retained AUTO Stage02 frame checkpoints and per-capture IDs before interpreting R03/R06. Keep the continuous-motion conclusion separate from paused MANUAL projected samples.


## Iteration 002 — exact-head Control Plane Admission route

The existing admission workflow was limited to `main` pushes and PRs targeting `main`; PR #332 targets `work/recognition-runtime-result-handoff-v1`, so its standalone admission job did not run. The existing workflow now also accepts `qa/**` pushes, allowing the final QA branch commit to run Control Plane Admission without changing the PR base or adding a workflow. The corresponding exact-head run is recorded in the delivery result.


## Iteration 003 — Mainline exact-head regression and APK provenance

**Main source baseline:** `e454e3af28e925a03ecf03d1ca2db37a61ff328c`  
**Integrated sampling/trace fix:** `a0b7ac45e00e5ea86eb96c2c94f87b3e3c890569`  
**Verification date:** 2026-11-16

### Source reconciliation

- Current remote `main` resolves to `e454e3af28e925a03ecf03d1ca2db37a61ff328c`.
- PR #332 is CLOSED / MERGED; merge commit is the current Main HEAD. Do not reopen, cherry-pick, or re-integrate it.
- Commit `a0b7ac45` is an ancestor of Main. Comparing `a0b7ac45...e454e3af` reports Main ahead by 6 commits and behind by 0.
- PR #331 is CLOSED / UNMERGED; its Code Local evidence and implementation were integrated through PR #332. Do not treat PR #331 as an active merge target.
- PR #333 is CLOSED / MERGED into the QA integration branch and its documentation commit is included in Main through PR #332.

### Exact-head CI and build evidence

- Android CI run: [43721682644](https://github.com/pan277942135/Yujian_App/actions/runs/43721682644), exact `head_sha=e454e3af28e925a03ecf03d1ca2db37a61ff328c`, conclusion SUCCESS.
- Control Plane Admission run: [43721682719](https://github.com/pan277942135/Yujian_App/actions/runs/43721682719), exact Main HEAD, conclusion SUCCESS.
- Android CI build job completed successfully. Its steps include Unit tests, Camera runtime harness contract tests, Debug APK assembly, Android runtime test APK compilation, packaged asset/model verification, model-trace export, Android lint, and Release Kotlin compilation; all listed steps completed successfully.
- The Android runtime, Orb visual evidence, Fish optics API36 runtime, and Recognition Result Handoff API36 physical jobs were SKIPPED. This is not device/instrumentation or visual acceptance.

### Exact Main Debug APK

- Workflow artifact: [14164514683](https://github.com/pan277942135/Yujian_App/actions/runs/43721682644/artifacts/14164514683)
- Artifact archive: `YuJian_Debug_e454e3af28e925a03ecf03d1ca2db37a61ff328c.zip`
- APK: `app/build/outputs/apk/debug/YuJian-debug.apk`
- APK bytes: `144408678`
- APK SHA-256: `3645151d125f9eda48da45789d8cde99bd8f0c44a011afcd98b27c0038903696`
- Embedded `apk_model_trace.json` reports `build_sha=e454e3af28e925a03ecf03d1ca2db37a61ff328c` and the same APK byte count and SHA-256.

### Model inventory in the exact Main APK

- Detector: `DET_FISH_v0.1`, input `[1,3,416,416]`, output `[1,3549,6]`, SHA-256 `12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f`.
- Classifier: `MODEL_CROP_M1_v0.2`, 15 classes, input `[1,3,224,224]`, output `[1,15]`, SHA-256 `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`.
- SlimSAM quantized ONNX encoder SHA-256: `cce23c7b2e5d4f330932738fb67ba518e04b0d99ccdd1cccd22a7da4e01f2971`.
- SlimSAM quantized ONNX decoder SHA-256: `cb90b279f549d2cab7fd6e20c38522438c65d84bdcca3d2a764cff7d857fdce2`.
- DET_FISH v0.3 is NOT present in this Main APK and must not be claimed as integrated.

### DET_FISH v0.3 PR status

- PR #329 remains OPEN / DRAFT at head `6e39db5f78f13d245aa2dbc880c9504f31e25f6a`.
- Exact-head Android CI run [43176522155](https://github.com/pan277942135/Yujian_App/actions/runs/43176522155) reports build SUCCESS and device-run bundle preparation SUCCESS; the bundle job is not physical-device execution.
- The PR body explicitly leaves physical inference, APK-internal v0.3 model SHA verification, Orientation Retry, BBox/Crop and Top1/Top5 acceptance pending.
- Comparing current Main `e454e3af` with PR #329 head `6e39db5f` reports DIVERGED (44 commits ahead, 101 behind) and GitHub reports `mergeable_state=dirty`. Reconcile the branch against current Main before any merge decision; do not merge as-is.

### Visual acceptance status

- R03 GPU/pixel continuity: NOT DEVICE-VERIFIED.
- R06 five-path per-path pixel contribution and human discernibility: NOT DEVICE-VERIFIED.
- Stage02 AUTO production trace retention: source fix included; current-source device recapture pending.
- Result Handoff / API36 physical runtime: NOT DEVICE-VERIFIED.
- Keep `R03_VISUAL_PASS`, `R06_VISUAL_PASS`, `GPU_PIXEL_PASS`, and `DEVICE_PASS` unclaimed.

### Next action

Product owner installs the exact Main APK above on the existing HUAWEI CMS-AL20 / Android 16 (API36), captures a normal AUTO recognition recording plus the current-build Debug evidence ZIP, and supplies both for source-bound review. Do not start an Emulator or claim physical-device acceptance from CI/build evidence.
