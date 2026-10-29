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


## 2026-10-29T05:38:22Z — exact-source W5 ownership and admission checkpoint

- Task: `YUJIAN_W4_W5_MAIN_PAGES_W6_ANDROID_RUNTIME_CLOSURE_V1`; supplemental NH01 structured Hi-Fi closure.
- Current Main: `b080bb0ffb4ebced18593c08709657fabb53a19e`. PR #234 exact candidate: `8d3f7393a027c3b83d8d1a84fca64fb55c6e6b31` (Open/Draft/Unmerged), restored to the W5-owned tree after reverting Login integration commit `cb25953a1859d3fb9b4bc25931698cf40b5a1987`; PR #239 remains the dedicated Login line at `6d2b5dc69cf9a744514a0986a249cba0d6d7ea63`.
- Exact runs: W1 `40824084875` PASS; W4 `40824084824` running; W5 `40824084796` running; Pages/W2 `40824084759` running; Design Governance `40824084752` running; PR event admission `40824084781` PASS but not PRODUCT_WORK; NH01 API 28 `40824084804` pending.
- W5 outcome is not yet established on `8d3f7393a027c3b83d8d1a84fca64fb55c6e6b31`. Login remains `NOT_RUN / NOT_CONNECTED` in W4. Product Work Admission must run on this isolated state branch with mode `PRODUCT_WORK`; required `CONTROL_PLANE_SYNC`, `PROJECT_STATE`, `WORK_STATE` are pending.
- Official Main Pages run `40819650522` targets `b080bb0ffb4ebced18593c08709657fabb53a19e` and previously verified HTTP `build.json`; post-preview root manifest/direct HTTP verification remains pending. PR preview production deploy must stay skipped.
- Native Runtime and user visual acceptance are not claimed from CI or preview; NH01, Effect 01 and AI Studio perimeter remain separately tracked.

## 2026-10-29T06:07:12Z — exact 7de2 candidate gate checkpoint

- Task: `YUJIAN_W4_W5_MAIN_PAGES_W6_ANDROID_RUNTIME_CLOSURE_V1`; supplemental `YUJIAN_NH01_STRUCTURED_HIFI_MULTI_SCREEN_RESPONSIVE_CLOSURE_V1`.
- Live baseline: Main `b080bb0ffb4ebced18593c08709657fabb53a19e`; PR #234 `7de2ebcaf3e327ce9599d73261ec4abf66afd870` Open/Draft/Unmerged; PR #239 `6d2b5dc69cf9a744514a0986a249cba0d6d7ea63` Open/Draft/Unmerged and based on current Main; PR #252 `8001e577fcdfae59f1071bbcaff1647c841ed753` Open/Draft/Unmerged.
- W1 `40826118171`, Design Governance `40826118062`, W4 `40826118110`, W5 `40826118013`, and PR-event control-plane admission `40826118209` completed successfully on exact PR #234 HEAD. W4 evidence artifact `12877100693`, SHA-256 `1fd94d35637a75c7d4d174233321cd824a9bd6345a45b5c9a241e2a9a22b3118`; W4 Pages bundle scan `12876722364`, SHA-256 `5837336087c1b5f56809779d269ffcf83c737951f536b6741ec60e62852a8435`. Login remains `NOT_RUN / NOT_CONNECTED`. W5 artifact `12876472803`, SHA-256 `0482c1edcef9234c1ed8260522f205a38ca165f409ed11ce359f8366021a5751`: 8/8 formal, 4/4 supplemental including 1.5× font, 8/8 fail-closed negatives; structural pass, product visual review pending.
- Pages/W2 run `40826118182` failed before candidate preview deployment. Resolver step selected old push run `40819650522`/artifact `12873957156`, omitting the official current Main Pages workflow_dispatch run `40825171321`; live Main `build.json` still reads `b080bb0ffb4ebced18593c08709657fabb53a19e`. Root integrity expected 1050 files and checked 1047; three generated NH01 report hashes differed. Final W2 and isolated preview deployment were skipped. Exact failure report/artifact SHA must remain FAIL until the resolver is repaired and rerun.
- Main Pages official run `40825171321` completed build and production deployment on Main `b080bb0ffb4ebced18593c08709657fabb53a19e`; direct HTTP `design/manager/build.json` matches. Main root manifest report digest remains to be extracted from this run's release evidence.
- Product Work Admission on the isolated state branch is pending for current source `7de2ebcaf3e327ce9599d73261ec4abf66afd870`; previous run `40824835460` is historical for source `8d3f739`.
- Bounded retry check for `G-DESIGN-MANAGER-PAGES-ISOLATED-PREVIEW / PAGES_RESOLVER_EXCLUDES_FORMAL_MAIN_WORKFLOW_DISPATCH` returned `ALLOW_REPAIR` at 1 prior failure; `failure_gate.py record` retained this second exact-SHA FAIL and new evidence. Do not reuse a different SHA's result.
- NH01 API 28 run `40826118440` is still in progress. PR #252 Android CI run `40826129796` built APKs and its API 28 runtime matrix is in progress/queued; no NH01, Effect 01, or perimeter native runtime PASS is claimed from builds alone.
- Next: commit this isolated state snapshot with `source_sha=7de2…`, run explicit `PRODUCT_WORK` Admission, repair only the Main event selection in the Pages resolver, and rerun exact-SHA gates. User visual acceptance remains pending.

