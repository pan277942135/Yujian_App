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


## 2026-10-28 — W5 isolated Work-state checkpoint after Main sync

- Main is now `632cada103bf022adeb873bd6e66464555b509e4`; this arrived after PR #242 merge through a separate canonical task registration. PR #234 branch was updated with a normal two-parent merge commit `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`, parented by prior candidate `27fae3faa7c4766abb474f8eee11d84009471ac6` and Main `632cada103bf022adeb873bd6e66464555b509e4`.
- The three Main-updated canonical control files and the new AI Studio perimeter-shader contract are synchronized into the W5 branch. The PR tree has no branch-local state files, so its canonical-promotion admission remains eligible to pass.
- Branch-local `WORK_EXECUTION_STATE.json` and `PROJECT_MASTER_STATE.yaml` are isolated on `work-state/w5-empty-home-pilot-v1`; their execution/source baseline is the exact PR candidate SHA `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`. They are not part of PR #234's proposed Main tree.
- PR #234 W4 Evidence Run `40667570169` and W5 Matrix Run `40667570176` were in progress at checkpoint time on the exact candidate SHA `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`; their final results must be re-read, not inferred.
- Main Pages run `40666041684` for Main SHA `632cada103bf022adeb873bd6e66464555b509e4` is `cancelled`; Main's prior Pages run at `80c55961059b95fd8dd52eea4b52a37bd91705e8` had succeeded. Current production root SHA remains unconfirmed relative to latest Main.
- Product-work Admission must be manually dispatched on ref `work-state/w5-empty-home-pilot-v1` via `.github/workflows/control-plane-admission.yml`. PR event admission is intentionally canonical-promotion mode and is not a substitute. No W4 source patch is authorized until the product-work Admission result is PASS.


## 2026-10-28 — Exact-HEAD W4/W5 browser results

