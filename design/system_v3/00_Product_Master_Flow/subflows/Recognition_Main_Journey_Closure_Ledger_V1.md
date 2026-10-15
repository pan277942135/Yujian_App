# Recognition Main Journey Closure Ledger V1

**Repository:** `pan277942135/Yujian_App`  
**Work line:** Draft PR [#145](https://github.com/pan277942135/Yujian_App/pull/145)  
**Branch:** `audit/design-system-v3-1-phase1-master-baseline`  
**Audit baseline:** `32c2f2654225b76a3bcf5b94d41bceef238ba3cc`  
**Base:** `main@7737f293ea3831aeea2c6f58308961cdb2ba7d01`  
**Status:** Stage 0 audit recorded; implementation and gates remain in progress.

This ledger tracks the authorized end-to-end closure of the Empty Home → Capture → Recognition → Result → durable FishRecord journey. It is subordinate to the existing V3.1 Product Master Flow and page contracts. It does not change existing Frozen PNGs, model artifacts, classifier/detector thresholds, CameraX ownership, or Processing motion contracts.

## Baseline verification

| Check | Result | Evidence |
|---|---|---|
| Remote branch head | `32c2f2654225b76a3bcf5b94d41bceef238ba3cc` | Git ref and PR #145 head agree; clean shallow checkout matches. |
| PR #145 | Open · Draft · Unmerged | PR metadata read at audit start. |
| Main head | `7737f293ea3831aeea2c6f58308961cdb2ba7d01` | Git ref and PR base agree. |
| Android CI build job | PASS | Run [38714545550](https://github.com/pan277942135/Yujian_App/actions/runs/38714545550): unit tests, Camera harness contract tests, APK assemble, AndroidTest APK compile, packaged asset checks, model trace export, lint, and APK artifact uploads completed. |
| Android runtime job in that run | SKIPPED | Run `38714545550`; no Recognition journey runtime result is implied. |
| API 28 Normal Home targeted validation | FAIL / BLOCKED | `NORMAL_HOME_RUNTIME_PARITY=BLOCKED`, `hero=BLOCKED`; the gate enforcement step exited nonzero. This is a separate existing Normal Home fixture issue, not a Recognition journey pass. |
| Frozen runtime APK baseline | Available, not final for this task | Artifacts are tied to baseline SHA `32c2f265...`; they must not be reported as the final journey APK. |

## Source audit findings

The source audit read the current Kotlin routes and existing V3.1 Capture, Processing, and Result contracts at the exact baseline above.

| Area | Verified baseline behavior | Closure work |
|---|---|---|
| Capture entry | Home opens the existing `identify` route; the route uses the existing CameraX surface and the platform photo picker. Camera permission is requested by the current Capture flow. | Preserve CameraX and picker ownership; align permission state and readiness gates with the new business contract. |
| First-use guidance | No `capture_guide_v1_completed` marker or first-capture overlay exists in the current Android source. | Add one-time, non-blocking Capture overlay and installation-scoped completion state. |
| Processing | `RecognizingScreen` runs recognition in a composition-scoped coroutine keyed by `imageId`; Back pops the route. | Verify cancellation and stale callback behavior with an attempt-scoped guard and runtime tests. |
| Result retry | `RecognitionResultScreen` labels the action “重新拍摄”; the current `YujianApp.kt` handler clears result fields and navigates to `recognizing` while retaining `sessionImage`. | P0 defect: make “重新拍摄” return to Capture and require a new image/attempt. |
| Result Back | The result route currently uses `popBackStack()`, which normally exposes the existing Identify/Capture route. | Preserve this expected destination and protect substantive unsaved edits. |
| Issue recovery | No Fish / Image Quality and Technical Failure share `recognition_issue`; recovery actions return to Identify or launch the picker. | Keep frozen RR04/RR05 authority; verify failure-state distinction, picker cancellation, and clean navigation stack. |
| Result editing | Species and metadata/story fields are local `remember` state. No dirty-draft confirmation is present. | Track substantive edits, handle system Back and top-bar Back, preserve drafts over configuration recreation, and defer Back to active editor/selector first. |
| Save Catch | UI prevents concurrent submissions and generates `clientRecordId` with `rememberSaveable`. Authenticated save posts that key and validates a reread by catch ID. | Add uncertain-outcome lookup/retry coverage and exact once-only tests. |
| Guest save | `GuestCatchRepository.saveCatch` creates a new random guest record ID and uses asynchronous preference `apply()`; it does not use the draft's idempotency key. | P0 closure: stable guest key, duplicate prevention, and synchronous durable acknowledgement before success navigation. |
| Save failure | The result remains visible for non-401 errors; 401 currently logs out to Home. | Preserve recoverable draft across identity recovery and distinguish pending/unknown server outcome from definite failure. |
| Save navigation | Save Catch returns to Home. Continue Memory currently pops only through `result`, potentially leaving the prior Identify route below Detail. | Clear the recognition stack for both successful destinations; verify Back from Detail. |
| Session/attempt identity | `SelectedImage.imageId` scopes image work and `clientRecordId` scopes a result save; there is no explicit user-session ID. | Add or derive a session/attempt guard sufficient to prevent stale results and repeated saves; document ID ownership. |

## Stage ledger

| Stage | Scope | Status | Required evidence before closure |
|---|---|---|---|
| S0 | Baseline and gap audit | IN PROGRESS | This ledger pushed; source and runtime baselines recorded. |
| S1 | Business contract, route matrix, J01–J20, editable 4K flow graphic, Design Manager and authority links | NOT STARTED | JSON/asset validation, frozen asset SHA stability, remote commit and governance checks. |
| S2 | Missing first-capture, dirty-draft, permission/provider and recovery high fidelity | NOT STARTED | Reuse-vs-new matrix, source assets, responsive/accessibility states, manifest and reviewable PNG/SVG. |
| S3 | Capture guidance, permissions, retry, cancellation and navigation | NOT STARTED | Kotlin changes and targeted JVM/Android tests on exact pushed SHA. |
| S4 | Result draft protection, recovery, save idempotency and clean destinations | NOT STARTED | Kotlin changes, repository tests, no-record negative checks, one-record positive checks. |
| S5 | J01–J20 automated journey coverage | NOT STARTED | Test results for each case with exact commit provenance. |
| S6 | API 28 runtime and visual acceptance | NOT STARTED | Exact-HEAD install/run evidence, screenshots/logs, explicit PASS/FAIL/BLOCKED classification. |
| S7 | Final APK and test report | NOT STARTED | APK built from final verified HEAD, SHA-256, size, install test, and linked report/artifact. |

## Gate snapshot

| Gate | Initial disposition |
|---|---|
| `BUSINESS_JOURNEY_FROZEN` | NOT_STARTED |
| `RECOGNITION_HIFI_READY` | NOT_STARTED |
| `CAPTURE_NAVIGATION_READY` | FAIL — retry currently re-enters recognition with the retained image |
| `RESULT_RECOVERY_SAVE_READY` | FAIL — no dirty-draft confirmation; guest idempotency/durability gap; memory destination may retain Capture in back stack |
| `JOURNEY_AUTOMATION_GATE` | NOT_RUN for J01–J20 |
| `JOURNEY_RUNTIME_GATE` | NOT_RUN for Recognition journey |
| `RECOGNITION_APK_READY` | NOT_READY — baseline APK exists but predates this task's changes |

## Execution rules

- Before each write, recheck branch head; commit only as a fast-forward against the observed head.
- Push one reviewable commit per completed stage; keep PR #145 Draft/Open/Unmerged and do not merge `main`.
- Preserve previous frozen page/image authority and record hashes for every new design asset.
- Use test fixtures for deterministic journey logic; do not use fabricated model output as live recognition evidence.
- No production database, GCS, backend, or deployment writes are in scope.
- Mark unrun checks `NOT_RUN`; distinguish product failure from infrastructure blockage; do not infer runtime success from build or test compilation.
