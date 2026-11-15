# PR331 × Code Local Unified QA Delivery Report

Task: `YUJIAN_PR331_CODE_LOCAL_UNIFIED_QA_INTEGRATION_V1`

Status: **Draft QA integration ready for remote review; Main was not modified or merged.**

## Source and preservation

| Item | SHA / branch | Result |
|---|---|---|
| PR #331 source | `2e11dea1761296cdd19625bfc45a6c47be1376d9` / `fix/orb-r06-render-visibility-p0` | Preserved unchanged |
| Code Local source | `59e5c7b22c07ca512008b8376367126cf32df24f` / `wp1b/recognition-pipeline-visual-handoff-v1` | Preserved unchanged |
| Actual common ancestor | `59e5c7b22c07ca512008b8376367126cf32df24f` | Verified; differs from the task's expected merge-base, recorded as evidence |
| Code Local snapshot | `c303acf5160ea37735490c0fdb4863f0449f8f1f` / `snapshot/code-local-20261115` | Created before integration |
| Normal merge commit | `40977c9774e76c7023f3823bb7714e678211be24` | `--no-ff` merge; only `harness/build-log.md` conflicted |
| Implementation/build HEAD | `27b51c5ab1e6bb3d5f1d462623e6747a2d97e118` | Exact source used for the build |
| QA report commit | `94bf0ad` | Documentation-only commit after the implementation build |

The complete file-level decision record is in [`CODE_LOCAL_VS_PR331_MERGE_MANIFEST.md`](CODE_LOCAL_VS_PR331_MERGE_MANIFEST.md). No global ours/theirs merge was used. Dynamic state, worktrees and Python caches are excluded from commits.

## Post-merge derived-source repair

`app/src/main/java/com/yujian/ai/ui/waterlight/renderer/OrbRuntimeProfileV1.kt` contained generated provenance constants that did not match the checked-in R02–R06 JSON resources. Commit `27b51c5ab1e6bb3d5f1d462623e6747a2d97e118` updates only those seven generated SHA constants and records the repair in the merge manifest. A subsequent exact-HEAD CI run exposed the Windows-CRLF/Linux-LF portability edge; the generator now reads the canonical Git Blob only when Index and Worktree are clean and falls back to Worktree bytes for real edits. The profile was regenerated from canonical Blob bytes, and `python scripts/android_runtime/generate_orb_runtime_profile.py --check` passes on both platforms. No timeline, geometry, model ID, mask identity or renderer behavior was changed.

## Local exact-head verification

All commands below used source HEAD `27b51c5ab1e6bb3d5f1d462623e6747a2d97e118` in an ASCII-path detached build worktree to avoid a Windows Gradle path limitation.

| Check | Result | Evidence |
|---|---|---|
| Control-plane registry, feature matrix, phase/task state machine, unified contract | PASS | Canonical `origin/main` registry; 15 features, 20 journeys, 22 routes, 80 tasks, 40 gates |
| Project Master State | PASS | `scripts/validate_project_master_state.py` |
| Work Execution State | PASS | `scripts/validate_work_execution_state.py` |
| Control-plane sync | PASS | `scripts/validate_control_plane_sync.py --base origin/main --no-fetch`; 753 paths, 0 errors |
| Orb profile generated-source check | PASS | `scripts/android_runtime/generate_orb_runtime_profile.py --check` with clean-tree Git-Blob provenance |
| JVM unit tests | PASS | `:app:testDebugUnitTest`; 426 tests |
| Debug APK | PASS | `:app:assembleDebug` |
| AndroidTest APK compilation | PASS | `:app:assembleDebugAndroidTest` |
| Debug lint | PASS | `:app:lintDebug` |
| Release Kotlin compilation | PASS | `:app:compileReleaseKotlin` |
| Packaged model/assets | PASS | `verify_production_model.py`, capture presentation, packaged APK and Orb runtime checks |
| Static SWOS contract | PASS | Contract/static geometry checks; Android and dynamic demo intentionally not run |

The first full admission invocation completed canonical registry/static/sync checks but returned `WORK_EXECUTION_STATE_VALIDATION=FAIL` because the branch-local checkpoint still pointed at the pre-repair merge commit. The state was corrected to the exact final HEAD and the two branch-local validators then passed. No validator or required rule was weakened.

## APK provenance

Artifact (kept outside Git history to avoid adding a 144 MB binary to the QA PR):

`artifacts/YuJian_Unified_PR331_CodeLocal_QA_Debug.apk`

- Source SHA: `27b51c5ab1e6bb3d5f1d462623e6747a2d97e118`
- Size: `144,398,266` bytes
- SHA-256: `b46509b585f6a547b6b3d14579879043bbf60791e003cc9f2b7f7a06ff07f818`
- Classifier: `MODEL_CROP_M1_v0.2`; SHA-256 `29c2f2fd1608178f023b8e35edf632c0dd685df4a7ae1c3562220a7352ccce18`
- Detector: `DET_FISH_v0.1`; SHA-256 `12b97f7c081987f33f99d255cdd2e935fb9cf93b893146f54ff98b9c4e3a8e4f`
- Capture button packaged pixel SHA-256: `501abcc58a65263dd879ad198a0a033630ab4bdcaf945eb02599e4f1249ca327`
- Orb Runtime Core WebP SHA-256: `daa85a1fc13fac4a7ca562959c2d19172ee0f1e94277c3eeb32ef1f09a1a7b09`

No DET_FISH v0.3 was introduced or claimed.

## Open gates and limitations

1. Product-owner physical-device visual acceptance is **PENDING**. No ADB, emulator or device run was performed by this Work.
2. Physical-evidence verifiers requiring a real device/evidence directory were not promoted to PASS.
3. The R01B frozen-bundle verifier cannot run to completion because the repository checkout does not contain the external `PACKAGE_MANIFEST.json`/`SHA256SUMS.txt` frozen bundle directory; the checked-in runtime asset verifier still reports the known CRLF-vs-raw-blob mismatch when run against the worktree bytes. This was not “fixed” by changing frozen assets or expected hashes.
4. The camera contract shell harness negative-path scenarios pass. Its synthetic ready/pass case stops at `RR09_NATIVE_FRAME_EVIDENCE_INVALID` because the Windows test environment lacks the required `ffprobe` executable; this is an environment/evidence limitation, not a product PASS.
5. B009 remains unverified. The APK uses only the authorized/pinned `DET_FISH_v0.1` path.

## Delivery state

- Integration branch: `qa/pr331-code-local-unified-20261115`
- Intended PR title: `QA: PR331 + Code Local Unified Integration`
- Intended base: `work/recognition-runtime-result-handoff-v1`
- Draft PR: to be created after this report commit and branch push
- Main: **not merged, not modified**
- PR #331: **not modified, not merged**

This report deliberately does not claim full release readiness or device visual acceptance; it records the exact evidence and the remaining gates for product-owner review.
