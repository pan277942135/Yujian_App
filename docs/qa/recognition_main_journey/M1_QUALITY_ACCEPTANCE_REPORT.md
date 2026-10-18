# M1 Quality Acceptance Report

**Task:** `M1_QUALITY_ENFORCEMENT_V1_1`  
**Parent Work:** `YUJIAN_M1_PARALLEL_DELIVERY_V1`  
**Audited source SHA:** `81aefb1c0eae08df5ea6f451fc7c65be89a9c191`  
**Report status:** `REVIEW_REQUIRED` — checks are made explicit; source and Runtime evidence are still being integrated.

This report applies C1–C7 from the current PR #163 task comment as acceptance locks. It does not create another control plane and does not turn static validation into implementation, Runtime, independent acceptance, or release status.

| Lock | Required acceptance | Current evidence | Status |
|---|---|---|---|
| C1 · Scope and protected inputs | Every PR declares owned file paths, Feature/Page/State scope, and reviewed dependencies. Frozen asset hashes, model/backend/API contracts, and shared tokens remain unchanged unless explicitly authorized. | Track A is isolated to Android implementation/tests and reports no model/API/backend or Frozen source edits. Track B preserves Frozen PNG bytes. C1 scope manifest, exact-path validator and negative guard tests are included in the candidate worktree; local diff validation passes for 55 changed paths with zero unexpected/protected paths. Exact-SHA CI execution remains pending. | `REVIEW_REQUIRED` |
| C2 · UI fidelity | Resolve each page via Design Manager → effective authority → canonical original PNG/SVG → Page Contract → shared foundation. Compare exact-source native screenshots using geometry, glyph and named ROI criteria; no thumbnail or full-screen-only shortcut. | Track B’s in-progress matrix records source paths and hashes; Figma binary import and native visual Runtime are `NOT_RUN`. Page Contract validator fails on 658 expected versus 669 active registry entries. | `REVIEW_REQUIRED` |
| C3 · Motion fidelity | Enforce V3.1 minima of 220/320/350/250ms and actual Pipeline event gating. Use real detector bounds, one clock, real traces, PNG/MP4/frame samples and degradation evidence. A visual minimum is not a model timeout. | Track A commit `82c49b8d7ced6568ee2d04e3ecbd1c10a06cc018` uses V3.1 minima and real event handoff. Its contract check passes. No exact-SHA Motion Trace/MP4 exists. The V1.2 Resolve 200ms presentation and V3.1 immediate late-result handoff remain a recorded conflict. | `REVIEW_REQUIRED` |
| C4 · Business flow and recovery | Cover Entry, Trigger, Input, Action, Next, Back, Cancel, Error and Recovery, including multitap/race, recreation, persistence and routing. Same saved record must remain coherent across FishRecord A/B. Production cannot rely on phase overrides, fake success or skipped Pipeline. | Baseline contains camera, save, detail and error tests; Track A added IME-aware editors, keyboard Back/focus behavior and source-level save/loading coverage. Track C added the V3.1/guide and saved-detail runtime contracts. No integrated exact-SHA end-to-end run exists. | `NOT_RUN` |
| C5 · Four acceptance gates | A Authority; B implementation; C Runtime; D independent acceptance. Manual naturalness/optical feel remains `REVIEW_REQUIRED`. | Gate A is incomplete for capture, detail A, technical failure and B-side memory authorities. Gate B has code/static checks only. Gate C has no hosted API28 run. Gate D has no independent reviewer result. | `REVIEW_REQUIRED` |
| C6 · Failure evidence and repair loop | Preserve failure → RCA → minimal correction → impacted regression → new source SHA rerun → quantified before/after → gap update. Never weaken an assertion or skip a gate. | The initial branch Admission failure and first local workflow YAML parse failure are preserved. Admission and post-repair workflow/static checks pass at baseline `81aefb1`; the Page Contract count failure remains. No Frozen registry or checker threshold was changed. Exact-SHA CI/Runtime rerun is pending. | `IN_PROGRESS` |
| C7 · Release eligibility | No P0/P1 gaps; all applicable Authority, implementation, Runtime, visual, motion, journey, and APK provenance gates pass on one exact SHA; N009 human authorization is recorded. | Build-only/Skipped Runtime run `39086281726` is not eligible. No current M1 APK or APK SHA-256 exists; no API28 preflight/run ID exists; N009 approval has not been requested or granted. | `BLOCKED_INFRA` |

