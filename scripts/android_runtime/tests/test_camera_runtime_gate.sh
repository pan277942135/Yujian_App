#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
GATE="$ROOT_DIR/scripts/android_runtime/gates/camera_runtime_v1.sh"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

export YUJIAN_EVIDENCE_DIR="$TEMP_DIR/evidence"
export YUJIAN_APP_PACKAGE="com.yujian.ai"
export YUJIAN_ADB_BIN="$TEMP_DIR/adb"
export FAKE_GRANT_EXIT=0
export FAKE_GRANT_OUTPUT=""
export YUJIAN_FAILURE_PHASE=""
export YUJIAN_FAILURE_REASON=""
export BUILD_SHA="camera-gate-test"
export YUJIAN_APP_APK="$TEMP_DIR/app-debug.apk"
EXIT_PASS=0
EXIT_BLOCKED_INFRA=10
EXIT_FAIL_EVIDENCE=40
EXIT_BLOCKED_ASSET_SOURCE=50
FAIL_PHASE=""
FAIL_REASON=""
runtime_set_failure() {
  FAIL_PHASE="$1"
  FAIL_REASON="$2"
  YUJIAN_FAILURE_PHASE="$1"
  YUJIAN_FAILURE_REASON="$2"
}

cat > "$YUJIAN_ADB_BIN" <<'ADB'
#!/usr/bin/env bash
set -u
command="$*"
case "$command" in
  "shell am force-stop com.yujian.ai") exit 0 ;;
  "shell pm clear com.yujian.ai") printf "Success\n"; exit 0 ;;
  "shell pm grant com.yujian.ai android.permission.CAMERA")
    printf "%s" "$FAKE_GRANT_OUTPUT"
    exit "$FAKE_GRANT_EXIT"
    ;;
  "exec-out run-as com.yujian.ai cat cache/camera-runtime-v1/P01_runtime.png"|\
  "exec-out run-as com.yujian.ai cat cache/camera-runtime-v1/P02_runtime.png")
    printf 'invalid png fixture'
    ;;
  "exec-out run-as com.yujian.ai cat cache/camera-runtime-v1/first_use_camera_guide_runtime_report.json")
    printf '{ invalid json fixture'
    ;;
  "exec-out run-as com.yujian.ai cat "*) exit 0 ;;
  "logcat -c") exit 0 ;;
  "logcat -d -v threadtime") exit 0 ;;
  "shell dumpsys media.camera") exit 0 ;;
  "shell dumpsys camera") exit 0 ;;
  *) printf "unexpected fake adb command: %s\n" "$command" >&2; exit 90 ;;
esac
ADB
chmod +x "$YUJIAN_ADB_BIN"

source "$GATE"

gate_before_instrumentation
pass_rc=$?
if (( pass_rc != EXIT_PASS )); then
  printf "expected empty-output pm grant with exit 0 to pass; got rc=%s phase=%s reason=%s\n" \
    "$pass_rc" "$FAIL_PHASE" "$FAIL_REASON" >&2
  exit 1
fi
grep -Fx "CAMERA_PERMISSION_GRANT_EXIT_CODE=0" "$YUJIAN_EVIDENCE_DIR/camera_runtime_v1/permission_preflight.txt" >/dev/null

export FAKE_GRANT_EXIT=7
export FAKE_GRANT_OUTPUT="Permission grant failed"
FAIL_PHASE=""
FAIL_REASON=""
gate_before_instrumentation
failure_rc=$?
if (( failure_rc != EXIT_BLOCKED_INFRA )); then
  printf "expected nonzero pm grant to block; got rc=%s phase=%s reason=%s\n" \
    "$failure_rc" "$FAIL_PHASE" "$FAIL_REASON" >&2
  exit 1
fi
[[ "$FAIL_PHASE" == "CAMERA_PERMISSION_SETUP" ]]
[[ "$FAIL_REASON" == "APP_RESET_OR_CAMERA_PERMISSION_GRANT_FAILED" ]]
grep -Fx "CAMERA_PERMISSION_GRANT_EXIT_CODE=7" "$YUJIAN_EVIDENCE_DIR/camera_runtime_v1/permission_preflight.txt" >/dev/null

# The post-instrumentation hook runs before the runner classifies the test
# result. Partial visual files must not overwrite a real test assertion.
YUJIAN_FAILURE_PHASE="INSTRUMENTATION"
YUJIAN_FAILURE_REASON="TEST_ASSERTION_FAILED"
FAIL_PHASE="$YUJIAN_FAILURE_PHASE"
FAIL_REASON="$YUJIAN_FAILURE_REASON"
gate_after_instrumentation
[[ "$YUJIAN_FAILURE_PHASE" == "INSTRUMENTATION" ]]
[[ "$YUJIAN_FAILURE_REASON" == "TEST_ASSERTION_FAILED" ]]
[[ "$FAIL_PHASE" == "INSTRUMENTATION" ]]
[[ "$FAIL_REASON" == "TEST_ASSERTION_FAILED" ]]

fixture="$YUJIAN_EVIDENCE_DIR/camera_runtime_v1"
mkdir -p "$fixture/first_use_camera_guide" "$fixture/component"
printf 'BACK_CAMERA_COUNT=1\nCAMERA_SERVICE_RESPONSIVE=true\n' > "$fixture/camera_preflight.txt"
for name in \
  01_camera_open.png 02_preview_streaming.png 03_capture_ready.png \
  04_capture_pressed.png 05_post_capture.png 06_recognition_handoff.png \
  P01_runtime.png P02_runtime.png \
  first_use_camera_guide_runtime_report.json camera_capture_trace.txt \
  camera_runtime_log.txt instrumentation_result.txt; do
  printf 'fixture\n' > "$fixture/$name"
done
for name in P01_overlay.png P02_overlay.png \
  P01_diff.png P02_diff.png first_use_camera_guide_runtime_report.json; do
  printf 'fixture\n' > "$fixture/first_use_camera_guide/$name"
done
for name in P01_compose_component.png P02_compose_component.png \
  first_use_camera_guide_runtime_report.json; do
  printf 'fixture\n' > "$fixture/component/$name"
done
printf '{"visual_parity":"BLOCKED_ASSET_SOURCE","pass":false}\n' \
  > "$fixture/first_use_camera_guide/first_use_camera_guide_runtime_report.json"
FAIL_PHASE=""
FAIL_REASON=""
gate_collect_evidence
blocked_rc=$?
if (( blocked_rc != EXIT_BLOCKED_ASSET_SOURCE )); then
  printf "expected missing source photos to block visual gate; got rc=%s phase=%s reason=%s\n" \
    "$blocked_rc" "$FAIL_PHASE" "$FAIL_REASON" >&2
  exit 1
fi
[[ "$FAIL_PHASE" == "ASSET_SOURCE" ]]
[[ "$FAIL_REASON" == "ASSET_SOURCE_BLOCKED" ]]

printf '{"visual_parity":"PASS","pass":true}\n' \
  > "$fixture/first_use_camera_guide/first_use_camera_guide_runtime_report.json"
gate_collect_evidence
pass_evidence_rc=$?
if (( pass_evidence_rc != EXIT_PASS )); then
  printf "expected explicit visual pass report to pass evidence collection; got rc=%s\n" \
    "$pass_evidence_rc" >&2
  exit 1
fi
printf "camera runtime gate permission and visual evidence checks: PASS\n"
