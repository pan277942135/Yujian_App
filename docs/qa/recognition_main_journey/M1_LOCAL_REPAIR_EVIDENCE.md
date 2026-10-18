# M1 Local Repair Evidence

**Tasks:** `M1_SIX_LAYER_COMPLIANCE_AUDIT_V1` + `M1_QUALITY_ENFORCEMENT_V1_1`  
**Parent:** PR #163, still Draft/Open/Unmerged.  
**Candidate worktree parent:** `81aefb1c0eae08df5ea6f451fc7c65be89a9c191`  
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

These are local checks on a dirty worktree whose Git HEAD remains `81aefb1`. The next published SHA still requires its own CI and hosted API28 execution. The Page Contract validator failure (658 expected / 669 active asset entries) remains an independent unresolved finding and was not changed by this repair.
