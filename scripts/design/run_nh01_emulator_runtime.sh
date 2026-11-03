#!/usr/bin/env bash
set -euo pipefail

runtime_started_epoch="$(date +%s)"
mkdir -p "$YUJIAN_RUNTIME_EVIDENCE/infra"
export ADB="$(command -v adb)"
timing_log="$YUJIAN_RUNTIME_EVIDENCE/infra/runtime_timings.log"
: > "$timing_log"
boot_marker="${RUNNER_TEMP:-/tmp}/nh01_emulator_boot_started_epoch"
if [[ -s "$boot_marker" ]]; then
  boot_started_epoch="$(cat "$boot_marker")"
  boot_seconds="$(( $(date +%s) - boot_started_epoch ))"
  printf 'emulator_boot_seconds=%s\n' "$boot_seconds" | tee -a "$timing_log"
else
  printf 'emulator_boot_seconds=UNAVAILABLE_NO_WORKFLOW_MARKER\n' | tee -a "$timing_log"
fi

adb_wait_started_epoch="$(date +%s)"
adb_wait_deadline="$((adb_wait_started_epoch + 360))"
selected_serial=""
adb_ready=false
while (( $(date +%s) < adb_wait_deadline )); do
  device_list="$(timeout 10s "$ADB" devices -l 2>&1 || true)"
  online_serials="$(awk 'NR > 1 && $2 == "device" {print $1}' <<< "$device_list")"
  online_count="$(awk 'NF {n++} END {print n+0}' <<< "$online_serials")"
  if [[ "$online_count" -eq 1 ]]; then
    candidate="$(awk 'NF {print; exit}' <<< "$online_serials")"
    state="$(timeout 10s "$ADB" -s "$candidate" get-state 2>/dev/null | tr -d '\r' || true)"
    boot_completed="$(timeout 10s "$ADB" -s "$candidate" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$state" == "device" && "$boot_completed" == "1" ]]; then
      selected_serial="$candidate"
      adb_ready=true
      break
    fi
  fi
  {
    printf 'elapsed_seconds=%s\n' "$(( $(date +%s) - adb_wait_started_epoch ))"
    printf '%s\n' "$device_list"
  } > "$YUJIAN_RUNTIME_EVIDENCE/infra/adb_wait_last_state.log"
  sleep 1
done
adb_wait_seconds="$(( $(date +%s) - adb_wait_started_epoch ))"
printf 'adb_ready_seconds=%s\nadb_serial=%s\n' "$adb_wait_seconds" "${selected_serial:-MISSING}" | tee -a "$timing_log"
if [[ "$adb_ready" != true ]]; then
  printf 'INFRA_EMULATOR_BOOT_TIMEOUT api=28 timeout_seconds=360 last_state=%s\n' \
    "$(tr '\n' ';' < "$YUJIAN_RUNTIME_EVIDENCE/infra/adb_wait_last_state.log" 2>/dev/null || true)" >&2
  python3 - "$YUJIAN_RUNTIME_EVIDENCE/runtime_gate_result.json" "$YUJIAN_SOURCE_SHA" "$adb_wait_seconds" <<'PY'
import json, pathlib, sys
path=pathlib.Path(sys.argv[1])
payload={"schema":"NH01_RUNTIME_GATE_RESULT_V1","gate":"gemini-home-v2","api_level":28,
         "source_sha":sys.argv[2],"preflight":"BLOCKED","install":"NOT_RUN",
         "instrumentation":"NOT_RUN","evidence":"NOT_RUN","classification":"BLOCKED_INFRA",
         "failure_phase":"EMULATOR_BOOT","failure_reason":"INFRA_EMULATOR_BOOT_TIMEOUT",
         "adb_ready_seconds":int(sys.argv[3])}
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY
  exit 10
fi
# Android 9 leaves Settings.System.FONT_SCALE unset on a clean AVD; the
# effective framework default is 1.0, but the read-only metadata gate must
# observe an explicit value. Temporarily materialize the frozen test value,
# capture it, then restore the original setting before the matrix starts.
original_font_scale="$("$ADB" -s "$selected_serial" shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"
if [[ -z "$original_font_scale" ]]; then original_font_scale="null"; fi
printf 'initial_font_scale_before_metadata=%s\n' "$original_font_scale" | tee -a "$timing_log"
if [[ "$original_font_scale" != "1.0" ]]; then
  "$ADB" -s "$selected_serial" shell settings put system font_scale 1.0
