## 2026-10-29 — Exact Admission rerun on current candidate

- Re-ran python scripts/validate_control_plane_admission.py against exact PR candidate 2b45c9c7fe50e9966be35ed4e658bd2d5cecb3ba after syncing Main 0c245b86f8228107309d0135bdf819af255604be and validating the branch state files.
- Result: PASS — 6 control-plane layers, 735 synchronized paths, one exact registered Login mapping exception, Project Master State PASS, Work Execution State PASS.
- The Login component mapping is required by the registered W6-A Login task and the Main exception; it remains preserved. The Empty Home brand authority gap remains blocked and is reported explicitly.
- This entry precedes the state-only checkpoint; rerun Admission on that checkpoint before W5/Pages evidence.

# Branch-local Work Build Log

## 2026-10-29 — PR #234 remote candidate source synchronized

- Live PR #234 remains Draft/Open/Unmerged; its branch was advanced by a fast-forward commit to exact code source `73cc6206eb146ada5120363ad7cf73fa8023908a` containing only the Empty Home workspace, mapping, CSS, and W5 verifier changes.
- Baseline Admission passed on `5de9100b452a82ca3c1b6a7f8fc9015f8c2b1fbc` against Main `0c245b86f8228107309d0135bdf819af255604be` before these page edits. The registered W6-A Login mapping remains untouched at blob `75c615d20be011b16e4e6843b41dabd0479a8d63`.
- Exact candidate checkpoint `8bae4dbdb9276b27da47ea99210f7ee35306d428` passed `python scripts/validate_control_plane_admission.py`: six control-plane layers, 735 synchronized paths, one exact Login exception, Project Master State and Work Execution State all PASS. Product code source remains `73cc6206`.
- This log/state update records that result and creates a state-only checkpoint. Rerun Admission on that resulting exact candidate before relying on formal W5/Pages evidence. The prior local-only commit IDs are not PR candidate IDs.
- Next action: rerun Admission on the final state-only SHA, then inspect the W5 matrix and isolated Pages preview.

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


## 2026-10-29 — Work P0 exact-SHA checkpoint

