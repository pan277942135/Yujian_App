#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$YUJIAN_RUNTIME_EVIDENCE/infra"
python3 scripts/design/capture_nh01_device_metadata.py --output "$YUJIAN_RUNTIME_EVIDENCE/infra/device_metadata.json" --insets-dump "$YUJIAN_RUNTIME_EVIDENCE/infra/window_insets_dump.txt"

export ADB="$(command -v adb)"
runtime_rc=0
bash scripts/run_android_runtime_gate.sh --gate gemini-home-v2 --api-level 28 --build-sha "$YUJIAN_SOURCE_SHA" --app-apk build/runtime-apks/app-debug.apk --test-apk build/runtime-apks/app-debug-androidTest.apk --evidence-dir "$YUJIAN_RUNTIME_EVIDENCE" || runtime_rc=$?

# Keep the NH01 adaptive matrix independent from the existing Gemini runtime
# suite: a failure in that suite must not suppress the requested 3×3 evidence.
matrix_rc=0
YUJIAN_ADB_BIN="$ADB" \
  YUJIAN_APP_PACKAGE="${YUJIAN_APP_PACKAGE:-com.yujian.ai.uiv2}" \
  YUJIAN_INSTRUMENTATION_TARGET="${YUJIAN_INSTRUMENTATION_TARGET:-com.yujian.ai.uiv2.test/androidx.test.runner.AndroidJUnitRunner}" \
  YUJIAN_APP_APK="build/runtime-apks/app-debug.apk" \
  YUJIAN_TEST_APK="build/runtime-apks/app-debug-androidTest.apk" \
  YUJIAN_AAPT_BIN="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/aapt" \
  YUJIAN_EVIDENCE_DIR="$YUJIAN_RUNTIME_EVIDENCE" \
  BUILD_SHA="$YUJIAN_SOURCE_SHA" \
  GITHUB_RUN_ID="${GITHUB_RUN_ID:-local}" \
  GITHUB_SERVER_URL="${GITHUB_SERVER_URL:-https://github.com}" \
  GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-pan277942135/Yujian_App}" \
  bash scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh || matrix_rc=$?

if (( matrix_rc != 0 )); then
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=FAIL exit=%s\n' "$matrix_rc" >&2
  matrix_classification="$(python3 - "$YUJIAN_RUNTIME_EVIDENCE" "$runtime_rc" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
legacy_rc = int(sys.argv[2])
summary_path = root / "NH01_ADAPTIVE_MATRIX_SUMMARY_V1.json"
try:
    summary = json.loads(summary_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    summary = {}
cases = summary.get("cases", [])
records = []
for case in cases:
    for key in ("candidate_execution", "baseline_execution"):
        record = case.get(key)
        if isinstance(record, dict):
            records.append(record)
failures = [record for record in records if str(record.get("status", "")).upper() == "FAIL"]
if failures:
    test_failure = any(
        "INSTRUMENTATION_EXIT_" in str(record.get("reason", ""))
        or "TEST_ASSERTION_FAILED" in str(record.get("reason", ""))
        or "failure_class=" in str(record.get("reason", ""))
        for record in failures
    )
    classification = "FAIL_TEST" if test_failure else "FAIL_EVIDENCE"
elif any(str(record.get("status", "")).upper() in {"MISSING", "RUNNING"} for record in records):
    classification = "BLOCKED_INFRA"
else:
    classification = "FAIL_EVIDENCE"
failure_reasons = sorted({
    str(record.get("reason")) for record in failures if record.get("reason")
})
result_path = root / "runtime_gate_result.json"
try:
    payload = json.loads(result_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    payload = {}
payload["nh01_adaptive_matrix"] = {
    "status": summary.get("status", "FAIL"),
    "classification": classification,
    "source_sha": summary.get("source_sha"),
    "failure_reasons": failure_reasons,
}
if legacy_rc == 0:
    payload.update({
        "evidence": "FAIL",
        "classification": classification,
        "failure_phase": "INSTRUMENTATION" if classification == "FAIL_TEST" else (
            "PREFLIGHT" if classification == "BLOCKED_INFRA" else "EVIDENCE"
        ),
        "failure_reason": "NH01_ADAPTIVE_API28_3X3_MATRIX_" + classification,
    })
result_path.parent.mkdir(parents=True, exist_ok=True)
result_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(classification)
PY
)"
  printf 'NH01_ADAPTIVE_MATRIX_CLASSIFICATION=%s\n' "${matrix_classification:-UNKNOWN}" >&2
  if (( runtime_rc == 0 )); then
    case "$matrix_classification" in
      FAIL_TEST) runtime_rc=30 ;;
      BLOCKED_INFRA) runtime_rc=10 ;;
      *) runtime_rc=40 ;;
    esac
  fi
else
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=%s\n' "$YUJIAN_SOURCE_SHA"
fi

exit "$runtime_rc"
