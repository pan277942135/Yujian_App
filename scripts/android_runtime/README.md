# Unified Android Runtime Harness

`scripts/run_android_runtime_gate.sh` is the single runtime entry point for
Recognition Frozen, Data Sanitization, and Runtime Parity.

The workflow builds the debug APK and androidTest APK exactly once, uploads
them as `YuJian-runtime-apks-${GITHUB_SHA}` with a SHA-256 manifest, and the
runtime matrix verifies and installs that exact pair directly with `adb`.
Runtime jobs never invoke Gradle.

## Classification contract

| Exit | Classification | Meaning |
| ---: | --- | --- |
| 0 | `PASS` | Preflight, install, instrumentation, and evidence passed |
| 10 | `BLOCKED_INFRA` | ADB/emulator/transport/readiness/storage failure |
| 20 | `FAIL_ARTIFACT` | APK missing, invalid, or package installation contract failed |
| 30 | `FAIL_TEST` | Instrumentation completed with a product/test assertion failure |
| 40 | `FAIL_EVIDENCE` | Instrumentation passed but the required evidence contract failed |

Every run writes `runtime_gate_result.json`, `instrumentation.log`, and
diagnostics under the selected evidence directory. ADB can be restarted once
for transport recovery, instrumentation is never retried, and APK installation
has at most one transport-only retry.

The fake-ADB contract suite is runnable without an emulator:

```bash
bash scripts/android_runtime/tests/test_contract.sh
```


## Normal Home V1

`normal-home-v1` is a required API 28 gate. It launches the production Normal Home with deterministic persisted guest FishRecords, captures 16:9 / 19.5:9 / 20:9 / 21:9 states, compares the canonical 1080×1920 frame with `normal_home_v1.png`, and records the real idle-motion path.