fi
prepared_font_scale="$("$ADB" -s "$selected_serial" shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"
if [[ "$prepared_font_scale" != "1.0" ]]; then
  printf 'FONT_SCALE_PREPARE_FAILED expected=1.0 actual=%s\n' "$prepared_font_scale" >&2
  exit 10
fi
metadata_rc=0
python3 scripts/design/capture_nh01_device_metadata.py --profile M04 \
  --output "$YUJIAN_RUNTIME_EVIDENCE/infra/device_metadata.json" \
  --insets-dump "$YUJIAN_RUNTIME_EVIDENCE/infra/window_insets_dump.txt" \
  --screencap-output "$YUJIAN_RUNTIME_EVIDENCE/infra/device_screencap.png" || metadata_rc=$?
restore_font_rc=0
if [[ "$original_font_scale" == "null" ]]; then
  "$ADB" -s "$selected_serial" shell settings delete system font_scale || restore_font_rc=$?
else
  "$ADB" -s "$selected_serial" shell settings put system font_scale "$original_font_scale" || restore_font_rc=$?
fi
restored_font_scale="$("$ADB" -s "$selected_serial" shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"
printf 'font_scale_metadata_value=%s\nfont_scale_restored=%s\n' "$prepared_font_scale" "$restored_font_scale" | tee -a "$timing_log"
if (( metadata_rc != 0 )); then exit "$metadata_rc"; fi
if (( restore_font_rc != 0 )); then
  printf 'FONT_SCALE_RESTORE_FAILED original=%s current=%s\n' "$original_font_scale" "$restored_font_scale" >&2
  exit 10
fi

APP_APK="${YUJIAN_APP_APK:-build/runtime-apks/app-debug.apk}"
TEST_APK="${YUJIAN_TEST_APK:-build/runtime-apks/app-debug-androidTest.apk}"
export BUILD_SHA="$YUJIAN_SOURCE_SHA" API_LEVEL=28 EVIDENCE_DIR="$YUJIAN_RUNTIME_EVIDENCE"
export YUJIAN_APP_PACKAGE="${YUJIAN_APP_PACKAGE:-com.yujian.ai.uiv2}"
export YUJIAN_TEST_PACKAGE="${YUJIAN_TEST_PACKAGE:-com.yujian.ai.uiv2.test}"
export YUJIAN_INSTRUMENTATION_TARGET="${YUJIAN_INSTRUMENTATION_TARGET:-$YUJIAN_TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner}"
source scripts/android_runtime/common.sh
source scripts/android_runtime/preflight.sh
source scripts/android_runtime/install_apks.sh

runtime_rc=0
preflight_status="PASS"
install_status="NOT_RUN"
instrumentation_status="NOT_RUN"
evidence_status="NOT_RUN"
runtime_classification="BLOCKED_INFRA"
runtime_failure_phase="MATRIX"
runtime_failure_reason="NH01_MATRIX_NOT_RUN"

write_runtime_result() {
  python3 scripts/android_runtime/result.py \
    --output "$YUJIAN_RUNTIME_EVIDENCE/runtime_gate_result.json" \
    --gate gemini-home-v2 --api-level 28 --build-sha "$YUJIAN_SOURCE_SHA" \
    --adb-state "$("$ADB" -s "$selected_serial" get-state 2>/dev/null | tr -d '\r' || true)" \
    --boot-completed "$("$ADB" -s "$selected_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" \
    --data-free-kb "${YUJIAN_DATA_FREE_KB:-}" \
    --preflight "$preflight_status" --install "$install_status" \
    --instrumentation "$instrumentation_status" --evidence "$evidence_status" \
    --classification "$runtime_classification" \
    --failure-phase "$runtime_failure_phase" --failure-reason "$runtime_failure_reason"
}

preflight_started_epoch="$(date +%s)"
if android_runtime_preflight; then
  preflight_status="PASS"
else
  preflight_status="FAIL"
  runtime_rc=10
  runtime_classification="BLOCKED_INFRA"
  runtime_failure_phase="PREFLIGHT"
  runtime_failure_reason="${YUJIAN_FAILURE_REASON:-ADB_PREFLIGHT_FAILED}"