- Work: `YUJIAN_THREE_PILOT_PAGE_SPEC_CLOSURE_AND_IMPLEMENTATION_GATE_V1`; current Main `b080bb0ffb4ebced18593c08709657fabb53a19e`; current candidate `8d3f7393a027c3b83d8d1a84fca64fb55c6e6b31` on PR #234.
- Main recovery is confirmed: PR #247 merged, followed by contract registration PR #250 and Login matrix binding PR #251; PR #251 produced current Main `b080bb0ffb4ebced18593c08709657fabb53a19e`.
- Exact candidate checks: W1 run [40824084875](https://github.com/pan277942135/Yujian_App/actions/runs/40824084875) PASS; W2 resource report run [40824084759](https://github.com/pan277942135/Yujian_App/actions/runs/40824084759) PASS with warnings; W4 evidence run [40824084824](https://github.com/pan277942135/Yujian_App/actions/runs/40824084824) completed, but its per-page manifests report NH01 `NOT_RUN`, Empty Home `PASS`, and Login `NOT_RUN`. The current task therefore does not treat aggregate W4 green as all-page PASS.
- NH01: the real Pages/Chromium run [40824084759](https://github.com/pan277942135/Yujian_App/actions/runs/40824084759) verifies the exact isolated preview, shared layer tree/DOM/Inspector interactions, route navigation, failure/recovery probes, responsive 24/24 and 5/5 continuity probes with 10/10 cases. Page Profile is `FROZEN_BINDING`, Envelope `SPEC_FROZEN`; runtime user photo/avatar sources remain dynamic `NOT_VERIFIED`, and generated Hero/Previous/Next previews remain `GENERATED_ILLUSTRATIVE_ONLY`/optional, never formal assets. Technical page status: `SPEC_READY_FOR_IMPLEMENTATION`; visual acceptance remains pending.
- Empty Home: W5 run [40824084796](https://github.com/pan277942135/Yujian_App/actions/runs/40824084796) PASS: 8/8 formal, 4/4 supplemental, 8/8 negative fail-closed. W2 binding SHA-256 `c52119cbf4a6bc062f5d66a531f6e106e4aff15e486f58df1ed6adfcc59bbbee`; 17 required assets remain `REVIEW`, including scene/fishing/atmosphere/button assets; Gallery and Chevron dimensions are not verified. Envelope/Profile remain DRAFT. No lifecycle status was promoted.
- Login: current candidate `8d3f7393a027c3b83d8d1a84fca64fb55c6e6b31` has no shared Login renderer route. The exact-SHA W4 log [40824084824](https://github.com/pan277942135/Yujian_App/actions/runs/40824084824) records `unconnected_policy=not-run`. Login Profile/Envelope are DRAFT; its formal matrix has 1 case and 12 supplemental candidate cases, plus 10 negatives and 9 candidate state probes. The two preserved exact-SHA failures are run [40822639810](https://github.com/pan277942135/Yujian_App/actions/runs/40822639810), 430x932/font 1.0 with root 968, and run [40823945023](https://github.com/pan277942135/Yujian_App/actions/runs/40823945023), 320x480/font 1.5 with root 758. Failure-gate ledger records both. Approved upload source remains OPTIONAL/MISSING with the frozen Login PNG degradation; Footer Grass is NOT_VERIFIED.
- Isolated preview run [40824084759](https://github.com/pan277942135/Yujian_App/actions/runs/40824084759) deployed only under [`/previews/pr-234/`](https://pan277942135.github.io/Yujian_App/previews/pr-234/), candidate build `8d3f7393a027c3b83d8d1a84fca64fb55c6e6b31`; live `source-manifest.json` and `design/manager/build.json` checks matched that SHA. Production deploy was SKIPPED; live root remained Main `b080bb0ffb4ebced18593c08709657fabb53a19e`, 1050 files, manifest SHA-256 `6b732803ef26b38249bdc1d77a817080559bc50d772ee235eda503689d06ce3c`.
- Android Runtime run [40824084804](https://github.com/pan277942135/Yujian_App/actions/runs/40824084804) is still IN_PROGRESS on `8d3f7393`; this is separate from browser/spec gates. Android implementation has not started because Empty Home and Login remain blocked.
- Per `AGENTS.md` and `harness/STATE_UPDATE_PROTOCOL.md`, live Work state was added only to root `PROJECT_MASTER_STATE.yaml`, root `WORK_EXECUTION_STATE.json`, and this log. Static Control Plane files remain unchanged; the task is already registered in Main's machine/UCR.

Next action: restore Login inside the existing shared Design Manager, retain all geometry/content assertions while interpreting the declared candidate scroll rule, and rerun every formal, supplemental, negative, state, timeout, and recovery case on one exact SHA.

## 2026-10-30 — PR #234 current-Main admission recovery

- Current Main: bf4c5c5777bddd3c37c8b2850aaa5a5881f0d513. PR #234 prior head: ffd9cfe69ff52c776049953b730f6dabb90fe44a; the prior admission result is historical to its older Main baseline.
- Synchronized AGENTS.md, the Structured Hi-Fi Fast Track playbook, and the asset-integrity register from current Main.
- Preserved Login mapping blob 75c615d20be011b16e4e6843b41dabd0479a8d63 (source commit 9c47519e87260992bc768178399fc9b6651a6fc8); exact W6-A exception merged via PR #259 at bf4c5c5777bddd3c37c8b2850aaa5a5881f0d513.
- Candidate control-plane sync source: d72adf5a02788de59125d56e760224c5efda393f. Empty Home page files remain unchanged.
- Admission for the state checkpoint is pending; no page inspection, visual acceptance, freeze, product merge, or Android work is claimed.


## 2026-10-29 — PR #234 current-Main state checkpoint

- Live Main is `a14d03fa78db0a1cb9b007f8e3d983b8a3b85754` after control-plane validator PR #261 merged.
- Re-read current `AGENTS.md` and the Structured Hi-Fi Fast Track V1.1 playbook; the candidate branch already matches both current Main files.
- Synchronized the five stale canonical controls from Main: `CONTROL_PLANE_CHANGE_SYNC.md`, `FEATURE_MATRIX.yaml`, `PHASE_TASK_STATE_MACHINE.yaml`, `UNIFIED_CONTRACT_REGISTRY.yaml`, and `scripts/validate_control_plane_sync.py`.
- Preserved Login mapping `design/pages/account_privacy/Login/structured_candidate/component_mapping_v1.json`, blob `75c615d20be011b16e4e6843b41dabd0479a8d63`, because W6-A requires it and Main registers the exact non-frozen path/blob exception.
- Current control-plane sync on the code-sync source `dad76a9ca737245d4076b75b86537412b0238765`: **PASS**, 735 paths compared, 1 exact approved exception. The root state files now point to Main `a14d03fa78db0a1cb9b007f8e3d983b8a3b85754` and this source SHA.
- Empty Home page files remain unchanged. Exact Admission on the final state-only checkpoint is still pending; no visual acceptance, freeze, product merge, or Android work is claimed.


## 2026-10-29 — Main advanced during admission recovery

- Main advanced from a14d03fa78db0a1cb9b007f8e3d983b8a3b85754 to 0c245b86f8228107309d0135bdf819af255604be when PR #258 merged. The product candidate remains code-source dad76a9ca737245d4076b75b86537412b0238765; only the state snapshot was rebound.
- Re-fetched Main. Canonical control-plane sync remains PASS on the candidate: 735 paths compared, 1 exact approved Login mapping exception, 0 errors.
- Updated both root state files to current Main 0c245b86f8228107309d0135bdf819af255604be. Exact Admission must be rerun on the final state-only candidate SHA; no page code has changed.


## 2026-10-29 — Admission attempt and state-file correction

- Exact candidate 16d241c65c41bad1593be4976453cb9746681c0f ran python scripts/validate_control_plane_admission.py. Control Plane and Project Master State passed; Work Execution State failed JSON parsing because its final two characters were a literal backslash and n (Extra data at line 48, column 2).
- Corrected only branch-local state files and recorded this failed attempt. The code source remains dad76a9ca737245d4076b75b86537412b0238765; page files are unchanged. Exact Admission is pending on the corrected state-only candidate.
