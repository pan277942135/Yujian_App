# RFX.6 — Live optical-fidelity measurement and V3.1 evidence

## Scope

The recognition evidence contract now follows the frozen V3.1 state sequence:
CAPTURED, DETECTING, OUTLINE, CLASSIFYING, and RESULT_READY. The obsolete
200 ms Resolve fade capture and its paused-motion assertions are removed. The
production result still hands off as soon as the real result and minimum
CLASSIFYING beat are ready.

`RecognitionOpticalFidelityRuntimeTest` runs inside the existing required
API28 `recognition-frozen` gate. It leaves the production `RecognitionProcessingScene`
frame loop live in CLASSIFYING, measures 120 Choreographer frame intervals in
FULL and 120 in LITE, and captures one frame from each profile. The report
includes refresh rate, mean, median, p95, maximum, intervals over 32 ms, raw
intervals, and live motion-clock sample counts. The quality gate validates the
measurements and their manifest; it records no performance PASS threshold
because none is defined by the frozen contract.

## Verification

- `scripts/verify_recognition_runtime_evidence_v3_1.py` validates the V3.1
  production-flow sequence, minimum beats, shared visual clock, degradation
  mapping, Level A geometry, frozen screenshot parity, and evidence hashes.
- `scripts/verify_recognition_optical_fidelity_evidence.py` validates both
  real-profile captures, raw interval counts, computed summaries, and hashes.
- `scripts/android_runtime/tests/test_contract.sh`: PASS, including the
  synthetic fake-ADB route for the updated evidence validators.
- `scripts/verify_recognition_runtime_contract.py`: PASS.
- `scripts/verify_final_android_matrix.py`: PASS.
- `git diff --check` and shell syntax checks: PASS.
- First exact-head Android CI attempt (`38940090476`, `a325102b1de38b72dd56fc3118a1ea4257c34266`)
  failed in Kotlin compilation: `RecognitionAmbientField.kt` was missing the
  `LaunchedEffect` import. The build stopped before API28 jobs and produced no
  APK. The import is added in the follow-up fix; API28 instrumentation, actual
  frame metrics, and APK evidence remain pending CI on that exact new SHA.
- Second exact-head Android CI attempt (`38940752726`, `8eac2ef2b4da6c281d51779e22718f9e5d4e2887`)
  passed unit tests, Camera harness contract tests, and debug APK assembly, then
  failed AndroidTest compilation because the test imported `Choreographer` from
  the wrong Android package and omitted Compose test API imports. No API28 jobs
  or APK artifacts ran. Those imports are corrected in the next commit; API28
  instrumentation, actual frame metrics, and APK evidence remain pending.
- Third exact-head Android CI attempt (`38941379127`, `cb6137248130cdc9f6486b326c0fec03aefb73eb`)
  built successfully and reached the API28 Recognition gate, but instrumentation
  hit the 180-second suite timeout as test 50 began. The test now captures the
  rendered device surface through UiAutomation and uses non-idling semantic
  checks. The frozen suite's 49 earlier tests already consume nearly the default
  timeout, so Recognition alone now declares a 300-second instrumentation limit;
  other gates retain the 180-second default.
- Fourth exact-head Android CI attempt (`38943615023`, `ad7abe3c395337d55aafdbfc35710c8cb764b223`)
  confirmed the same 180-second cutoff immediately after the optical-fidelity
  test began. This run produced no optical metrics and is not a PASS. The
  Recognition-only timeout increase and evidence update are committed for the
  next exact-head run.
- Fifth exact-head Android CI run (`38946381172`,
  `c4721529f0f8b3d90ce51bf54722049c3d7d450d`): build, unit tests, harness
  contract tests, APK assembly, AndroidTest APK compilation, packaged asset
  checks, model trace, and lint all PASS. The API28 `recognition-frozen` gate
  reached the optical-fidelity test as item 50/50 but ended
  `FAIL_TEST / INSTRUMENTATION_TIMEOUT` at 300 seconds, so no optical metrics
  or screenshots were emitted. Logcat confirms the production scene reached
  CLASSIFYING and then repeatedly reported the Compose-Espresso idling resource
  as busy. The test's semantics query was synchronizing against that
  intentionally live animation. The harness now uses ActivityScenario and
  production phase/motion callbacks instead of Compose idling/semantics queries;
  this correction remains unverified until the next API28 run. No PASS is
  claimed for this run.

## Independent check retained

The local Page Contract verifier still reports an Asset Registry count mismatch
(expected 658, observed 669). RFX.6 changes no design or asset registry files;
this unrelated repository check remains nonpassing and is not promoted to PASS.

## Frozen behavior

Detector, classifier, source image, result semantics, V3.1 state minimums, and
human visual-review status remain unchanged. `UNREVIEWED` visual QA findings
remain unreviewed until a human review is recorded.
