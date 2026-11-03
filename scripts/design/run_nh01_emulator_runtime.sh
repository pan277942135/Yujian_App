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
python3 scripts/design/capture_nh01_device_metadata.py --output "$YUJIAN_RUNTIME_EVIDENCE/infra/device_metadata.json" --insets-dump "$YUJIAN_RUNTIME_EVIDENCE/infra/window_insets_dump.txt"

runtime_rc=0
bash scripts/run_android_runtime_gate.sh --gate gemini-home-v2 --api-level 28 --build-sha "$YUJIAN_SOURCE_SHA" --app-apk build/runtime-apks/app-debug.apk --test-apk build/runtime-apks/app-debug-androidTest.apk --evidence-dir "$YUJIAN_RUNTIME_EVIDENCE" || runtime_rc=$?

# Run only the three representative windows on the one installed APK pair.
# M04=360x640, M07=390x844, M10=430x932; ADAPTIVE_V2 only.
run_nh01_matrix() {
  local profile_filter="${1:-}"
  local variant_filter="${2:-}"
  local -a matrix_env=(
    "YUJIAN_ADB_BIN=$ADB"
    "YUJIAN_APP_PACKAGE=${YUJIAN_APP_PACKAGE:-com.yujian.ai.uiv2}"
    "YUJIAN_INSTRUMENTATION_TARGET=${YUJIAN_INSTRUMENTATION_TARGET:-com.yujian.ai.uiv2.test/androidx.test.runner.AndroidJUnitRunner}"
    "YUJIAN_APP_APK=build/runtime-apks/app-debug.apk"
    "YUJIAN_TEST_APK=build/runtime-apks/app-debug-androidTest.apk"
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

if (( matrix_rc != 0 )); then
  printf 'NH01_ADAPTIVE_DEVICE_MATRIX=FAIL exit=%s\n' "$matrix_rc" >&2
  matrix_classification="$(python3 - "$YUJIAN_RUNTIME_EVIDENCE" "$runtime_rc" "NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT" <<'PY'
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
if not records:
    report_stem = sys.argv[3] if len(sys.argv) > 3 else "NH01_THREE_DEVICE_ADAPTIVE_V2_REPORT"
    targeted_path = root / (report_stem + ".json")
    try:
        targeted = json.loads(targeted_path.read_text(encoding="utf-8"))
        records = [case.get("execution", {}) for case in targeted.get("cases", []) if isinstance(case, dict)]
    except (OSError, json.JSONDecodeError):
        records = []
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
        "failure_reason": "NH01_API28_THREE_DEVICE_ADAPTIVE_V2_" + classification,
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