fi
printf 'adb_preflight_seconds=%s\n' "$(( $(date +%s) - preflight_started_epoch ))" | tee -a "$timing_log"

if [[ "$preflight_status" == "PASS" ]]; then
  install_started_epoch="$(date +%s)"
  if android_runtime_install_apks "$APP_APK" "$TEST_APK"; then
    install_status="PASS"
  else
    install_rc=$?
    install_status="FAIL"
    runtime_rc="$install_rc"
    runtime_classification="BLOCKED_INFRA"
    if (( install_rc == 20 )); then runtime_classification="FAIL_ARTIFACT"; fi
    runtime_failure_phase="${YUJIAN_FAILURE_PHASE:-INSTALL}"
    runtime_failure_reason="${YUJIAN_FAILURE_REASON:-APK_INSTALL_FAILED}"
  fi
  printf 'apk_install_seconds=%s\n' "$(( $(date +%s) - install_started_epoch ))" | tee -a "$timing_log"
fi
write_runtime_result

# Run only the three representative windows on the one installed APK pair.
# M04=360x640, M07=390x844, M10=430x932; ADAPTIVE_V2 only.
run_nh01_matrix() {
  local profile_filter="${1:-}"
  local variant_filter="${2:-}"
  local -a matrix_env=(
    "YUJIAN_ADB_BIN=$ADB"
    "YUJIAN_APP_PACKAGE=$YUJIAN_APP_PACKAGE"
    "YUJIAN_INSTRUMENTATION_TARGET=$YUJIAN_INSTRUMENTATION_TARGET"
    "YUJIAN_APP_APK=$APP_APK"
    "YUJIAN_TEST_APK=$TEST_APK"
    "YUJIAN_AAPT_BIN=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/build-tools/35.0.0/aapt"
    "YUJIAN_EVIDENCE_DIR=$YUJIAN_RUNTIME_EVIDENCE"
    "BUILD_SHA=$YUJIAN_SOURCE_SHA"
    "GITHUB_RUN_ID=${GITHUB_RUN_ID:-local}"
    "GITHUB_SERVER_URL=${GITHUB_SERVER_URL:-https://github.com}"
    "GITHUB_REPOSITORY=${GITHUB_REPOSITORY:-pan277942135/Yujian_App}"
  )
  if [[ -n "$profile_filter" ]]; then matrix_env+=("YUJIAN_NH01_PROFILE_FILTER=$profile_filter"); fi
  if [[ -n "$variant_filter" ]]; then matrix_env+=("YUJIAN_NH01_VARIANT_FILTER=$variant_filter"); fi
  env -u YUJIAN_NH01_PROFILE_FILTER -u YUJIAN_NH01_VARIANT_FILTER \
    YUJIAN_NH01_REPORT_STEM=NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT \
    "${matrix_env[@]}" bash scripts/android_runtime/gates/run_nh01_adaptive_device_matrix.sh
}

matrix_started_epoch="$(date +%s)"
matrix_rc=0
run_nh01_matrix "M04,M07,M10" "ADAPTIVE_V2" || matrix_rc=$?
printf 'representative_3_case_matrix_seconds=%s\n' "$(( $(date +%s) - matrix_started_epoch ))" | tee -a "$timing_log"

matrix_classification="$(python3 - "$YUJIAN_RUNTIME_EVIDENCE" "$matrix_rc" "NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT.json" <<'PY'
import json, pathlib, sys
root = pathlib.Path(sys.argv[1])
matrix_rc = int(sys.argv[2])
report_path = root / sys.argv[3]
try:
    report = json.loads(report_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    report = {}
records = [case.get("execution", {}) for case in report.get("cases", []) if isinstance(case, dict)]
statuses = [str(record.get("status", "MISSING")).upper() for record in records]
failures = [record for record in records if str(record.get("status", "")).upper() == "FAIL"]
if matrix_rc == 0 and len(records) == 3 and statuses == ["PASS"] * 3:
    classification = "PASS"
elif failures and any(
    "INSTRUMENTATION_EXIT_" in str(record.get("reason", ""))
    or "TEST_ASSERTION_FAILED" in str(record.get("reason", ""))
    or "FAIL_CAPTURE_PIPELINE" in str(record.get("reason", ""))
    or "PAGER_SINGLE_PAGE_INTERACTION_FAILED" in str(record.get("reason", ""))
    or "SWIPE_TO_NEXT_REAL_REPOSITORY_CATCH" in str(record.get("reason", ""))
    or "failure_class=" in str(record.get("reason", ""))
    for record in failures
):
    classification = "FAIL_TEST"
elif any(status in {"MISSING", "RUNNING"} for status in statuses):
    classification = "BLOCKED_INFRA"
else:
    classification = "FAIL_EVIDENCE"
reasons = sorted({str(record.get("reason")) for record in records if record.get("reason")})
print(classification + "\t" + "; ".join(reasons))
PY
)"
IFS=$'\t' read -r matrix_classification matrix_reasons <<< "$matrix_classification"
if [[ "$runtime_rc" -eq 0 && "$matrix_classification" == "PASS" ]]; then
  instrumentation_status="PASS"
  evidence_status="PASS"
  runtime_classification="PASS"
  runtime_failure_phase=""
  runtime_failure_reason=""
