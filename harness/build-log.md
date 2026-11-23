# Branch-local Work Build Log

## 2026-10-27 — W4 exact-SHA evidence-state and browser-gate correction

- Work: `YUJIAN_PAGE_FREEZE_PIPELINE_W4_TEST_EVIDENCE_V1`; branch `work/w4-test-evidence-v1`; code candidate `9767f39723cf635cc964e85142d7ef3140e29e90`; Main `42ad0d3fef23ea6576fd43dee781d253dcc11251`.
- Corrected the false-green case where a schema/resource/Pages scan could make the per-page evidence manifest `PASS` while browser geometry or page-owned matrix cases were still `NOT_RUN`. Overall evidence now remains `NOT_RUN` until applicable cases and actual browser evidence are attached; `validate-manifest` validates recorded status without silently promoting it.
- Added exact-source clean-worktree enforcement (with only documented generated state/cache and staged `_site` exceptions), W3 staged workspace browser checks before the Pages artifact is uploaded, and verifier rejection for reference-image-only or disabled tree nodes.
- Exact-candidate static checks: W4 design suite **75/75 PASS**; Admission **PASS** after rebinding branch-local state to the code candidate; W1 and W2 validation **PASS** for `home_normal_v1`, `home_empty_v2`, and `auth_login_v2`; checklist/YAML/shell syntax validation **PASS**.
- Per-page evidence state at the local run: NH01 `NOT_RUN` (24 matrix cases), Empty Home `NOT_RUN` (8 cases with unresolved page states left unbound), Login `NOT_RUN` (no machine-readable viewport authority). No actual staged Pages scan was attached locally, so each publication check and aggregate manifest remains `NOT_RUN`.
- Browser fixture attempt: `BLOCKED_INFRA`; Playwright module is available but the local Chromium executable is absent. GitHub Actions installs Chromium. No browser or native runtime result is claimed locally.
- W3 review finding for joint acceptance: Empty Home and Login currently expose only a single frozen reference image, not independently mapped page components; Login's own authority explicitly requires direct-image preview and forbids HTML reconstruction. W4's actual staged verifier now fails closed on that mapping state. This remains an integration blocker until page-owned DOM authority/implementation is available.
- Next action: push the independent W4 Draft PR and run exact-HEAD Actions; run the combined W3/W4 branch checks and preserve any non-NH01 DOM failures as `FAIL`/`BLOCKED`, not PASS.

## 2026-10-27 — W4 generic page validation and exact-SHA evidence pipeline

- Work: `YUJIAN_PAGE_FREEZE_PIPELINE_W4_TEST_EVIDENCE_V1`; branch `work/w4-test-evidence-v1`; baseline Main `42ad0d3fef23ea6576fd43dee781d253dcc11251`.
- Re-ran branch Admission before implementation: PASS on the Main-synchronized W4 branch; six static control-plane layers, canonical sync (731 paths), Project Master State, and Work Execution State all passed.
- Added a W4-owned evidence-manifest schema and runner that invokes the existing W1 envelope/profile validator and W2 page binding validator. It resolves each page via the Experience Registry and W1 matrix; it does not introduce another page or asset registry.
- Added exact-SHA candidate Pages staging and a separate W4 PR workflow. It runs the W2 private-name/hash and permission scan against the staged `_site` before any evidence artifact upload, then emits distinct `NH01_Evidence`, `EmptyHome_Evidence`, and `Login_Evidence` directories. The existing `design-manager-pages.yml` remains byte-identical because W2 pins its publication-policy authority SHA.
- Matrix derivation at baseline: NH01 24 profile/variant cases; Empty Home 8 authority-declared viewport/font-scale cases with unresolved state IDs left null and `NOT_RUN`; Login 0 because no machine-readable viewport authority is bound. No matrix cases were executed or promoted by the local derivation.
- Local validation: W4 tests 13/13 PASS; full `scripts/design/tests` discovery 73/73 PASS; Python compile, Node syntax checks, W4 evidence JSON Schema, workflow YAML parsing, and reusable checklist validation PASS. Browser fixture execution is pending CI because this container has the Playwright package but no installed Chromium executable. W4 workflow installs Chromium and runs five real-browser fixtures.
- Post-commit Admission caught the W4-owned schema and guide placed under the canonical responsive-authority root (`CONTROL_PLANE_SYNC` reported two extra files). Moved the schema to `scripts/design/schemas/` and the guide to `docs/design/`; no static control-plane or W0 topology changes. Exact-HEAD Admission is being rerun after this correction.
- Candidate-source W1/W2/Pages publication checks, GitHub Run IDs, evidence artifacts, and Draft PR creation remain pending exact-HEAD commit and Actions execution. Page acceptance, visual acceptance, and Android Runtime remain `NOT_VERIFIED`.

## 2026-10-25 — NH01 structured workspace reuse and visual parity repair

- Refreshed `origin/main` and ran `scripts/validate_control_plane_admission.py`: PASS, exit 0, six control-plane layers, synchronized baseline `772a206131d784d5c962a1eb62b5e43bca0f6534`.
- Confirmed PR #203 is merged at the current Main SHA. PR #176 remains Draft/Open and is used only as a selective reuse source.
- Live Design Manager at Main showed a flat semantic layer list, the blocked Hero/Peek placeholders, and the existing working responsive loader; the Main build marker was `772a206131d784d5c962a1eb62b5e43bca0f6534`.
- Replaced the flat layer model with a recursive mapping from the checked-in NH01 component tree. The CTA remains explicitly unmapped to an individual source node because the available structure reuses the capture-action group reference.
- Kept the PR #203 loader, routing and contract calculations. Bound the approved Normal Home V2 avatar source; preserved the five frozen NH01 screenshots and their hashes. The V2 avatar is not the illustrative person shown in the M03 screenshot.
- Searched registered and repository image assets for approved independent Hero/Peek originals; none were established. The workspace continues to show explicit blocked states and does not crop frozen page screenshots into photos.
- Added measured DOM rectangle evidence and multi-size screenshot comparison outputs for M01–M04. M05 remains a width-only boundary with no assumed height. Comparison output is diagnostic and never declares visual parity.
- Local checks: JavaScript syntax, `tests/nh01_structured_workspace.test.cjs`, navigation verification, frozen hi-fi integrity, Design Manager registry validation, and 320×480 comparator smoke all passed. Candidate Chromium and visual parity remain unverified pending exact-head workflow evidence.


