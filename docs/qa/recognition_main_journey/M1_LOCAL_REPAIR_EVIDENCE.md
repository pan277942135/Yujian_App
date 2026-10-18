# M1 Local Repair Evidence

**Tasks:** `M1_SIX_LAYER_COMPLIANCE_AUDIT_V1` + `M1_QUALITY_ENFORCEMENT_V1_1`
**Parent:** PR #163, still Draft/Open/Unmerged.
**Published failure source:** `1d98f48d59132d3d1ea9e226d0e32342459eb002`
**Current local repair base:** `1d98f48d59132d3d1ea9e226d0e32342459eb002`
**Classification:** local static validation only; not GitHub CI, emulator, Runtime, APK, or Release evidence.

## Preserved failure and repair

During the hosted-runner workflow migration, YAML parsing first failed in `.github/workflows/android.yml`:

```text
yaml.scanner.ScannerError: mapping values are not allowed here
  in ".github/workflows/android.yml", line 355, column 11
```

The inserted action fragment contained literal `\n` text before a step and malformed GitHub expression delimiters in its evidence path. No assertion was removed and no CI result was claimed. The fragment was corrected in the candidate worktree.

## Revalidation after repair

The following checks ran against the same local candidate worktree:

- PyYAML BaseLoader parsed `.github/workflows/android.yml`, `.github/workflows/camera-runtime-harness.yml`, and `.github/workflows/m1-hosted-api28-runtime.yml`.
- `bash -n` passed for every workflow `run` block and each `android-emulator-runner` script block after replacing GitHub expressions with shell-safe placeholders.
- `python3 scripts/test_validate_m1_scope.py`: 3 tests passed.
- The first local scope scan also correctly rejected 15 Python `__pycache__` outputs produced by the preceding tests (70 paths total). These generated files were removed rather than whitelisted. The repeat scan passed with 55 declared paths, zero unexpected paths and zero protected paths.
- `python3 scripts/validate_m1_scope.py --base-sha 0d3238e8a41b87363e9a17b04c9cc45fca4c426d`: PASS on the clean candidate tree, 55 changed paths, zero unexpected paths, zero protected paths.
- Harness contract, Camera Runtime gate, V3.1 timing contract, 66/10 catalog structure, source-closure tests, authority map and V3.1 finalizer tests passed.
- `git diff --check` passed after trimming a trailing blank line in `camera-runtime-harness.yml`.

The static checks above were run before publishing `1d98f48`. At that exact remote SHA, Hosted Runtime run 39092555676 and Android CI run 39092555645 then exposed the failures recorded below. The present local worktree is based on `1d98f48`; repairs remain uncommitted and require new exact-SHA CI and hosted API28 execution. The Page Contract validator failure (658 expected / 669 active asset entries) remains an independent unresolved finding and was not changed by this repair.

## Exact-SHA CI failure and repair

At source `1d98f48d59132d3d1ea9e226d0e32342459eb002`, the live GitHub Actions evidence is:

- Hosted Runtime Run [39092555676](https://github.com/pan277942135/Yujian_App/actions/runs/39092555676): `ubuntu-24.04` runner scheduled; scope step passed. Build Once failed at “Resolve Android SDK build tools” because `sdkmanager` was not found on PATH. API28 Preflight, API28 Runtime, and the fallback classification job were skipped. Artifact list is empty; no APK exists.
- Android CI Run [39092555645](https://github.com/pan277942135/Yujian_App/actions/runs/39092555645): 211 unit tests ran; one test failed: `RecognitionVisualStateControllerTest.noTimerOnlyProgressWithoutDetectionFocusClassificationOrResult`. Later build/APK and Runtime jobs were skipped. No artifact was produced.

The SDK workflow steps now resolve `sdkmanager` from `ANDROID_HOME`/`ANDROID_SDK_ROOT` command-line tools if it is not on PATH. The test now starts CAPTURED at 10,000ms, verifies it at 10,219ms, and delivers the real DETECTING event at 10,219ms; it verifies the phase transition at 10,220ms. No assertion or gate was removed or lowered. These are local, unpublished repairs, not PASS evidence. A new source SHA and full applicable workflow rerun are required.
