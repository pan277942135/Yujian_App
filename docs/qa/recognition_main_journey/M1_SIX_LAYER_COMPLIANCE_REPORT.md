# M1 Six-Layer Control Plane Compliance Report

**Task:** `M1_SIX_LAYER_COMPLIANCE_AUDIT_V1`
**Parent Work:** `YUJIAN_M1_PARALLEL_DELIVERY_V1`
**Repository / PR:** `pan277942135/Yujian_App` / [Draft PR #163](https://github.com/pan277942135/Yujian_App/pull/163)
**Initial product source:** `81aefb1c0eae08df5ea6f451fc7c65be89a9c191`
**Latest exact-head state and CI audit:** `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`
**Canonical Control Plane:** `main@0d3238e8a41b87363e9a17b04c9cc45fca4c426d`
**Task comment:** [#6244627483](https://github.com/pan277942135/Yujian_App/pull/163#issuecomment-6244627483)
**Audit outcome:** `REVIEW_REQUIRED` — Admission and state recovery pass; L2/L4 mappings and exact-SHA hosted Runtime remain open.

## Audit method and evidence boundary

The audit was run in a product worktree checked out at the live PR #163 head, not in the `main` promotion context. The required entry point was `python scripts/validate_control_plane_admission.py`. Dynamic project and work state were kept outside the public repository, persisted in a private durable checkpoint, restored in an isolated recovery rehearsal, and checked by exact SHA-256 before Admission was rerun. Raw state and protected logs are not included in this PR.

The first Admission result is retained as a failure record: static validation and synchronization passed, but both branch-local state files were missing. After initialization and private persistence, the restored files matched their checkpoint hashes and the complete Admission command exited `0`. Historical and current evidence are distinct and are not relabeled.

## Six-layer result

| Layer | Authority and check | Result | Evidence and open work |
|---|---|---|---|
| L1 · Project Master State | `PROJECT_MASTER_STATE.yaml`; `scripts/validate_project_master_state.py` | `PASS` after repair | Initial exact-head Admission failed because Project Master State and Work Execution State were absent. Private checkpoint restore reproduced SHA-256 `bb41037e…ac4294` and `5a6b654b…54b3`; both validators passed, followed by full Admission PASS at source `81aefb1`. Keep updating the private checkpoint as the PR SHA changes. |
| L2 · Feature Matrix | `harness/FEATURE_MATRIX.yaml`; `scripts/validate_feature_matrix.py` | `REVIEW_REQUIRED` | Structural check passed: 15 features, J01–J20, 22 routes, zero declared open gaps. M1 page/state/test-to-Android bidirectional traceability must be confirmed from the integrated Design Authority Matrix. PR #162’s two-page guide remains Draft/unmerged; `main` still has the three-page contract and must not be reported as two-page. |
| L3 · Phase / Task State Machine | `harness/PHASE_TASK_STATE_MACHINE.yaml`; `scripts/validate_phase_task_state_machine.py` | `REVIEW_REQUIRED` | Static check passed: 10 phases, 45 tasks, N001–N010. N009 remains the human release-authorization gate; retry policy remains bounded to the initial attempt plus at most two retries. Existing P5-02 still references the retired self-hosted API28 runner and N010 still requires three guide pages, so both operational bindings need an authorized Control Plane change before they can represent active M1 targets. |
| L4 · Unified Contract and design authority resolution | `harness/UNIFIED_CONTRACT_REGISTRY.yaml`; `scripts/validate_unified_contract.py`; Design Manager / Registry / Frozen Asset / Page Contract | `REVIEW_REQUIRED` | Structural check passed: 15 features, 20 journeys, 79 child surfaces, 22 routes, 45 tasks, 11 gates, no structural errors. The latest PR task comment sets `State_Machine_V3_1.json` as the M1 phase-timing authority: 220/320/350/250ms minimums and real Pipeline events. V1.1 timing values are historical. V1.2 Resolve 200ms visual behavior conflicts with V3.1 immediate RESULT handoff for late results and remains unresolved. Page Contract validator currently reports 658 expected versus 669 active asset entries; protected Registry and validator were not changed. |
| L5 · Evidence Gate | Task machine, unified gate bindings, `scripts/run_android_runtime_gate.sh` | `BLOCKED_INFRA` | PR #163 Android CI run `39086281726` has a successful Build job but `android-runtime` and `normal-home-brand-title-validation` are `SKIPPED`; it is not Runtime evidence and used the PR merge ref rather than exact source-head provenance. No hosted API28 preflight, exact-source Runtime gate, M1 APK, screenshots, motion trace, bounds, or Pixel Diff exist yet. |
| L6 · Automatic Recovery | Task machine, `harness/STATE_UPDATE_PROTOCOL.md`, private branch checkpoint | `PASS` for recovery rehearsal | Both private state files were recovered from the persisted checkpoint, exact hashes matched, and Master/Work/Admission validators passed at source `81aefb1`. The original missing-state Admission failure remains preserved. Subsequent commits require a refreshed checkpoint bound to the new source SHA. |

### Static validator output at the audited SHA

`CONTROL_PLANE_REGISTRY_VALIDATION=PASS` (six layers, 21 static files, four design authority roots); `FEATURE_MATRIX_VALIDATION=PASS` (15/20/22); `PHASE_TASK_STATE_MACHINE_VALIDATION=PASS` (10 phases, 45 tasks, N001–N010); `UNIFIED_CONTRACT_VALIDATION=PASS` (15/20/79/22/45/11); `CONTROL_PLANE_SYNC=PASS` (616 paths, zero errors); Project Master State and Work Execution State validation passed; final `CONTROL_PLANE_ADMISSION=PASS` in `PRODUCT_WORK` mode. These results prove admission at `81aefb1` only and do not prove product/runtime acceptance.

## Control decisions and open gates

- Keep `CONTROL_PLANE_REGISTRY.yaml` as the single Work entry point. This report and the M1 matrix are evidence artifacts, not a second control plane.
- Do not change canonical static task definitions on the product branch. The retired-runner binding is an explicit blocker pending the proper authority/synchronization path.
- Keep the latest PR comment’s V3.1 timing rule as the M1 numeric/event authority. Do not reinstate the earlier V1.1 duration gate or infer model timeout from UI minimums.
- Preserve the V3.1/V1.2 Resolve conflict as `REVIEW_REQUIRED`; do not make the visual delay hold a real result.
- No release inference is made from the skipped Runtime job or successful Build job. `M1_RELEASE_PASS` remains unavailable until every applicable exact-SHA gate and N009 approval pass.

## Next actions

| Order | Owner | Action | Gate |
|---|---|---|---|
| 1 | Work | Reconcile `P5-02` runner binding through the authorized existing governance path; preserve canonical `main` until approved. | L3 / L5 |
| 2 | Track B + Work | Integrate the 65-row M1 Design Authority Matrix and verify page/state/feature/journey/component/test bidirectional links against canonical sources. | L2 / L4 |
| 3 | Track A + Track C | Land V3.1 event-gated source and tests; record Resolve conflict without inventing a timing decision. | L4 / C3 |
| 4 | Work + Track C | Run the hosted API28 Pixel 2 preflight, then exact-SHA Runtime gates using the Build Once APK pair. | L5 |
| 5 | Track C | Classify every base case and E2E as PASS/FAIL/BLOCKED/NOT_RUN with source/image provenance; do not treat catalog validation as execution. | L5 |
| 6 | Work | Refresh private checkpoint at each integrated SHA and repeat Admission/recovery evidence after any source-state change. | L1 / L6 |


## Post-audit integration checkpoint

The matrix now contains 49 page/state and 16 shared-component records. It is a branch-scoped evidence matrix based on canonical source registries plus the approved PR #162 guide proposal; it does not imply that PR #162 is effective on main. The matrix records J18 owner disagreement, missing/blocked original authorities, and 48 rows requiring review. Direct access to the deployed Design Manager page was not available in this audit, so that part is BLOCKED_ACCESS.

C1's exact-path manifest, scope validator and negative guard tests are included in the candidate change. The exact diff validation has not yet run on a published SHA. The live 81a Page Contract validator failed with 658 expected versus 669 active asset entries; neither registry nor validator was changed. P5-02 continues to name the retired self-hosted runner; its canonical Control Plane binding requires the approved sync path.

The next integration must refresh the private Work checkpoint to its actual remote source SHA, rerun Project Master, Work and full Admission validation there, and keep the raw state untracked. The hosted runner configuration is not considered a successful environment until its Preflight Run ID records actual KVM, API28, Pixel 2, 1080x1920, APK install, CameraX, instrumentation and artifact upload results.


## N010 conflict

The static task machine's N010 completion still requires P01/P02/P03 PNGs. The approved M1 guide decision is two pages. This is a real L3/L4 authority conflict; the product branch did not edit the static registry or weaken the N010 checks. The existing design proposal and admission/synchronization path must produce an approved successor before C01 can close.

## Live-head Admission retest and integration static checks

At `2026-10-18`, the required command was rerun from the product worktree at the live PR #163 source `81aefb1c0eae08df5ea6f451fc7c65be89a9c191`, then refreshed and repeated at `1d98f48d59132d3d1ea9e226d0e32342459eb002`. At both sources, private state was restored with matching checkpoint hashes; at 1d98f48, `mode=PRODUCT_WORK`, `CONTROL_PLANE_SYNC=PASS` (616 paths, zero errors), Project Master State PASS, Work Execution State PASS, and `CONTROL_PLANE_ADMISSION=PASS` (exit 0). The same-source interruption/recovery rehearsal also passed. The next source-changing commit requires refreshed state and repeat Admission/recovery.

Local integration checks also passed for the declared path whitelist (55 changed paths, zero unexpected/protected paths), workflow YAML and embedded Bash syntax, Runtime Harness contract tests, Camera Runtime tests, V3.1 timing contract, catalog structure (66 base / 10 E2E), evidence source closure, authority matrix shape and V3.1 finalizer. Full command output and the initial YAML and transient cache findings are summarized in `M1_LOCAL_REPAIR_EVIDENCE.md`. None of these local checks is a GitHub Actions Run or an API28 Runtime result. The Page Contract validator remains FAIL (658 expected / 669 active entries).

## Exact-source recovery and CI retest at `6d16bb7`

At `6d16bb7d2c98f0cc2bcf55da3c3eff08984a179e`, the required private Project Master and Work states were refreshed to the current source SHA in a private checkpoint. The local PRODUCT_WORK Admission run passed L1/L2/L3/L4/L6 static checks, Project Master, Work, and Control Plane sync (616 paths, zero errors). The interruption rehearsal removed and restored both private state files from the persisted checkpoint; their SHA-256 hashes matched and a second full Admission passed. GitHub Control Plane Admission Run 39093186750 also passed on this SHA. Raw state remains private.

L5 remains blocked: Hosted Runtime Run 39093186784 failed at SDK license-pipe exit handling before Preflight, and Android CI Run 39093186726 failed AndroidTest compilation after passing unit tests. This exact-SHA audit is not a Release PASS. The next source commit needs refreshed private state and repeated Admission/recovery.


## Latest exact-SHA checkpoint

This refresh supersedes the prior “latest exact-head” row for the current candidate while preserving historical failures. Live PR #163 is ffe285aff3818435815f769532fa221d3157917e; canonical main is 0d3238e8a41b87363e9a17b04c9cc45fca4c426d.

| Layer | Current result | Exact evidence / remaining action |
|---|---|---|
| L1 · Project/Work State | PASS | Required PRODUCT_WORK Admission ran in the actual M1 worktree at ffe285aff3818435815f769532fa221d3157917e after private state refresh. Private state checkpoint version 7 is bound to this SHA. No raw Work state was committed. |
| L2 · Feature Matrix | REVIEW_REQUIRED | 15 features, J01–J20, routes/child surfaces and the 65-row design matrix are present, but J18 Feature Matrix/Unified Contract owner references disagree; missing/review/blocked sources remain. Resolve the mapping through canonical authority. |
| L3 · Phase/Task Machine | REVIEW_REQUIRED | Static validators pass; P5-02 still names the expired self-hosted API28 runner and N010 still expects three guide pages. Retry budget for the prior runner repair loop is exhausted; keep the hosted KVM blocker explicit. |
| L4 · Unified Contract/Design | REVIEW_REQUIRED | C01 target is the approved two-page proposal in Draft PR #162, but main and PR #163 product code remain three-page. The Design Manager web route could not be retrieved by the browsing surface; GitHub canonical main files remain inspectable. V3.1 is the latest M1 timing authority per comment #6244627483. |
| L5 · Evidence Gate | BLOCKED_INFRA | Hosted Run 39093844697 Build Once PASS; API28 Preflight failed KVM access and formal Runtime skipped. Android CI 39093846259 passed. No API28 native capture, trace, bounds, ROI diff or MP4. |
| L6 · Automatic Recovery | REVIEW_REQUIRED | Private checkpoint exists at the exact SHA; the current-SHA interruption/recovery rehearsal result is not yet verified. The successful recovery at 6d16bb7 remains historical and is not reused as an ffe result. |

The true branch-local Admission PASS is separate from GitHub run 39093844689, which validates canonical promotion rules only. The current overall audit remains REVIEW_REQUIRED/BLOCKED_INFRA; no layer is promoted from a neighboring layer's result.


## Live branch audit checkpoint — exact tested source 85dfe92

| Layer | Result on source 85dfe9217e879e0f87d19b41801beb05e502aaa2 | Evidence / action |
|---|---|---|
| L1 · Project/Work State | REVIEW_REQUIRED | The private checkpoint and true PRODUCT_WORK Admission pass are from parent source ffe285a. The GitHub Admission Run 39095194580 validates canonical promotion only. Refresh private state and rerun on this SHA. |
| L2 · Feature Matrix | REVIEW_REQUIRED | 15/20/22/79 static inventories pass; J18 owner references conflict and some M1 surfaces/assets are unresolved. |
| L3 · Phase/Task Machine | REVIEW_REQUIRED | P5-02 still names the expired self-hosted API28 pool; N010 still requires three pages; retries are bounded. |
| L4 · Unified Contract/Design | REVIEW_REQUIRED | C01 remains a Draft proposal in #162, not integrated/effective on main; Design Manager was not retrievable through the web surface; V3.1 timing authority is the current M1 rule. |
| L5 · Evidence Gate | BLOCKED_INFRA | Run 39095194693 Build Once passed; KVM Preflight failed; API28 runtime skipped. Android CI 39095194583 passed. |
| L6 · Automatic Recovery | REVIEW_REQUIRED | No current-SHA recovery rehearsal; private checkpoint remains bound to parent ffe285a. |

The new CI/workflow commit adds the 669-entry Page Contract validator to Android CI and logs exact APK hashes. These are not reported as passed until that exact SHA completes.


## Live-head six-layer audit — b97f01f6790cea2e2f1cb4bfef07a38086223020

| Layer | Result | Evidence and next action |
|---|---|---|
| L1 · Project/Work State | REVIEW_REQUIRED | Private checkpoint is bound to `ffe285aff3818435815f769532fa221d3157917e`, not current `b97f01f6790cea2e2f1cb4bfef07a38086223020`. PRODUCT_WORK Admission and recovery rehearsal are NOT_RUN. GitHub Admission Run 39095681147 is canonical-promotion only. Refresh securely and run the real validator at the live source SHA. |
| L2 · Feature Matrix | REVIEW_REQUIRED | Static inventory counts (15/20/22/79) pass. Frozen J17–J20 acceptance semantics conflict with Feature Matrix/Unified Contract; see Gap M1-P0-019. |
| L3 · Phase/Task State Machine | REVIEW_REQUIRED | Canonical P5-02 still requires the expired self-hosted API28 runner. Retry budget remains bounded; update the existing canonical task only through the governed PR and preserve N009. |
| L4 · Unified Contract/Design Authority | REVIEW_REQUIRED | C01 two-page guide remains a Draft proposal in PR #162, not active on main or #163. Live Design Manager could not be retrieved. V3.1 State_Machine is the M1 timing authority; PROC-07 timeout is unapproved. |
| L5 · Runtime Evidence | BLOCKED_INFRA | At exact `b97f01f6790cea2e2f1cb4bfef07a38086223020`, Build Once passed in Run 39095681151, but KVM preflight failed. API28 emulator preflight and formal Runtime were skipped; Android CI Run 39095681153 passed. |
| L6 · Recovery | REVIEW_REQUIRED | No current-SHA private checkpoint refresh/recovery rehearsal is available. Do not reuse the historical ffe Admission/recovery result. |

The repository-local static Page Contract V3 validator now passes on this SHA (669 registry entries). Overall six-layer disposition remains `REVIEW_REQUIRED / BLOCKED_INFRA`; no layer borrows another layer's PASS.