## 2026-10-28 — AI Studio perimeter shader port start

- Work: `YUJIAN_ANDROID_AISTUDIO_PERIMETER_SHADER_PORT_V1`; branch `work/aistudio-perimeter-shader-port-v1`; exact Main baseline/control-plane revision `632cada103bf022adeb873bd6e66464555b509e4`.
- Control Plane PR #243 merged as `632cada103bf022adeb873bd6e66464555b509e4` after every exact-head check passed. Product branch synchronized to Main; Admission PASS (six static layers, 733 synchronized paths, Project Master State, Work Execution State).
- Resolved the source contract at `pan277942135/demo--Image-light-effect@794e7f2698525761bbb2f3e5d775b5dae8d7a25a`; shader and perimeter-node sources were read at that SHA. No Android implementation or visual/runtime result is inferred from source inspection.
- Stage start: E1 implementation, perimeter-only GLES2 photo compositor bound to the existing PhotoSession, `RecognitionImageTransform`, and `processingElapsedMs` / `visualClockMs`. Acceptance gate: `G-ANDROID-AISTUDIO-PERIMETER-SHADER-PORT-V1`.
- `source_browser_geometry`, `coordinate_mapping`, and `overlay_alpha_comparison`: NOT_RUN. The current browser has no source tab; the repository exposes no homepage or GitHub Pages deployment.
- `api28_device_runtime`, seven-timestamp comparison, APK provenance and visual acceptance: NOT_RUN. This execution environment has no attached API 28 device.
- Next at stage start: implement the renderer and continue seeking the exact source preview/runtime geometry; do not claim visual PASS until source comparison and a real API 28 device run exist.

## 2026-10-28 — Perimeter GLES2 candidate validation

- Android implementation source SHA `3ad7b0257ec67807f4d5fe124ea98ea7a6a8624a` was submitted as Draft PR #244 against Main `632cada103bf022adeb873bd6e66464555b509e4`; source authority remains AI Studio SHA `794e7f2698525761bbb2f3e5d775b5dae8d7a25a`.
- Android CI run `40669933101` completed successfully: unit tests, runtime harness contract tests, `assembleDebug`, `assembleDebugAndroidTest`, packaged asset/model checks, model trace export, and `lintDebug` all passed. This CI did not run the GLES renderer or device runtime.
- Runtime APK artifact `12807628530` was downloaded for verification. `app-debug.apk` SHA-256 is `1fe5b10a47478c00876fb26c159cc9470df5d52212aa7e5ecf6632aa46eff6b8`; it matched the artifact `SHA256SUMS` and an independent streamed hash of the APK ZIP entry. The SHA-256 of the artifact ZIP is not used as the APK hash.
- Control-plane Admission `40669933102`, W1 contract `40669933153`, W4 evidence `40669933113`, and Design Governance `40669933218` succeeded on that candidate. Design Manager Pages `40669933158` failed because its design-only changed-file gate saw Android app paths in a PR that also added an evidence document under `design/`; the report has been moved to `harness/evidence/` so the final PR diff contains no `design/**` changes.
- `source_browser_geometry`, `coordinate_mapping`, Overlay alpha comparison, GLES compile/link runtime logs, API 28 physical-device acceptance, seven-frame comparison, full recording, lifecycle captures, and same-input Detector/Classifier trace remain `NOT_RUN`.
- Next: rerun the PR checks on the current branch head, then obtain the exact-source browser geometry and a physical API 28 device. Keep visual acceptance and task completion blocked until those captures exist.

## 2026-10-28 — Renderer foreground-start correction

- Code review found that `GLSurfaceView` starts resumed by default. The candidate now calls `onPause()` immediately after `setRenderer`; the existing `LifecycleOwner` observer is the only path that resumes GLES rendering.
- The previous successful Android CI and APK hash above belong to the initial implementation source SHA `3ad7b0257ec67807f4d5fe124ea98ea7a6a8624a` and are historical for this lifecycle correction.
- Latest exact-head build/tests and APK provenance are pending the PR #244 synchronization run. Source-browser geometry, GLES runtime logs, and physical API 28 validation remain `NOT_RUN`.


## 2026-10-28 — Lifecycle-corrected Android candidate CI and APK provenance

- Current Android implementation source SHA: `e1bda4f568225b4349e247e204e2157912668a06`; PR #244 exact-head Android CI run `40672019980` completed successfully. The PR workflow built merge SHA `85f2ee06cfa4b08d952ab32d03d9fdb130f6fb6c` from this head and refreshed Main `632cada103bf022adeb873bd6e66464555b509e4`.
- Passed: Android unit tests, camera runtime harness contract tests, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, packaged launcher/recognition asset checks, model trace export, lint, and runtime APK artifact upload. The `android-runtime` job was skipped; these results do not compile the GLES shader on a device or constitute API 28 runtime acceptance.
- Runtime APK artifact `12809162079` (`YuJian-runtime-apks-85f2ee06cfa4b08d952ab32d03d9fdb130f6fb6c-MODEL_CROP_M1_v0.2`) contains `app-debug.apk`. Its SHA-256 is `1cf66279ed4622b653412a672ce74a7a87ec83fc4a2f97e517de41e6200c9a8f`; it matches the artifact `SHA256SUMS` and an independent streamed hash of the ZIP entry.
- Model trace package metadata reports model `MODEL_CROP_M1_v0.2`, model SHA-256 `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`, class-map SHA-256 `5f88d39dc7d61b2bc4a9c13b7279a0ad1fff726dc6cf22363d2be17bc3ef40db`, and tensor-contract SHA-256 `9da0cc2c943a181fa66689bb6ecf092e9e68ab3dd72a41a3baf4d3fa446bca8`. This confirms packaged model identity and tensor contract; it is not a same-photo Detector/Classifier output comparison.
- The source repo at `794e7f2698525761bbb2f3e5d775b5dae8d7a25a` builds locally. The local preview HTTP URL was rejected by the cloud-browser URL policy, so source-browser dimensions and captures remain `NOT_RUN`.
- No `adb`, local Gradle, GLES compiler, or attached API 28 device is available. Shader compile/link logs, API 28 install/runtime, visual frames/recording, and lifecycle cases remain `NOT_RUN`.

## 2026-10-29 — RR09 and SWOS native integration candidate

