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
  YUJIAN_EVIDENCE_DIR="$YUJIAN_RUNTIME_EVIDENCE" \
  BUILD_SHA="$YUJIAN_SOURCE_SHA" \
  GITHUB_RUN_ID="${GITHUB_RUN_ID:-local}" \
  GITHUB_SERVER_URL="${GITHUB_SERVER_URL:-https://github.com}" \
  GITHUB_REPOSITORY="${GITHUB_REPOSITORY:-pan277942135/Yujian_App}" \
  bash scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh || matrix_rc=$?

if (( matrix_rc != 0 )); then
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=FAIL exit=%s\n' "$matrix_rc" >&2
  if (( runtime_rc == 0 )); then
    python3 - "$YUJIAN_RUNTIME_EVIDENCE/runtime_gate_result.json" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
payload = json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}
payload.update({
    "evidence": "FAIL",
    "classification": "FAIL_EVIDENCE",
    "failure_phase": "EVIDENCE",
    "failure_reason": "NH01_ADAPTIVE_API28_3X3_MATRIX_FAILED",
})
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
    runtime_rc=40
  fi
else
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=%s\n' "$YUJIAN_SOURCE_SHA"
fi

exit "$runtime_rc"