## 2026-10-29 — exact PR #234 checkpoint 6933f459d2223823a25edcd1d4598ffde3061267

- Main: `b080bb0ffb4ebced18593c08709657fabb53a19e`. PR #234 is Open/Draft/Unmerged at exact HEAD `6933f459d2223823a25edcd1d4598ffde3061267`.
- W1 `40828169507` and Design Governance `40828169495` passed; PR-event Admission `40828169471` passed but is not Product Work Admission.
- W4 `40828169489` failed before Login exact-SHA evidence generation. W5 `40828169461` and Pages/W2 `40828169492` failed due three `SOURCE_AUTHORITY_SHA_MISMATCH` errors: pinned workflow SHA `51f8d59ab19dd2a974381991038a2f746a4b30ab73422b1894d2f01df16f7200`, candidate workflow SHA `e1d7c6555b83570e92769244b83c5024113c683cc1db5f51e99d0f7ac8e8871c`. No isolated preview deployed.
- NH01 Android run `40828169459` failed source/resource preflight; native runtime remains `NOT_VERIFIED`. PR #239 remains blocked and Draft. Product Work Admission for `6933f459d2223823a25edcd1d4598ffde3061267` is pending. User visual acceptance is pending.

## 2026-10-29T06:34:44Z — Product Work Admission failure on 6933f459d2223823a25edcd1d4598ffde3061267

- Exact Actions run: https://github.com/pan277942135/Yujian_App/actions/runs/40829566256. Mode `PRODUCT_WORK`, source SHA `6933f459d2223823a25edcd1d4598ffde3061267`. `CONTROL_PLANE_SYNC=FAIL`; `PROJECT_STATE=PASS`; `WORK_STATE=PASS`.
- Exact failure: `extra/divergent canonical file on Work branch: design/pages/account_privacy/Login/structured_candidate/component_mapping_v1.json`. The Login renderer/mapping change is already present in PR #239; PR #234 must not own this canonical page authority. Preserve the commit history and retain Login in PR #239.

- Bounded failure guard for the new admission-scope symptom on `6933f459d2223823a25edcd1d4598ffde3061267`: check `ALLOW_FIRST_ATTEMPT`; record `LOCALIZE_AND_REPRODUCE`, failure ID `cebf6c13d5dc6fc4c1f8`. This is a repair allowance only, not a PASS. Checkpoint `2026-10-29T06:35:39Z`.

- 2026-10-29T06:41:37Z scope checkpoint: PR #234 moved forward from 6933f459d2223823a25edcd1d4598ffde3061267 to 91418127df91b9854897f24eeb84c970cb2e6563; duplicate Login canonical mapping removed, Login remains owned by PR #239, W4 stale-workspace regression and reference-image readiness fixes retained. Product Work Admission retry is pending on exact source 91418127df91b9854897f24eeb84c970cb2e6563.

- 2026-10-29T06:46:36Z Admission checkpoint: run 40830891497 on exact source 91418127df91b9854897f24eeb84c970cb2e6563: CONTROL_PLANE_SYNC=PASS, WORK_STATE=PASS, PROJECT_STATE=FAIL due runtime_gates.exact_head.sha drift. Failure gate check allowed first attempt; record id c4172a251dfae944cfa6. Updating only isolated state pointer before same-source re-admission.

- 2026-10-29T06:51:00Z Admission PASS checkpoint: Product Work Admission run 40831382946 on state commit c9bd5e0c4b3eaea1e85d3f6f52a790ec3eb59615, source 91418127df91b9854897f24eeb84c970cb2e6563: CONTROL_PLANE_SYNC=PASS, PROJECT_STATE=PASS, WORK_STATE=PASS. Exact-source downstream runs: W1 40830549559 PASS; W2/Pages 40830549495 FAIL on three workflow SHA pins; W4 40830549596 FAIL at upstream W2; W5 40830549593 FAIL at W2. Next: Main-first authority sync.