- Work: `YUJIAN_ANDROID_RECOGNITION_SWOS_RR09_FULL_INTEGRATION_APK_V1`; branch `feature/android-recognition-swos-rr09-full-integration-v1`; initial Main/control-plane baseline `4690913c1d2934d0ec5183f32a0b5f1b96c20f68`.
- Live Admission passed on initial Main `4690913c1d2934d0ec5183f32a0b5f1b96c20f68`. A later refresh advanced Main to `b080bb0ffb4ebced18593c08709657fabb53a19e` with three Login-only contract paths; the feature branch merged that exact Main revision. Current code checkpoint is `5b1025cc701b1389995b136029ef119f2cf0b3b4`; Admission was rerun after the Main sync and passed on synchronized HEAD `4dc109ad`. The Android build/test workflow is the next gate.
- PR #244 shader port was merged into the feature branch for reuse. Its AI Studio source attribution remains `794e7f2698525761bbb2f3e5d775b5dae8d7a25a`. The integration applies the SWOS P0–P7 environment envelope, disables the duplicate Compose perimeter, and keeps the Effect 01 caustic/photo shock and real SlimSAM focus layers on the shared Processing clock.
- RR09 integration changes the 200 ms photo geometry to smoothstep, binds Mask trust to current session/attempt/image hash, scales the three Mask contour strokes with real fish geometry, and forms the Chinese title using its native glyph path. HIGH results without trusted Mask geometry keep the non-spatial Chinese title reveal; non-HIGH results remain static.
- Added/updated targeted tests for the 2800 ms phase sequence, P0–P7 ambient strengths, P2–P7 fish-gold hold, R0.5 at 100 ms, title-only fallback, and stale Mask identities. Source tests and Debug APK are pending exact-head Android CI.
- Local Kotlin/Gradle build and APK installation are `NOT_RUN`: this execution environment has Java 17 but no Gradle executable/wrapper, Android SDK, or `adb`. The exact `feature/**` push will invoke the repository Android workflow; physical-device runtime and product visual acceptance remain pending.


## 2026-11-03 — P0-2 frozen Orb wake, breath, R01B grass, and R06 local release

- Work ID: `YUJIAN_AI_ORB_WAKE_BREATH_RELEASE_RUNTIME_FROZEN_V1_1`; repository `pan277942135/Yujian_App`; branch `feature/p0-2-ai-orb-wake-breath-release-v1-1`; implementation starts from Main/P0-1 SHA `b5f025ef9232ea3e2bb491d142b31f341584fe16`.
- Re-ran `python scripts/validate_control_plane_admission.py` after refreshing `origin/main`: PASS across six static layers, 747 synchronized paths, project state, and work state.
- Verified the uploaded freeze archive (SHA-256 `677ea9dddf3d445bc9c8385a14d9c6bb20e45619453f766f8b6c21a36d811500`): all 17 manifest resources and 18 checksum rows pass. All six source-derived masks are 1254×1254 grayscale and have zero nonzero pixels outside frozen source alpha. Full-source UV mapping was checked against the pinned 384×384 Runtime WebP (bilinear alpha correlation `0.999789`); frozen source and Runtime Core hashes remain unchanged.
- Implementing the shared-frame Orb wake/breath, one-texture R01B local warp, R06 five-path local release, and repository CI checks. No P0-1 controller/stage/result ownership changes. Local Gradle is unavailable; exact-head Android CI will run the requested test, lint, Debug assemble, and Release Kotlin compile equivalents. No emulator/API28/connected-device/Work visual run is authorized or planned.
- Implementation source commit `b847baa6b9156be454dcae6cf6a90c387e06e5c5` is committed; destination admission and non-Android PR checks are recorded below. Android build gates and APK provenance remain pending exact-head evidence.

- Implementation source commit `b847baa6b9156be454dcae6cf6a90c387e06e5c5` is committed. Source asset, R01B path-to-runtime coordinate, shader boundary-clamp, and R06 timeline verifiers pass on this candidate. The full freeze archive verifier passes locally; Gradle tasks, PR CI, and APK provenance are pending.

- Opened PR #315 (https://github.com/pan277942135/Yujian_App/pull/315), based on Main/P0-1 `b5f025ef9232ea3e2bb491d142b31f341584fe16`. After keeping the two branch-local execution-state files out of the canonical promotion tree, destination admission, W5 Chromium, and Work Execution V2 contract checks pass on head `c7545cd5697da57d467f35ec75e5106f36d7f029`. No device visual acceptance is claimed.

- Exact-head Android CI run `41592577764` on `c7545cd5697da57d467f35ec75e5106f36d7f029`: frozen resource checks passed; `testDebugUnitTest` ran 360 tests and exposed one assertion failure at `OrbWakeBreathReleaseV11Test.kt:19`. The 80 ms wake start was numerically nonzero because Bezier inversion approximated the exact x=0 endpoint. Recorded through `failure_gate.py` (first attempt allowed); corrected `wakeEase` to return exact endpoint values before bisection. The fix, regression test, lint, Debug APK, Release Kotlin compile, and product-owner visual acceptance remain pending exact-head rerun; no emulator or connected test ran.
- Exact-head Android CI run `41593211664` on `26c2225cc768df827c4d2ae558233a1979825bfd`: all 360 unit tests passed, Debug APK assembled, Android runtime-test APK compiled but not run, and packaged launcher/recognition/Core/model checks passed. `lintDebug` then failed with 2 errors and 85 warnings; the first reported `NewApi` error was disposal of the API-33 grass-warp renderer in `AIOrbCore.kt:221` under the project API-24 minimum. Recorded through `failure_gate.py` (first attempt allowed); added an explicit API-33 runtime guard to the cleanup path. Lint and the downstream APK/release gates require exact-head rerun; no emulator or connected test ran.
- Follow-up lint run `41594346091` on `6adb22b7fa1dd25e3fb06d7690c3c72f3d9f4a3e`: unit tests, Debug APK assembly, Android runtime-test APK compilation, and packaged-resource checks passed. `lintDebug` reduced to 1 error, now identifying the per-frame `OrbGrassWarpProgramV11.update()` call at `AIOrbCore.kt:229` as API 33 on the API-24-minimum app. Recorded as the second occurrence under the same lint failure budget; added the matching explicit API-33 guard around uniform updates. New exact-head verification is pending.

## 2026-11-13 — Recognition Runtime × Result Handoff closure started