## Quality gate state

| Gate | Current status | Evidence required to close |
|---|---|---|
| Gate A · Design authority | `REVIEW_REQUIRED` | Integrated `M1_DESIGN_AUTHORITY_MATRIX`, complete effective authority paths and canonical asset SHA, resolved source-count validator issue, reviewed C01 two-page branch authority. |
| Gate B · Implementation | `REVIEW_REQUIRED` | Integrated Android/Design/QA commits; build, JVM and AndroidTest compile on the same source SHA; scope and frozen-source diff report. |
| Gate C · Runtime | `BLOCKED_INFRA` | Hosted Ubuntu 24.04/API28 x86_64 Pixel 2 preflight; successful install of both APKs; real CameraX/Instrumentation; native screenshots, bounds, Pixel Diff, V3.1 Pipeline timing trace and Motion MP4. |
| Gate D · Independent acceptance | `NOT_RUN` | Independent review of exact-SHA journey and visual/motion/logic evidence; optical feel remains a human review item. |
| Release gate | `BLOCKED_INFRA` | All applicable P0/P1 closed, one exact-source APK SHA and Run ID, all gate results, and N009 human release authorization. |

## Non-negotiable handling

- A successful Build, source validator, or catalog validator is not a Runtime or Release PASS.
- A skipped Runtime job remains `NOT_RUN`; it is never promoted from a build result.
- Every Android screenshot, APK, log, Pixel Diff, bounds record, Motion Trace and video must name the same source SHA and build/run identity.
- Test image candidates without source SHA and rights evidence remain unapproved. Their dependent E2E cases must be `BLOCKED_ASSET_SOURCE` or `NOT_RUN`, never silently replaced.
- Keep PR #163 Draft/Open/unmerged. No release claim is made by this report.


## Candidate integration update

The C1 scope manifest is now present and names files, Feature/Page/State scope, invariants, tests and evidence for Android, QA, Design Authority evidence and hosted runner work. A validator checks the complete PR diff and rejects undeclared files plus Frozen design-image, registry, model and Control Plane changes. Its negative unit tests cover protected Frozen rasters and control-plane/model paths. Local and remote exact-source execution are not yet recorded.

The M1 Design Authority Matrix has 65 rows (49 page/state and 16 component). It records raw asset hashes where sources exist, and distinguishes missing, review and blocked integrity rows. Geometry, glyph, ROI pixel, motion, accessibility and full journey acceptance remain NOT_RUN without a native exact-SHA API28 run. The Page Contract count validator failure is retained.

C1: local scope/diff checks PASS; exact-SHA CI on a published candidate is NOT_RUN. C2: REVIEW_REQUIRED with source and deployed Design Manager gaps. C3: source/test contract checks pass on the integrated local candidate, but real trace/MP4 is NOT_RUN. C4: save/detail runtime test added but not compiled or run. C5: Gate A/D incomplete; Gate B is pending exact-SHA Build and tests; Gate C is BLOCKED_INFRA. C6: initial Admission and YAML failures plus Page Contract validator failure are preserved. C7: BLOCKED; no APK Run ID or N009 release approval exists.


## Control Plane authority conflict

N010 in the canonical task machine still requires the historical three-page guide. The approved M1 two-page decision is documented in C01 and PR #162, but static Control Plane authority was not edited from the product branch. Gate A remains REVIEW_REQUIRED until the authorized successor contract and sync are approved.

## Exact-source failure handling update

At source `1d98f48d59132d3d1ea9e226d0e32342459eb002`, scope enforcement passed in Hosted Runtime Run 39092555676. The job then failed because `sdkmanager` was not resolved; API28 jobs were skipped. Android CI Run 39092555645 ran 211 unit tests and failed one timing assertion. Both failures are retained, and minimal local corrections are staged for a new source SHA. No repair is counted as passing until that SHA reruns CI; no Runtime, visual, motion, APK or Release PASS is inferred.
