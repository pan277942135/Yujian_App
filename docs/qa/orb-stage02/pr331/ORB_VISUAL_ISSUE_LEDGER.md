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