- Work: `YUJIAN_RECOGNITION_RUNTIME_RESULT_HANDOFF_ARCHITECTURE_CLOSURE_V1`; canonical task binding: `YUJIAN_WP2_13_STAGE_RECOGNITION_EXPERIENCE_RUNTIME_CLOSURE_V3` (P6).
- Re-verified Main `81200da2e41163b04cfd340de59c3f43700c329a`, PR #327 head `076c830e043dfc7644188b9f730e9c6d53ba48e6`, base Main, Draft/Open/Unmerged.
- Exact-head Android CI run `42950331889` succeeded: unit tests, camera runtime harness contract tests, Debug APK assembly, AndroidTest APK compilation, packaged model/resource checks, lint, and Release Kotlin compile passed. Android runtime and fish-optics API36 runtime jobs were skipped; production runtime and device visual acceptance remain `NOT_RUN`.
- Exact-head Debug APK artifact `13824533268` is available. Recorded artifact archive size/digest only; APK-internal SHA-256 has not yet been verified. Do not treat the ZIP digest as the APK digest.
- Initial local Control Plane Admission failed because branch-local `PROJECT_MASTER_STATE.yaml` and `WORK_EXECUTION_STATE.json` were missing. No source or test gate failed. Initialized this branch-local checkpoint; exact Admission rerun is pending.
- Independent repair branch `work/recognition-runtime-result-handoff-v1` created from exact PR #327 HEAD `076c830e043dfc7644188b9f730e9c6d53ba48e6`. PR #327 remains Draft/Open/Unmerged; this branch owns the new closure work.
- Branch-local state now points to the independent branch. Re-admission is required before code changes.
- Exact Control Plane Admission passed on `work/recognition-runtime-result-handoff-v1`: canonical registry, Feature Matrix, Phase/Task State Machine, Unified Contract, 753-path Main sync, Project Master State, and Work Execution State all PASS. This is admission only; no runtime/device result is implied.
- Started P0-1 at source baseline `076c830e043dfc7644188b9f730e9c6d53ba48e6`: trace the existing production path ledger, immutable handoff, session store, Result RETURN producer and controller acceptance before changing code.


## 2026-11-13 — P0-1 Processing Path Snapshot candidate

- Source candidate SHA: `86e42b887d38cf5db828f7dd162728227016fb1a` on `work/recognition-runtime-result-handoff-v1`; PR #327 remains unchanged and unmerged.
- Added immutable `RecognitionFishPathSnapshot` export from the Processing `WaterLightPathLedger`, validated by controller/session identity, and carried it through `RecognitionRevealSessionStore` and `RecognitionRevealHandoff`. FULL_REVEAL Result RETURN now requires the original path ID, RETURN segments, Mask SHA/version, original Geometry Version, and P*; explicit Orb-only/BBOX_ONLY paths remain separate.
- Added path identity and stale-identity unit coverage, and extended the real-model Android production runtime test to assert that the same Processing snapshot survives SessionStore into FULL_REVEAL. Tests are awaiting exact-head Android CI.
- Local `./gradlew :app:testDebugUnitTest` is `NOT_RUN`: the wrapper could not download Gradle 9.3.1 because network access to `services.gradle.org` is blocked. This is not recorded as a test failure or pass. Device runtime and visual acceptance remain `NOT_RUN`.

