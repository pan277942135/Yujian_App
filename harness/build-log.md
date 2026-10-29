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
