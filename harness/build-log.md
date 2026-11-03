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