- Opened draft PR [#330](https://github.com/pan277942135/Yujian_App/pull/330), base Main `81200da2e41163b04cfd340de59c3f43700c329a`, head `c6f66c106a1cee09892db172936de7baa74ec482`. The PR is independent of #327, which remains unchanged and unmerged. Exact-head Actions run discovery is pending; no build or test result is attributed to this commit yet.

## 2026-11-13 — P0-1 exact-head CI compile failure recorded

- Exact source: `e5ade8c2490d5338b816f39e03817c482b705092`; Android CI run `43356522026` failed at `:app:compileDebugKotlin`. The wrapper declared and forwarded `onFishPathSnapshot`, but `RecognitionProcessingScene` did not declare the parameter, producing an unknown named argument and unresolved callback reference. No unit tests, APK, or runtime stage completed.
- Recorded as a source compile failure; current hypothesis is an incomplete callback contract between the outer RecognizingScreen and its Processing scene. Repair remains limited to that producer/consumer wiring; exact-head CI must rerun before P0-1 can pass.
- Control Plane Admission currently requires checkpoint synchronization after this source change; physical Android runtime remains `NOT_RUN`.

## 2026-11-13 — P0-1 callback repair exact-head CI

- Repair commit `65829b9c9c49a5c1e9f212dcae332d6e1e6204d7` declares the forwarded `onFishPathSnapshot` parameter on `RecognitionProcessingScene`, restoring the real Processing PathLedger → YujianApp/SessionStore callback. Exact-head Android CI run `43358018059` passed unit tests, AndroidTest APK compilation, Debug APK assembly, packaged resource/model checks, Android lint, and Release Kotlin compilation. The preceding compile failure on `e5ade8c2490d5338b816f39e03817c482b705092` is recorded and its fix verified.
- The AndroidTest APK was compiled but not executed; no HUAWEI CMS-AL20 / Android 16 runtime evidence exists. P0-1 remains open for full SessionStore/Handoff/RETURN acceptance evidence.

## 2026-11-13 — P0 Recognition Runtime × Result Handoff exact-head automated acceptance

- Work: `YUJIAN_RECOGNITION_RUNTIME_RESULT_HANDOFF_ARCHITECTURE_CLOSURE_V1`; PR #330 remains independent of PR #327. Runtime source SHA: `e79ef2eba133ef6a703c9a65eb2aba331cc06744`.
- Unified the Session/Attempt/Photo/Mask/Path/Geometry identity in the immutable Result snapshot and typed 13.1–13.5 event/receipt lifecycle. Stage progression waits for accepted name stability, measured photo settle, result-scene commit, info arrival, and residue settle. Playback and navigation lifecycle state are retained. Frozen 13-stage order and 6000 ms total are unchanged; frozen visual handoff contract is reused without edits.
- Exact-head Android CI run `43365705354` passed unit tests, camera runtime harness contract tests, Debug APK assembly, AndroidTest APK compilation, packaged resource/model verification, Android lint, Release Kotlin compilation, and artifact upload. Control Plane Admission `43365705359` and Work Execution V2 Contract Smoke `43365705461` passed.
- Debug artifact `14010576852`; APK size `144217818` bytes; independently streamed APK SHA-256 `aa3b0c6897852ded1c10f9af43833b68e83c483ae3fed68220dbda02c4e6cb9b`. Artifact archive SHA-256 `548fe0c789230d65da6ee7d5cd3ac0c07071d915341b10852621a9c45de82c1c` is recorded separately.
- Independently verified packaged identity: DET_FISH v0.1 ONNX SHA-256 `12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f`; MODEL_CROP_M1 v0.2 SHA-256 `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`; both SlimSAM ONNX assets are present. This candidate does not claim DET_FISH v0.3 integration.
- Android runtime, API36 fish-optics runtime, and visual-evidence jobs were skipped. AndroidTest APK compiled but was not executed. HUAWEI CMS-AL20 / Android 16 end-to-end recognition and Result handoff, visual evidence, and product acceptance remain `NOT_RUN`; PR #330 remains Draft/Open.

## 2026-11-13 — Result Handoff exact-head compile repair

- Exact source `49684ae9f7e4dc8ed62e07919c91977773477fe1`; Android CI run `43404913307` failed at `:app:compileDebugKotlin` in `RecognitionResultProductionEvidenceWriter.kt`. Kotlin parsed unparenthesized Boolean expressions adjacent to infix `to` as nested `Pair` values, causing the qualification-report entries T17, T23, T24, and T27 to fail type checking. No unit tests, APK, or device runtime completed.
- Parenthesized each Boolean expression before constructing its `(pass, evidenceFile)` pair. The follow-up exact-head CI must pass before this source repair is accepted. API36 physical runtime remains a separate gate.

## 2026-11-13 — Orb source visibility and R06 current-frame integrity

- Work: `YUJIAN_ORB_R06_RENDER_VISIBILITY_P0_V1`; independent branch `fix/orb-r06-render-visibility-p0`, created from PR #330 exact head `6692b5d7bf8254442df1aacfe9798286a3ecc010`; Main baseline remains `81200da2e41163b04cfd340de59c3f43700c329a`.
- Admission on the repair branch: PASS after initializing its private branch-local state. It verifies all six canonical control-plane layers, 753-path sync, Project Master State, and Work Execution State. The initial local admission attempt reported only missing state files; no test gate ran before re-admission.
- Implemented the source-core presentation contract (no PHOTO_STABLE shader, shader wake at 80 ms, no added timer), removed the pre-handoff 280 ms Orb fade, and keep the source core visible if WaterLight host validation rejects geometry/session. Debug step mode alone reserves 88 dp below the processing layout.
- R06 sampling now invalidates per-frame samples, draws only contiguous valid runs, derives chunk visual parameters from the current valid run, and logs identity-bound stage-start/half render metrics. Added unit coverage for stage gates, geometry reserve profiles, host rejection reasons, release/return movement, stale-frame invalidation, and 48/96 sample tiers, plus a Compose pixel-capture AndroidTest for NORMAL/RAW_CORE/RAW_CORE_WITH_R06.
- Static resource/runtime checks passed: immutable Orb WebP SHA/dimensions; frozen wake/release asset verification; R02–R06 generated profile parity; recognition runtime contract. `git diff --check`: PASS. Local Gradle unit-test and AndroidTest compile attempt: NOT_RUN because Gradle 9.3.1 is not cached and `services.gradle.org` is unreachable; this is an environment blocker, not a source-test failure. Exact-head Android CI is pending push.
- HUAWEI CMS-AL20 / Android 16 device evidence: NOT_RUN; this workspace has no ADB or attached device. Keep this branch Draft pending exact-source physical acceptance.
- Exact-head Android CI run `43453935547` on `0765cef0dda66b0c3eb9b67d57debd2c3c211616` completed 405 unit tests with 1 failure: `OrbGrassFrozenAssetsV11Test.kt:62` still asserted the removed unconditional RenderEffect assignment. Production compile and prior static/resource checks passed; no APK or AndroidTest compile ran. Work failure gate allowed the first corrective attempt and recorded issue `779116f8b55520fed39a`.
- Updated the existing frozen-source assertion to require the stage-gated applied RenderEffect and the shared-clock optical gate. The exact-head Android CI rerun is required; this source-only repair does not promote the test gate.

## 2026-11-14T00:38:12Z — Orb Stage01–03 shader-safe layering P0 V2

- Opened Work `YUJIAN_ORB_STAGE01_03_SHADER_SAFE_LAYERING_P0_V2` on existing PR #331 branch `fix/orb-r06-render-visibility-p0` at exact baseline `5cb01bfffdc0f427a83b6936e31e35171f821378`.
- Scope is source, unit/harness checks, CI, AndroidTest compilation, and APK delivery. Instrumentation/device execution is explicitly excluded by the task.
- Control Plane Admission is the next gate; no product source has been modified yet.

### 2026-11-14T00:59:24Z — V2 implementation and pre-CI checks

- Stage02 optical gating now consumes `stageProgress`: formal 350 ms boundary is 80/350; the 3000 ms diagnostic reaches the same normalized boundary near 686 ms. Controller timing is unchanged.
- The frozen WebP is reused as the Raw Core and bounded Shader Overlay painter inside one offscreen composite. The frozen energy-alpha expression is applied once to the shared group.
- Shader updates are latched per shader instance; an update exception marks the instance FAILED, stops further updates, and disables only the overlay. New Session/Attempt identity owns a fresh creation slot. Candidate/legacy route selection is preserved.
- AndroidTest still installs one Compose root per test and uses draw-token synchronization. The matrix includes all requested stage/progress pairs, retains RAW_CORE_WITH_R06 captures, and adds injected black/transparent output pixel comparisons. No instrumentation/device execution is in scope.
- Local prechecks passed: Control Plane Admission, R02–R06 generated profile parity, Orb WebP SHA/dimensions, frozen R06 assets and timing map, Recognition Runtime contract, capture raster decode, design closure, resource package, and Android runtime harness contract tests.
- `git diff --check` passed. The local Gradle wrapper could not download Gradle 9.3.1 because network access is unavailable, so local Unit Test/AndroidTest compilation did not run. The local packaged-model check also cannot run before the CI asset-bootstrap step materializes the ignored classifier model; exact-head CI runs bootstrap before model validation.
- No ADB discovery, Emulator, Device Runner, or physical runtime was invoked.

## 2026-11-15 — PR #331 R03/R06 iterative visual closure baseline initialization

- Work ID: `YUJIAN_PR331_R03_R06_ITERATIVE_VISUAL_ENGINEERING_CLOSURE_V1`.
- Registered parent task binding: `YUJIAN_ORB_WATERLIGHT_WEB_ANDROID_FULL_LIFECYCLE_V3` / `G-ANDROID-ORB-WATERLIGHT-WEB-FULL-LIFECYCLE-V3`.
- Exact PR HEAD: `762b656591be3129bf2ec02768a65109371fe4ab`; PR remains OPEN / DRAFT / UNMERGED.
- Existing exact-source CI `43602865617` was recorded as historical PASS; no CI was run for this checkpoint. Device execution remains prohibited by task scope.
- Prior audit established: manual diagnostic progress-100 clock freeze; back transmission radial-mask-squared attenuation candidate; per-image capture metadata and per-path final-pixel evidence gaps.
- Local workspace isolation: current PR branch was materialized in a separate worktree; original `work/recognition-runtime-result-handoff-v1` tree and its existing changes were left untouched.
- Admission attempt at exact HEAD failed because branch-local `PROJECT_MASTER_STATE.yaml` and `WORK_EXECUTION_STATE.json` were absent. State initialization is in progress; no product source or tests were changed/executed.

## 2026-11-15 — PR #331 R03/R06 iterative visual closure implementation

- Work `YUJIAN_PR331_R03_R06_ITERATIVE_VISUAL_ENGINEERING_CLOSURE_V1` continued from exact PR head `762b656591be3129bf2ec02768a65109371fe4ab`; PR #331 remains Open / Draft / Unmerged. No PR state transition or device operation was performed.
- Implemented R03 continuous flow phase from the existing `WaterLightFrame.elapsedMs`, retained progress-based wake strength, held the semantic phase through the Stage03 80 ms fade, and removed duplicate displacement attenuation in AGSL. This is a source correction only; current GPU pixels are unavailable.
- Removed the duplicate scalar radial mask from the G01/A01 glass body alpha while keeping the source Core alpha, single smooth Paint edge mask, Back pass and frozen R06 geometry/material contracts.
- Added bounded Debug trace events and capture identity, target draw marker, Android window frame-commit callback, PixelCopy crop/hash metadata, projected fixed-progress R03 samples, and five same-frame R06 path ablations. Pixel evidence is marked measured only after alignment and measured-comparison status pass.
- Local checks: Control Plane Admission PASS; immutable Orb Core WebP SHA/alpha PASS; frozen R06 assets/material/timing PASS; generated profile parity PASS; Recognition runtime contract PASS; camera/recognition harness contract PASS; resource package validation PASS; Recognition design closure PASS; runtime evidence source-closure tests 6/6 PASS; bootstrap asset contract tests 6/6 PASS; JSON parsing and `git diff --check` PASS.
- Local `:app:testDebugUnitTest` was attempted but Gradle did not start: wrapper distribution 9.3.1 is uncached and `services.gradle.org` is unreachable. This is `BLOCKED_INFRA`, not a test failure or PASS. Exact-head Android CI must run the Unit Tests, AndroidTest APK compile, Debug APK, resource/model checks, lint and Release Kotlin compile.
- No current-source PixelCopy ZIP or same-mode advancing-clock video is available. Pixel delta remains `NOT_MEASURED`; AndroidTest instrumentation and API36 device visual QA remain `NOT_RUN_BY_SCOPE`.

- Remote branch publication: implementation source commit `317ade8de606dd673e4bb04a4f24063b41bd95c7` was created from Main `98684af617206027e3fbba35e0d2a45bd475e3ba`; the four implementation/test/verifier files are byte-identical to the locally validated tree (SHA-256 verified per file). Exact-head CI is pending.

## 2026-11-07 — WP1-B dynamic Mask content identity checkpoint (local Work evidence)

- Implementation source commit `6e4daf5cae54b33a9d2b1ce0440d9de929ad5810` computes SHA-256 from the actual encoded transparent SlimSAM PNG after mask cleanup. The value is propagated through `FishSubjectResult`, mask geometry, Processing geometry, and `RecognitionRevealHandoff`; it does not relabel DET_FISH v0.1 or supply the blocked v0.3 model.
- Local Debug unit-test startup located Android Studio JBR, then stopped before Kotlin compilation because Gradle Wrapper could not download `gradle-9.3.1-bin.zip` (network timeout). No test, build, APK, AndroidTest, device, or visual result is claimed for `6e4daf5`.
- GitHub Actions run `42319889988` initially rejected public copies of `PROJECT_MASTER_STATE.yaml` and `WORK_EXECUTION_STATE.json` in PR #320. The public PR tree was corrected at `59e5c7b22c07ca512008b8376367126cf32df24f`; the state and this observation remain local Work evidence.
- Next: obtain the declared Gradle distribution or an exact-head CI build, then record the actual Debug test/compile/package outcome and APK SHA-256 only if packaging succeeds.

- Exact-head PR #320 run `42320179675` on `59e5c7b22c07ca512008b8376367126cf32df24f` passed control-plane admission after state isolation, but Android CI stopped in `verify_lake_glass_route_coverage.py`: `NavHost route mismatch; missing=['record_calendar']`. This is a pre-existing resource/route gate outside the dynamic Mask SHA-256 scope. Gradle, Kotlin tests, APK assembly, AndroidTest, device, and visual evidence remain `NOT_RUN` on this exact SHA.


## 2026-11-16 — PR #335 + PR #336 Main integration admission checkpoint

- Work ID: `YUJIAN_PR335_PR336_INTEGRATION_MAIN_APK_V1`; integration branch `integration/pr335-pr336-main-apk` starts from the exact PR #335 head `8ee42198afa9684ffb651964f505eeb59bc02af0`. Main remains `e454e3af28e925a03ecf03d1ca2db37a61ff328c`.
- Re-verified PR #335 and PR #336 are Open/Draft/Unmerged; PR #336 exact head is `bc72263a9b6b9f2bd8d249768fdd55afd801f5a1` and its four-commit history is based on the same Main SHA.
- PR #335 exact-head Android CI run [43878634104](https://github.com/pan277942135/Yujian_App/actions/runs/43878634104) succeeded, including Unit Tests, Runtime Harness, Debug APK, AndroidTest APK compilation, packaged resource/model checks, lint and Release Kotlin compile. Device/runtime jobs were skipped (`NOT_RUN`).
- PR #335 Control Plane Admission run [43878634146](https://github.com/pan277942135/Yujian_App/actions/runs/43878634146) succeeded. PR #336 exact-head Android CI run [43845709918](https://github.com/pan277942135/Yujian_App/actions/runs/43845709918) succeeded; device/runtime jobs were skipped (`NOT_RUN`).
- This local checkpoint updates only ignored branch-local Work state and this execution log; no product source has changed. Local admission is being rerun for the new integration branch. No Main merge, ADB, Emulator, or device execution has occurred.


### Combined source checkpoint — `dbc5049d72c50061bf92a94ccb9c5f36d2d29905`

- Merged PR #336's four original commits `fb3d2a0fffd64b355f245d50877dffcb8f2a37ec`, `ca05a2e77babfcf262019021f985b7735a9fd896`, `38099578a67674f8dced423e77b5db3d6f9fe738`, `bc72263a9b6b9f2bd8d249768fdd55afd801f5a1` into the integration branch whose first product parent is PR #335 exact HEAD `8ee42198afa9684ffb651964f505eeb59bc02af0`. The merge commit is `dbc5049d72c50061bf92a94ccb9c5f36d2d29905`; neither source PR branch was modified.
- Resolved `MainActivity.kt`, `YujianApp.kt`, and `RecognitionVisualStateControllerTest.kt` manually. The combined wiring retains PR #335 evidence recording and `isNavigable` (including `STATIC_RESULT`), while adding PR #336's draw-receipt gates and Debug AUTO default.
- Component CI runs are source-specific only; combined tests/build/CI remain `NOT_RUN` at this checkpoint. No model or frozen visual asset was changed; no ADB, Emulator, or device execution occurred.


### Combined CI execution environment — `dbc5049d72c50061bf92a94ccb9c5f36d2d29905`

- Local `./gradlew --version` stopped before Gradle startup because the wrapper could not reach `services.gradle.org` to fetch Gradle 9.3.1. This is `NOT_RUN_NETWORK_UNAVAILABLE`; no local JVM test or APK task ran. The Android environment was not rebuilt.
- The integration tree is unchanged. Combined source tests and build gates will be evaluated by GitHub Actions at its exact source SHA; device jobs remain prohibited and will be recorded as `SKIPPED/NOT_RUN`, never PASS.
## 2026-11-22 — PR #346 Work V4.1 state bootstrap

- Work ID: `YUJIAN_PR346_CROSS_LAYER_CLOSURE_CLOUD_V4_1`; canonical Work task: `YUJIAN_WP2_13_STAGE_RECOGNITION_EXPERIENCE_RUNTIME_CLOSURE_V3`.
- Target branch `fix/26kf-path-boundary-and-fish-gate-v1` and `refs/pull/346/head` both resolve to `3eb481c1f2ca4053fc58f87729f923fb70ef7f50`; `origin/main` control-plane revision is `03db3692669847084d7e48fc3128f85cbf832af9`.
- The branch had no root `PROJECT_MASTER_STATE.yaml` or `WORK_EXECUTION_STATE.json`; initialized these branch-local dynamic files from current canonical definitions. No application source or test was changed in this bootstrap.
- The user-provided task facts remain `NOT_DEVICE_VERIFIED`: `285122.mp4` and `YuJian_Recognition_Evidence_5344335d6a51.zip` were not attached or found in the repository/workspace. GitHub REST/GraphQL requests return Forbidden; the PR web page renders a Draft badge. No build, unit test, emulator, or device action has run at this checkpoint.

### G0 correction — user supplied original evidence

- The user subsequently attached both original files. The ZIP (14,190,826 bytes) passed `unzip -t`; the video attachment was rejected by the Sediment download tool because it exceeds the 32 MiB transfer limit, so no local video copy exists.
- The initial branch state mapped the Work to the WP2 task and admission rejected its current-scope contract path. Per the user's narrowed Stage10 priority, state now maps to canonical P6 task `YUJIAN_ANDROID_RECOGNITION_RUNTIME_ROOT_CAUSE_AND_FULL_FLOW_REPAIR_V2`, whose bound current-scope contract paths resolve. No validator or Registry change was made.
- Minimal `scripts/validate_control_plane_admission.py` passed at unchanged source SHA `3eb481c1f2ca4053fc58f87729f923fb70ef7f50`; Unified Contract reported `structural_errors=0, open_gaps=1`. The historical path gap remains unchanged.
- G1 is active. The ZIP contains device records for Huawei CMS-AL20 / Android 16 / API 36, plus stage, recognition, path, geometry, result, mask, model and capture logs. Their exact source SHA and event identities are being verified before defining the regression. No production source/test change has occurred yet.

## 2026-11-22 — PR #346 Stage10–13.5 cross-layer closure candidate

- Source commit `d499a615bcaffe5ea37f8f1ff4e028af08af9e84` is on the existing `fix/26kf-path-boundary-and-fish-gate-v1` branch; no new PR was opened. The product diff does not touch the unified Registry, validators, Detector/Classifier, Mask/P*, PathLedger, or Orb GPU material implementation.
- Independently verified the supplied ZIP (`14,190,826` bytes; archive test passed; 33 recorded hash entries checked). The MP4 is over the 32 MiB Sediment limit: `REAL_DEVICE_EVIDENCE_UNAVAILABLE`; no synchronized video replay is claimed. Device logs do not expose SessionStore binding or navigation, so those device facts remain `NOT_OBSERVABLE`.
- Device-supported root conditions: geometry resolution logged `RESULT_HERO_PENDING` even though transition geometry, title bounds and reveal geometry were ready while `heroRenderReady=false`; the former Result gate required the raster receipt before allowing measured geometry. The same trace records a RETURN fallback with zero path segments rejected as `EVENT_GEOMETRY_MISMATCH`. The device trace does not prove the fallback was caused by SessionStore rejection.
- `RecognitionFishPathSnapshotBindingTest.samePStarWithSubPixelViewportProjectionDriftStillBinds` was first run red against strict pixel equality, then passed with a 1 px viewport-space endpoint tolerance; source-space P* identity, mask SHA/version and geometry version remain strict. This is a deterministic code reproduction, not a claim that the unobservable device Store rejected that field.
- Fixes add typed RECEIVED/PENDING/BOUND/rejection states with hashed identity diagnostics; Stage10 navigation evidence and retry facts; measured Result geometry readiness independent of asynchronous raster readiness; RETURN Event submission only after the real Canvas receipt; stable `asImageBitmap()` identity; and a Debug AUTO checkpoint capture hold until PixelCopy finishes.
- Local results after the same validated bootstrap used by CI: 8/8 source-module unit tasks PASS; aggregate JUnit XML 516 tests, 0 failures/errors, 0 skipped; model bootstrap PASS (classifier `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`, detector ONNX `98257b953b0e9f8aa673572fe4a9749a730ed8a2c014a42b7c492a0e3592c9a1); 26KF matrix 4/4 PASS; `:app:assembleDebugAndroidTest` PASS. The production AUTO instrumentation test now asserts Stage10 requests navigation before Result-only gates and attaches the Result route, but it is compiled only so far.
- API36 local emulator is `BLOCKED_INFRA`: no VMX/SVM/KVM and ADB cannot write `/home/agent/.android`. Added a marker-gated GitHub-hosted API36 AUTO smoke to the existing Android workflow; exact-head execution is pending push. Final Debug APK has not been built.

- Minimal recovered-state Admission on source `d499a615bcaffe5ea37f8f1ff4e028af08af9e84`: **PASS**; project/work state validators pass; Unified Contract has `structural_errors=0`, one unchanged historical `OPEN_GAP` for the pre-migration RecognitionResultScreen path. No Registry or validator edits.

## 2026-11-22 — PR #346 API36 KVM preflight

- CI-only commit `f0d8e1471627e15b6101cdf7168aa8b0e7427faf` adds direct `/dev/kvm` permission repair, emulator `-accel-check` classification, an isolated preflight-only mode, and an `ubuntu-22.04` x64 fallback. No Stage10–13.5 product source changed.
- Preflight-only run `45029788799` on that exact source completed successfully on `ubuntu-24.04`; fallback was skipped and the Android build was skipped by design. The summary reached `KVM_READY`.
- Primary preflight report artifact: `api36-kvm-preflight-ubuntu-24.04-f0d8e1471627e15b6101cdf7168aa8b0e7427faf`, 533 bytes, artifact digest `sha256:97ed5a0039ee24770dcaf1784d8622eb86037bf0149633aaec289c3230993a5b`.
- A full exact-head build plus API36 AUTO smoke is the next validation run; it will use the KVM-selected GitHub-hosted x64 runner and upload the exact-head APK pair and runtime evidence.

## 2026-11-23 — PR #346 API36 AUTO false-pass audit checkpoint

- Pre-checkpoint HEAD was `0acb593e258e1a752df0778807684fd442f40def`; the local branch is the existing PR #346 branch. The task remains Draft and no new PR is requested.
- The user reports that Run `45032036762` emitted `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` and `INSTRUMENTATION_CODE: 0` while the job reported success. The same user audit reports `lowmemorykiller Kill com.yujian.ai.uiv2`, RSS `670552kB`, swap `1257028kB`, and `reason=min watermark is breached and swap is low`.
- GitHub Actions API/raw artifact retrieval is unavailable here; Run `45032036762`'s source SHA is `NOT_CONFIRMED`. These supplied facts invalidate the reported AUTO pass, but they are not independently replayed evidence. The failure guard returned `ALLOW_FIRST_ATTEMPT`; no durable failure record is written against an unverified run SHA.
- The required Admission attempt on the pre-checkpoint source had static registry, feature, task, unified-contract structural validation and branch sync pass, with one unchanged historical `OPEN_GAP`; Work State validation failed because its `source_sha` was `d499a615bcaffe5ea37f8f1ff4e028af08af9e84` while CI changes already existed through `0acb593e258e1a752df0778807684fd442f40def`.
- Updated only branch-local live state to the verifiable code baseline `0acb593e258e1a752df0778807684fd442f40def`, kept Work `RUNNING`, and recorded the smoke false-pass audit without promoting runtime status. Project Master State and Unified Contract validators pass; Unified Contract reports `structural_errors=0`, `open_gaps=1` for the unchanged historical pre-migration path.
- No API36 smoke script, Android business code, model, or workflow has been changed at this checkpoint. Next: commit this state-only checkpoint, rerun minimal Admission, then implement fail-closed smoke validation, production business evidence, and hosted memory capture.

## 2026-11-23 — PR #346 API36 fail-closed AUTO implementation

- Source commit `dfcd04b020a3e8efd32f09180850846eb5476e5d` changes only the Android workflow, the production AUTO instrumentation test harness, and the API36 smoke scripts; no recognition product/model/Mask/P-star/PathLedger/Orb GPU implementation was changed.
- The smoke validator requires exactly one target test start and normal completion, AndroidJUnitRunner final code `-1`, no crash/failure/skip/timeout, and the ordered real Result Runtime event chain through Stage10, three-segment RETURN, glyph draw receipt, stable name, and final Result scene. It also validates source SHA, model contract SHA, geometry/navigation/RETURN/handoff artifacts and the 6,000 ms stage-duration contract.
- The requested script tests pass 3/3. Shell syntax, workflow YAML, inline Python, and diff checks pass. The memory analyzer fixture records separate encoder/decoder timings, RSS/PSS/Swap and LMK classification correctly.
- API36 AVD requests 4096M RAM and 512M VM heap; KVM preflight and smoke require 5 GiB host `MemAvailable`. Runtime evidence collection includes host/emulator meminfo, dumpsys PSS/Native Heap, process RSS/Swap, GC/LMK, actual screenshots/video, timeline/navigation/geometry/RETURN/handoff reports, and SHA-256 hashes. A single workload is explicitly insufficient to diagnose a true memory leak.
- The modified AndroidTest source has not been compiled or run locally. No local APK or emulator run occurred. The marked hosted exact-HEAD build, KVM check, API36 AUTO run and APK Pair are pending; Run `45032036762` is not accepted as PASS.

### Minimal Admission after fail-closed implementation

- `CONTROL_PLANE_ADMISSION=PASS` at state checkpoint `7d4882a9432ec247948cb32ecd26618541a86f64`. Control Plane Registry, Feature Matrix, Phase/Task State Machine, Unified Contract, Control Plane Sync, Project Master State and Work Execution State all pass. Unified Contract reports `structural_errors=0` and one unchanged historical OPEN_GAP for the pre-migration `RecognitionResultScreen.kt` path; it is not blocking.
- The final marker checkpoint changes only branch-local Work state/log files; the fail-closed implementation source remains `dfcd04b020a3e8efd32f09180850846eb5476e5d`. Pushing that checkpoint to the existing PR branch will trigger exact-HEAD build and API36 smoke.
