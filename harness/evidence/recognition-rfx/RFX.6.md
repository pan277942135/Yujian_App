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
- API28 instrumentation, actual frame metrics, and Android build: pending CI
  on the exact RFX.6 code SHA.

## Independent check retained

The local Page Contract verifier still reports an Asset Registry count mismatch
(expected 658, observed 669). RFX.6 changes no design or asset registry files;
this unrelated repository check remains nonpassing and is not promoted to PASS.

## Frozen behavior

Detector, classifier, source image, result semantics, V3.1 state minimums, and
human visual-review status remain unchanged. `UNREVIEWED` visual QA findings
remain unreviewed until a human review is recorded.
