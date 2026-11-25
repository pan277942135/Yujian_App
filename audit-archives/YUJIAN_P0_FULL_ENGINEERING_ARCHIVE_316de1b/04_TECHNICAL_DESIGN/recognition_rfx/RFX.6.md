# RFX.6 — Live optical-fidelity measurement and V3.1 evidence

## Scope

The recognition evidence contract follows the frozen V3.1 state sequence:
CAPTURED, DETECTING, OUTLINE, CLASSIFYING, and RESULT_READY. Capture no longer
depends on an obsolete Resolve fade or paused-motion assertion. The production
result still hands off as soon as the real result and minimum CLASSIFYING beat
are ready.

`RecognitionOpticalFidelityRuntimeTest` runs inside the existing required
API28 `recognition-frozen` gate. It leaves the production
`RecognitionProcessingScene` frame loop live in CLASSIFYING, samples
Choreographer intervals in FULL and LITE, and captures a representative frame
from each profile. Its report records refresh rate, interval summaries, raw
samples, and live motion-clock sample counts. The quality gate validates the
measurements and their manifest; it adds no performance PASS threshold because
the frozen contract defines none.

## Verification approach

- `scripts/verify_recognition_runtime_evidence_v3_1.py` validates the V3.1
  production-flow sequence, minimum beats, shared visual clock, degradation
  mapping, Level A geometry, screenshot parity, and evidence hashes.
- `scripts/verify_recognition_optical_fidelity_evidence.py` validates FULL/LITE
  captures, sample counts, computed summaries, and the evidence manifest.
- `scripts/android_runtime/tests/test_contract.sh` exercises the harness
  contract, including fake-ADB scenarios for evidence validation.
- `scripts/verify_recognition_runtime_contract.py`,
  `scripts/verify_final_android_matrix.py`, shell syntax checks, and
  `git diff --check` cover the associated static contracts.
- Exact-head API28 outcomes, device logs, screenshots, and APK provenance are
  maintained in the private N001 acceptance record. This public note records
  the reproducible verification method and does not claim a runtime PASS.

## Independent check

A repository Page Contract verifier still has an unrelated Asset Registry
count discrepancy. RFX.6 changes no design or asset registry files; that check
remains independently classified and is not reported as passing here.

## Frozen behavior

Detector, classifier, source image, result semantics, V3.1 state minimums, and
human visual-review status remain unchanged. `UNREVIEWED` visual QA findings
remain unreviewed until a human review is recorded.
