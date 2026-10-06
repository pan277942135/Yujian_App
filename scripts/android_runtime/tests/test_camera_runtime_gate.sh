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
EXIT_PASS=0
EXIT_BLOCKED_INFRA=10
FAIL_PHASE=""
FAIL_REASON=""
runtime_set_failure() {
  FAIL_PHASE="$1"
  FAIL_REASON="$2"
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
  "logcat -c") exit 0 ;;
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
printf "camera runtime gate permission handling: PASS\n"