# 拍照识别主流程 · 标准测试用户 V1

**Owner**: Recognition QA · **Status**: Git-tracked baseline (fixture schema v1.0 / dataset v1.1.0), not a real account or runtime acceptance certificate.

## Unique pinned identity

- \`QA_RECOGNITION_001\` is a **logical, local Android guest test identity**, not a created cloud account, registered email or credential-bearing login.
- Source-of-truth manifest: \`app/src/androidTest/assets/qa/recognition/v1/test_users.json\`. The **same checked-in bytes** are read by CI validation and Android instrumentation.
- Four **states of the same logical test user**: \`FIRST_USE\`, \`GUIDE_PENDING\`, \`RETURNING\`, \`PERMISSION_DENIED\`.
- Initial guest fish record count **0** for all four; the test starts from Empty Home. No Normal Home F01–F08 or FishRecordDetail/B-side requirement.
- Scenario ↔ test-case references remain **candidate mappings** while the 66-case main-journey test plan is reviewed. Do not mark an unmapped or unexecuted case as PASS.

## What GitHub persists and what it does not

Git tracks the stable test user ID, state variants, expected app-private keys, version, reset behavior, provenance and scenario ID mapping. On demand, the fixture **resets and applies local state in a debug Android test app**, and emits a SHA-bound seed receipt. Git does **not** contain a reusable live logged-in session, Android permission grant, user password, backend user ID, image rights grant, emulator snapshot, or a claimed AI recognition result.

Identity of the real account, if a future login path needs one, must be created in an isolated QA backend and secrets placed in GitHub Actions Secrets or another dedicated secret provider. Never create an account in production merely because the fixture ID exists.

## Exact state mappings

| State | Guide completed | Host camera permission | Expected page | Purpose |
|---|---|---|---|---|
| FIRST_USE | false | revoke/reset | Empty Home | Initial permission and guide |
| GUIDE_PENDING | false | grant | Empty Home | Guide begins only once CameraX is ready |
| RETURNING | true | grant | Empty Home | Subsequent camera entry |
| PERMISSION_DENIED | false | revoke/reset | Empty Home | Permission denial and recovery |

Permission is **not** seeded by app code. Set it using the device's test harness, e.g. test environment \`adb shell pm grant/revoke <debug-package> android.permission.CAMERA\` when supported and the application is in a compatible installation/permission state. Verify the actual on-device grant state separately before declaring a permission test PASS.

## Explicitly seed on debug instrumentation (example)

The seed tests are opt-in and skipped in normal all-tests suites. Pick exactly **one** method and run it on a disposable test emulator where the debug app and AndroidTest APK are already installed:

\`\`\`bash
adb shell am instrument -w -r \
  -e qa_seed_enabled true \
  -e class com.yujian.ai.RecognitionQaUserFixtureSeedTest#seedFirstUseGuest \
  com.yujian.ai.uiv2.test/androidx.test.runner.AndroidJUnitRunner
\`\`\`

Replace \`seedFirstUseGuest\` with \`seedGuidePendingGuest\`, \`seedReturningGuest\`, or \`seedPermissionDeniedGuest\` for other states.

**This mutates only \`yujian_guest_archive\`, app-private \`guest_catches\`, and \`yujian_capture_guide_v1\`.** It refuses non-debuggable APKs. Do not invoke concurrently with live user interactions or other AndroidTest suites. Clean-room CI typically starts with an app-data reset before the seed; the narrow seed alone does not clear unrelated auth preferences or backend sessions.

Test script should record \`recognition-qa-user-seed.json\` from the debug target app's cache and attach its \`manifest_sha256\`, dataset version, state ID, exact source SHA, build APK SHA, and required runtime screenshot/test artifacts. Receipt \`runtime_acceptance=NOT_RUN\` is intentional.

## Validate updates

\`\`\`bash
python3 scripts/validate_recognition_qa_test_users.py
python3 -m unittest discover -s tests -p 'test_recognition_qa_test_users.py' -v
\`\`\`

Every dataset change is reviewed in PR, updates \`dataset_version\` and \`CHANGELOG.md\`, and passes the isolated \`Recognition QA Test Users\` CI. Keep identity ID stable across compatible versions; additions to state variants are MINOR; behavior-breaking changes are MAJOR with explicit migration note. Golden images/photos, model outputs and real CameraX screenshots belong in separate, SHA-indexed packages. No changing stored camera/media bytes to “make a test green.”

## Boundaries and existing integration

The fixture reuses the real GuestCatchRepository preference keys and first-use guide persistence key. Do not modify the existing on-device CameraX / Detector / Classifier behavior or Frozen V3.1 design. The fixture does not replace the real API28 camera/runtime acceptance gate and does not certify the E2E happy path.

## V1.1 linked test-case library

- [66-case review catalog](test_case_catalog_v1.json)
- [10 composed E2E scenarios](e2e_scenarios_v1.json)
- [29 input/host specs](test_inputs_v1.json)
- [Readable review and unresolved decisions](TEST_CASE_REVIEW_V1.md)
- Check cross-references with `python3 scripts/validate_recognition_test_case_catalog.py`; every base case and E2E has one primary initial state and is listed exactly once in `states[].test_case_ids`. Source-case `NOT_RUN` is not runtime acceptance.