- PR #234 candidate SHA `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`: W5 Chromium Matrix Run [40667570176](https://github.com/pan277942135/Yujian_App/actions/runs/40667570176) PASS, 12/12 cases (8 formal + 4 supplemental), 8 negative cases; visual review remains pending.
- W4 Evidence Run [40667570169](https://github.com/pan277942135/Yujian_App/actions/runs/40667570169) FAIL on the same SHA. Workspace verifier fixture suite reports 11 cases PASS, but the actual page workspace report is `home_normal_v1=FAIL`, `home_empty_v2=PASS`, `auth_login_v2=NOT_RUN`; therefore W4 is not PASS.
- PR #234 canonical-promotion Admission Run [40667570171](https://github.com/pan277942135/Yujian_App/actions/runs/40667570171) PASS in `CANONICAL_MAIN_PROMOTION` mode only. Product-work Admission has not run; the manual dispatch must target `work-state/w5-empty-home-pilot-v1`.
- PR candidate Pages run [40667570170](https://github.com/pan277942135/Yujian_App/actions/runs/40667570170) is queued at this checkpoint. Main SHA `632cada103bf022adeb873bd6e66464555b509e4` Pages run [40666041684](https://github.com/pan277942135/Yujian_App/actions/runs/40666041684) is cancelled; live root SHA relative to current Main remains unconfirmed.
- No W4 source repair has been pushed. Continue only after product-work Admission PASS, then apply the preserved workspace-scope fix and rerun W4/W5 on the new exact SHA.


## 2026-10-28 — Isolated preview composition progress

- PR #234 candidate SHA `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`, Design Manager Pages Run [40667570170](https://github.com/pan277942135/Yujian_App/actions/runs/40667570170): live-root SHA-matched artifact resolution PASS; current production artifact download PASS; byte-preserving isolated preview composition PASS; candidate preview verification under its isolated Pages path PASS; final W2 resource-binding validation PASS.
- The build job is still running `Run Empty Home V2.2 real DOM matrix and negative checks`. Artifact upload and the `deploy-preview` job have not yet been confirmed. Do not call the isolated preview deployed until the run completes and its deploy job passes.
- W4 Evidence Run `40667570169` remains FAIL on this SHA; W5 Chromium Matrix Run `40667570176` remains PASS (12/12). Product-work Admission still requires manual dispatch on `work-state/w5-empty-home-pilot-v1` before any W4 source patch.


## 2026-10-28 — Isolated preview deployment checkpoint

- Design Manager Pages Run [40667570170](https://github.com/pan277942135/Yujian_App/actions/runs/40667570170) build job is PASS. The `deploy-preview` job verified the existing production root under the shared deployment lock and confirmed a matching successful artifact; isolated preview deployment is still in progress.
- The Main Pages run for latest Main SHA `632cada103bf022adeb873bd6e66464555b509e4` remains cancelled, so the latest Main SHA itself is not claimed deployed. This does not negate the separate live-root/artifact guard used for the isolated preview.
- The remaining product blockers are product-work Admission (manual dispatch required) and W4 exact-SHA Evidence FAIL on `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`. W5 matrix remains PASS 12/12 on that same SHA.


## 2026-10-28 — Final exact-SHA W4/W5 and isolated-preview checkpoint

- Product candidate remains PR #234 HEAD `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`, Draft/Open/Unmerged. Main remains `632cada103bf022adeb873bd6e66464555b509e4`.
- W5 Chromium Matrix [Run 40667570176](https://github.com/pan277942135/Yujian_App/actions/runs/40667570176): **PASS**, 12/12 matrix cases and 8 negative cases; visual acceptance remains pending.
- W4 Evidence [Run 40667570169](https://github.com/pan277942135/Yujian_App/actions/runs/40667570169): **FAIL** on this exact SHA. Archived report `browser/home_normal_v1/workspace-browser-report.json` records `WORKSPACE_CHECK_FAILED: component tree node page is disabled and cannot be selected in the actual workspace`; `home_empty_v2=PASS`; `auth_login_v2=NOT_RUN` because it is reference-image-only and not connected. The 11 verifier fixture cases passing does not override the actual page gate. Artifact: [W4 evidence ZIP](https://github.com/pan277942135/Yujian_App/actions/runs/40667570169/artifacts/12807320489).
- Design Manager Pages [Run 40667570170](https://github.com/pan277942135/Yujian_App/actions/runs/40667570170): **SUCCESS**. Isolated preview is live at https://pan277942135.github.io/Yujian_App/previews/pr-234/ and its build SHA is verified as `5ba86fd8d1142d4708d1b8f9e6ddc81cbc96ccf6`. Live candidate browser verification and W2 resource validation passed. This is an isolated preview, not Main production deployment.
- The isolated-preview deployment verified the current production root as `80c55961059b95fd8dd52eea4b52a37bd91705e8` (Pages Run `40665646629`, artifact `12806302330`). The latest Main `632cada103bf022adeb873bd6e66464555b509e4` Pages run `40666041684` was cancelled; do not claim latest Main is live.
- Product-work Admission remains **NOT_RUN**. Run `40667570171` is PR-event `CANONICAL_MAIN_PROMOTION` mode only. Required next gate is a manual `workflow_dispatch` of `.github/workflows/control-plane-admission.yml` on ref `work-state/w5-empty-home-pilot-v1`, explicitly checking `PRODUCT_WORK` and `CONTROL_PLANE_SYNC + PROJECT_STATE + WORK_STATE`. Do not apply the preserved W4 source patch before that gate passes.
- State files were updated on the isolated state branch only; no state files are being promoted through PR #234. W4 remains FAIL, Main deployment remains stale relative to latest Main, and user visual acceptance remains pending.


## 2026-10-28 — W4 scope-fix and Main Pages production checkpoint

- Current Main is `2fab34d20ccd7f2d6f9a0d22dd65cac5d5bd5e98`. Official Main Pages run [40789167725](https://github.com/pan277942135/Yujian_App/actions/runs/40789167725) completed build and deploy successfully on that exact SHA. Live production `design/manager/build.json` reads `2fab34d20ccd7f2d6f9a0d22dd65cac5d5bd5e98`; real browser verification passed for 23 bound resources, navigation, exact source layer tree, bidirectional selection, viewport/font changes, missing-resource/recovery probes and NH01 responsive preview. Production `github-pages` artifact 12861340836 has SHA-256 `ac9acbebfdf80d3b7a20d9d1585ac1b6ae0e09726488462c8c4afa1e51087f01`.
- PR #234 exact candidate `b7dfbde0b8b3cf4920c260096ce04c4585f3ca7d`: W5 matrix [40790142122](https://github.com/pan277942135/Yujian_App/actions/runs/40790142122) PASS; W4 [40790142020](https://github.com/pan277942135/Yujian_App/actions/runs/40790142020) FAIL in the new fixture harness because `stale-workspace` correctly returned PASS but the harness expected FAIL. Its evidence confirms the hidden old-SHA root and disabled `page` node were excluded while the exact visible root was measured. Fix expected classification only; keep the disabled-node negative assertion.
- PR Pages [40790141941](https://github.com/pan277942135/Yujian_App/actions/runs/40790141941) failed because its live-root resolver filters successful Main Pages runs to event `push`, excluding the successful official `workflow_dispatch` publication on the same verified Main SHA. Update the allow-list to include `workflow_dispatch`, still require exact SHA plus successful build, deploy step, unexpired Pages artifact, and live `build.json` match.
- Native runtime remains NOT_VERIFIED. NH01 standalone Hero/Peek approved real-image sources have not been established; illustrative images remain explicitly `GENERATED_ILLUSTRATIVE_ONLY`. `USER_VISUAL_ACCEPTANCE_PENDING`.
- Next: make the two narrow fixture/workflow corrections, commit a new PR #234 SHA; update this isolated state branch with that exact source, rerun PRODUCT_WORK Admission, then rerun W4/W5/W2/Pages/Governance on the same exact candidate.


## 2026-10-28 — PR #234 exact candidate checkpoint after W4/Pages correction

- PR #234 was updated by non-force, expected-SHA GitHub ref update. New candidate: `33b89b345d0f5c5bf9df3fe53ed3d583070c820f` (parent `b7dfbde0b8b3cf4920c260096ce04c4585f3ca7d`); Main remains `2fab34d20ccd7f2d6f9a0d22dd65cac5d5bd5e98`. Changed only the W4 verifier, its regression fixture, and `design-manager-pages.yml` Main run resolver.
- W4 regression fixture now expects the hidden stale workspace case to PASS after its disabled old-SHA node is excluded, while the direct disabled-node fixture must remain FAIL with the original failure reason and diagnostic screenshot/DOM. The verifier reports failure cause, workspace candidates, DOM geometry and screenshot digests without replacing the original exception with secondary capture errors.
- PR Pages accepts `push` or `workflow_dispatch` only; exact workflow ID, main branch, live build.json SHA, build success, deploy success and nonexpired Pages artifact checks remain.
- Exact-SHA Actions and product-work Admission for `33b89b345d0f5c5bf9df3fe53ed3d583070c820f` are pending. Previous b7 W4/Pages failures remain historical and recorded in `WORK_EXECUTION_STATE.json`; b7 W5 PASS is not reused.


## 2026-10-29 exact-SHA recovery checkpoint

- Current Main remains `2fab34d20ccd7f2d6f9a0d22dd65cac5d5bd5e98`. PR #234 remains Draft/Open/Unmerged; exact candidate is `27ab653f1020b17e46292e5ff380ed781e0026af`.
- Remote commit `33b89b345d0f5c5bf9df3fe53ed3d583070c820f` had incorrect GitHub blobs: the Pages workflow was empty and both workspace verifier files were truncated. W2 reported the missing workflow authority as the empty-file SHA.
- Restored six complete files in commit `27ab653f1020b17e46292e5ff380ed781e0026af`: the Pages workflow, W4 verifier, W4 fixture, and the three canonical resource bindings with the exact SHA-256 of the restored workflow. Publication markers and existing asset lifecycles were left intact.
- Current exact-SHA runs: W4 `40791350065`, W5 `40791349936`, Pages `40791350459`, W1 `40791350200`, Governance `40791350517`. All were still running at checkpoint time. PR-event Admission `40791349924` is static only; PRODUCT_WORK Admission remains NOT_RUN. Native NH01 runtime workflow `40791350265` was skipped.
- Prior 33b89b3 W4/W5 failures remain recorded; no PASS or user acceptance is inferred.


## State-tree validation correction — 2026-10-29T00:07:06+00:00

- The first isolated checkpoint commit `9a71b34e1b85441f724b2b3d01adc5029caa049c` preserved state files but used the prior state tree as its content base; exact provenance validators correctly rejected it because product files disappeared relative to source `27ab653f1020b17e46292e5ff380ed781e0026af`.
- Rebuild the next checkpoint from the product source tree and overlay only `PROJECT_MASTER_STATE.yaml`, `WORK_EXECUTION_STATE.json`, and this work log; preserve both parent commits. No product candidate file is being modified by this state correction.

## 2026-10-29T00:18:38.467352+00:00 — YUJIAN_W4_W5_MAIN_PAGES_W6_ANDROID_RUNTIME_CLOSURE_V1_2026-10-29 / admission and W4 checkpoint

- Current Main: `2fab34d20ccd7f2d6f9a0d22dd65cac5d5bd5e98`; PR #234 HEAD: `27ab653f1020b17e46292e5ff380ed781e0026af` (Draft/Open/Unmerged); PR #239 remains Draft/Open/Unmerged.
- W4 run `40791350065` FAIL; exact artifact `12861782024`, SHA-256 `394717e6d3af22b6fa5a46c289fbe135a7b9de464d438e3bcb34878e77cf7379`. Actual workspace is unique/visible and exact SHA; `page` tree row reports disabled=false/selectable=false because NH01 renderer omitted explicit selectability metadata.
- W5/W2 run `40791349936` PASS: 8 formal + 4 supplemental cases, 8 negative cases, W2 3 pages; artifact `12861278328`, SHA-256 `712bf3ca3eea0668fa92f3d8fbcaad30829e70dbf920b955f18691941ac937ae`.
- PRODUCT_WORK Admission FAIL: registry, feature matrix, task state machine, unified contract, and both state validators PASS; CONTROL_PLANE_SYNC fails exactly the Login, Empty Home, and Normal Home canonical bindings against Main workflow authority. Support PR #246 exact HEAD `9ec94dff0956a7ae371cc006075db345fcd0643c` contains only the workflow plus those three pins; checks pending.
- Pages PR preview run `40791350459` PASS; preview artifact `12862505381` SHA-256 `c5dfcd96f8739a9fdf6a22f8d9895ac3ff15c84c971cb40517a98b053053e3c5`; production deployment skipped.
- Android API 28 native NH01 workflow on PR #234 was opted in by adding label `run-nh01-api28`; runtime result is pending. No native runtime PASS asserted.
- Next: finish PR #246 checks and authority sync, rerun PRODUCT_WORK Admission with exact updated state/source SHA, then fix NH01 tree renderer and rerun W4/W5/W2/Governance/Pages on the new SHA.


## 2026-10-29T00:29:11Z — Exact source 92e1 checkpoint before PRODUCT_WORK Admission

- Live Main is `1d851b98e550cdc4d0c5c6f74ffedcf194e126f4`. PR #234 remains Draft/Open/Unmerged at `92e1b4282ae5de2f6528ddcc8e59e401ecb99b4a`, based on Main `1d851b98e550cdc4d0c5c6f74ffedcf194e126f4`; PR #239 remains Draft/Open/Unmerged at its stale base `874beafb9cb865e2577f72888a380ff5aee9c1a0`.
- Support PR #246 is merged into Main as `1d851b98e550cdc4d0c5c6f74ffedcf194e126f4`; it resolved the canonical Pages workflow hash divergence. Exact PRODUCT_WORK Admission is not yet run on source `92e1b4282ae5de2f6528ddcc8e59e401ecb99b4a`.
- W1 Run 40793804425 PASS. W4 Run 40793804470 FAIL, artifact 12863181100, SHA-256 `013c5531c1a216f7d21bfebd3b83e8d8e2e37bdbf3bf73778970e12023a6b2f2`. W5 Run 40793804466 PASS (8 formal + 4 supplemental, 8 negative), artifact 12862652295, SHA-256 `d4f7a2a703b43d1681a02db9f45dce926a58a0ec725117f97bff0da5bb3db3e2`. Design Governance Run 40793804504 PASS.
- PR Pages Run 40793804507 is still running; no preview deployment claimed yet. Main Pages Run 40793680312 build and deploy steps succeeded on exact Main `1d851b98e550cdc4d0c5c6f74ffedcf194e126f4`; production HTTP/browser consistency verification remains pending.
- NH01 API28 run 40793804441 failed before Gradle/APK/emulator steps because the Python validator environment lacked PyYAML. Native runtime is NOT_VERIFIED; no APK evidence from this run.
- Recovery: Admission first; then fix only the W4 verifier semantics while retaining native-disabled and explicitly non-selectable negative fixtures; rerun all affected gates on the resulting exact SHA.


## 2026-10-29T00:34:52Z — PRODUCT_WORK Admission exact-source PASS

- Actual GitHub workflow_dispatch Run [40794591711](https://github.com/pan277942135/Yujian_App/actions/runs/40794591711) ran against isolated state branch `work-state/w5-empty-home-pilot-v1` checkpoint `3dbc93805a207a894935d15e257709d9c5e9e8da`, with product `source_sha=92e1b4282ae5de2f6528ddcc8e59e401ecb99b4a` and `Main=1d851b98e550cdc4d0c5c6f74ffedcf194e126f4`.
- Job log explicitly reports `mode=PRODUCT_WORK branch=work-state/w5-empty-home-pilot-v1`, `CONTROL_PLANE_SYNC=PASS`, `PROJECT_MASTER_STATE_VALIDATION=PASS`, `WORK_EXECUTION_STATE_VALIDATION=PASS`, and `CONTROL_PLANE_ADMISSION=PASS`. No state docs entered PR #234.
- This closes only the historical workflow-binding divergence. W4 remains FAIL at 92e1; W5 and Design Governance remain PASS; PR Pages and production HTTP verification remain separate.
- Next: W4 verifier change is now authorized by the passing Admission; preserve all fail-closed checks and re-run admission on the resulting exact product SHA.