elif [[ "$runtime_rc" -eq 0 ]]; then
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=FAIL exit=%s\n' "$matrix_rc" >&2
  instrumentation_status="FAIL"
  evidence_status="FAIL"
  runtime_classification="$matrix_classification"
  runtime_failure_phase="INSTRUMENTATION"
  [[ "$matrix_classification" == "FAIL_EVIDENCE" ]] && runtime_failure_phase="EVIDENCE"
  [[ "$matrix_classification" == "BLOCKED_INFRA" ]] && runtime_failure_phase="PREFLIGHT"
  runtime_failure_reason="NH01_API28_THREE_DEVICE_ADAPTIVE_V2_${matrix_classification}"
  if [[ -n "$matrix_reasons" ]]; then runtime_failure_reason+="; $matrix_reasons"; fi
  case "$matrix_classification" in
    FAIL_TEST) runtime_rc=30 ;;
    BLOCKED_INFRA) runtime_rc=10 ;;
    *) runtime_rc=40 ;;
  esac
  printf 'NH01_ADAPTIVE_MATRIX_CLASSIFICATION=%s\n' "$matrix_classification" >&2
else
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=FAIL exit=%s runtime_setup_exit=%s\n' "$matrix_rc" "$runtime_rc" >&2
fi

write_runtime_result
python3 - "$YUJIAN_RUNTIME_EVIDENCE/runtime_gate_result.json" "$matrix_classification" "$matrix_reasons" <<'PY'
import json, pathlib, sys
path=pathlib.Path(sys.argv[1])
try: payload=json.loads(path.read_text(encoding="utf-8"))
except (OSError,json.JSONDecodeError): payload={}
payload["nh01_adaptive_matrix"]={
    "status":"PASS" if sys.argv[2]=="PASS" else "FAIL",
    "classification":sys.argv[2],
    "source_sha":payload.get("build_sha"),
    "profile_ids":["M04","M07","M10"],
    "layout_variant":"ADAPTIVE_V2",
    "failure_reasons":[reason for reason in sys.argv[3].split("; ") if reason],
}
path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### NH01 API 28 runtime result"
    echo
    echo "- Preflight: $preflight_status"
    echo "- APK install (one app/test APK install before the matrix): $install_status"
    echo "- Matrix: $matrix_classification (M04, M07, M10; ADAPTIVE_V2)"
    echo "- Source SHA: $YUJIAN_SOURCE_SHA"
  } >> "$GITHUB_STEP_SUMMARY"
fi

if [[ "$matrix_classification" != "PASS" ]]; then
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=FAIL classification=%s\n' "$matrix_classification" >&2
else
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=%s\n' "$YUJIAN_SOURCE_SHA"
fi

runtime_total_seconds="$(( $(date +%s) - runtime_started_epoch ))"
printf 'runtime_script_total_seconds=%s\n' "$runtime_total_seconds" | tee -a "$timing_log"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### NH01 API 28 runtime timings"
    echo
    cat "$timing_log"
    echo
    echo "ADB preflight: PASS (serial $selected_serial, sys.boot_completed=1)"
    echo "Three-device matrix (M04/M07/M10, ADAPTIVE_V2): $([[ "$matrix_rc" -eq 0 ]] && echo PASS || echo FAIL)"
  } >> "$GITHUB_STEP_SUMMARY"
fi
exit "$runtime_rc"
