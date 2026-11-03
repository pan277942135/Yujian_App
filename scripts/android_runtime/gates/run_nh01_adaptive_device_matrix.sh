#!/usr/bin/env bash
set -uo pipefail

: "${YUJIAN_ADB_BIN:?YUJIAN_ADB_BIN is required}"
: "${YUJIAN_APP_PACKAGE:?YUJIAN_APP_PACKAGE is required}"
: "${YUJIAN_INSTRUMENTATION_TARGET:?YUJIAN_INSTRUMENTATION_TARGET is required}"
: "${YUJIAN_EVIDENCE_DIR:?YUJIAN_EVIDENCE_DIR is required}"
: "${BUILD_SHA:?BUILD_SHA is required}"

readonly TEST_CLASS="com.yujian.ai.ui.home.GeminiHomeDeviceMatrixTest#capturesProductionHomeForRepresentativeDevice"
readonly FIXTURE_SEED_CLASS="com.yujian.ai.HomeVisualEvidenceSeedTest#seedGeminiReferenceGuestHome"
readonly RECORD_SCENARIO="three_unique_catches"
readonly APP_APK="${YUJIAN_APP_APK:-build/runtime-apks/app-debug.apk}"
readonly TEST_APK="${YUJIAN_TEST_APK:-build/runtime-apks/app-debug-androidTest.apk}"
readonly RAW_DIR="$YUJIAN_EVIDENCE_DIR/nh01-adaptive-raw"
readonly RAW_CASE_DIR="$RAW_DIR/nh01-adaptive-visual-balance-v2"
readonly MATRIX_DIR="$YUJIAN_EVIDENCE_DIR/NH01_RUNTIME_MATRIX_V1"
readonly EXEC_DIR="$MATRIX_DIR/executions"
readonly LOG_DIR="$MATRIX_DIR/logs"
readonly REMOTE_DIR="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/nh01-adaptive-visual-balance-v2"
readonly AVD_NAME="${YUJIAN_AVD_NAME:-test}"
readonly EMULATOR_BIN="${YUJIAN_EMULATOR_BIN:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}/emulator/emulator}"

rm -rf "$RAW_DIR" "$MATRIX_DIR"
mkdir -p "$RAW_CASE_DIR" "$EXEC_DIR" "$LOG_DIR/diagnostics"
mkdir -p "$MATRIX_DIR/avd_profiles"
cp scripts/android_runtime/avd_profiles/nh01_api28_M04.ini \
  scripts/android_runtime/avd_profiles/nh01_api28_M07.ini \
  scripts/android_runtime/avd_profiles/nh01_api28_M10.ini "$MATRIX_DIR/avd_profiles/"

profiles=(
  "M04 360 640 1.0"
  "M07 390 844 1.0"
  "M10 430 932 1.0"
)
variants=("ADAPTIVE_V2")
all_ok=1
matrix_setup_ok=1
matrix_setup_reason=""
selected_serial=""
initial_size_override=""
initial_density_override=""
initial_font=""
initial_animator_scale=""
initial_transition_scale=""
initial_window_scale=""
last_native_size="unknown"
last_native_density="unknown"
device_api="unknown"
device_fingerprint="unknown"
app_package_verified=0
test_package_verified=0
instrumentation_verified=0

record_setup_failure() {
  local reason="$1"
  matrix_setup_ok=0
  if [[ -n "$matrix_setup_reason" ]]; then
    matrix_setup_reason+="; $reason"
  else
    matrix_setup_reason="$reason"
  fi
}

profile_filter="${YUJIAN_NH01_PROFILE_FILTER:-}"
if [[ -n "$profile_filter" && "$profile_filter" != "M04,M07,M10" ]]; then
  record_setup_failure "THREE_DEVICE_SCOPE_FIXED_TO_M04_M07_M10_GOT_${profile_filter//,/_}"
fi
if [[ -n "$profile_filter" ]]; then
  IFS=',' read -r -a requested_profiles <<< "$profile_filter"
  filtered_profiles=()
  for row in "${profiles[@]}"; do
    read -r row_profile _ <<< "$row"
    for requested_profile in "${requested_profiles[@]}"; do
      if [[ "$row_profile" == "$requested_profile" ]]; then
        filtered_profiles+=("$row")
        break
      fi
    done
  done
  if [[ "${#filtered_profiles[@]}" -ne "${#requested_profiles[@]}" ]]; then
    record_setup_failure "PROFILE_FILTER_CONTAINS_UNKNOWN_OR_DUPLICATE_PROFILE"
  fi
  profiles=("${filtered_profiles[@]}")
fi

variant_filter="${YUJIAN_NH01_VARIANT_FILTER:-}"
if [[ -n "$variant_filter" && "$variant_filter" != "ADAPTIVE_V2" ]]; then
  record_setup_failure "LAYOUT_VARIANT_SCOPE_FIXED_TO_ADAPTIVE_V2_GOT_${variant_filter//,/_}"
fi
if [[ -n "$variant_filter" ]]; then
  IFS=',' read -r -a requested_variants <<< "$variant_filter"
  filtered_variants=()
  for requested_variant in "${requested_variants[@]}"; do
    for variant in "${variants[@]}"; do
      if [[ "$variant" == "$requested_variant" ]]; then filtered_variants+=("$variant"); break; fi
    done
  done
  if [[ "${#filtered_variants[@]}" -ne "${#requested_variants[@]}" ]]; then
    record_setup_failure "VARIANT_FILTER_CONTAINS_UNKNOWN_OR_DUPLICATE_VARIANT"
  fi
  variants=("${filtered_variants[@]}")
fi

matrix_started_epoch="$(date +%s)"

write_case_record() {
  local profile="$1" variant="$2" status="$3" exit_code="$4" reason="$5"
  local report_present="$6" screenshots_present="$7" settings_match="$8" duration_seconds="${9:-}"
  local output="$EXEC_DIR/${profile}-${variant}.json"
  PROFILE_ID="$profile" LAYOUT_VARIANT="$variant" CASE_STATUS="$status" CASE_EXIT_CODE="$exit_code" \
    CASE_REASON="$reason" REPORT_PRESENT="$report_present" SCREENSHOTS_PRESENT="$screenshots_present" \
    SETTINGS_MATCH="$settings_match" CASE_DURATION_SECONDS="$duration_seconds" SOURCE_SHA="$BUILD_SHA" APP_PACKAGE="$YUJIAN_APP_PACKAGE" \
    INSTRUMENTATION_TARGET="$YUJIAN_INSTRUMENTATION_TARGET" REMOTE_DIR_VALUE="$REMOTE_DIR" \
    HOST_CASE_DIR="$LOG_DIR/diagnostics/${profile}_${variant}" python3 - "$output" <<'PY'
import datetime
import json
import os
from pathlib import Path
import sys

path = Path(sys.argv[1])
prior = {}
if path.is_file():
    try:
        prior = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        prior = {}
now = datetime.datetime.now(datetime.timezone.utc).isoformat()
payload = {
    "schema": "NH01_ADAPTIVE_MATRIX_EXECUTION_V1",
    "profile_id": os.environ["PROFILE_ID"],
    "layout_variant": os.environ["LAYOUT_VARIANT"],
    "source_sha": os.environ["SOURCE_SHA"],
    "status": os.environ["CASE_STATUS"],
    "exit_code": None if os.environ["CASE_EXIT_CODE"] == "null" else int(os.environ["CASE_EXIT_CODE"]),
    "reason": os.environ["CASE_REASON"],
    "device_report_present": os.environ["REPORT_PRESENT"] == "true",
    "required_screenshots_present": os.environ["SCREENSHOTS_PRESENT"] == "true",
    "profile_settings_match": os.environ["SETTINGS_MATCH"] == "true",
    "duration_seconds": float(os.environ["CASE_DURATION_SECONDS"]) if os.environ.get("CASE_DURATION_SECONDS") else None,
    "app_package": os.environ["APP_PACKAGE"],
    "instrumentation_target": os.environ["INSTRUMENTATION_TARGET"],
    "remote_output_directory": os.environ["REMOTE_DIR_VALUE"],
    "host_case_directory": os.environ["HOST_CASE_DIR"],
    "stdout_log": f"NH01_RUNTIME_MATRIX_V1/logs/{os.environ['PROFILE_ID']}_{os.environ['LAYOUT_VARIANT'].lower()}_stdout.log",
    "stderr_log": f"NH01_RUNTIME_MATRIX_V1/logs/{os.environ['PROFILE_ID']}_{os.environ['LAYOUT_VARIANT'].lower()}_stderr.log",
    "started_at_utc": prior.get("started_at_utc", now),
    "updated_at_utc": now,
}
if "started_at_utc" not in prior and payload["status"] not in ("MISSING", "PENDING"):
    payload["started_at_utc"] = now
path.parent.mkdir(parents=True, exist_ok=True)
temporary = path.with_suffix(path.suffix + ".tmp")
temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
os.replace(temporary, path)
PY
}

for row in "${profiles[@]}"; do
  read -r profile _width _height _font <<< "$row"
  for variant in "${variants[@]}"; do
    variant_token="$(tr '[:upper:]' '[:lower:]' <<< "$variant" | tr -d '\r\n')"
    write_case_record "$profile" "$variant_token" "MISSING" "null" "CASE_NOT_STARTED" false false false
  done
done

if ! command -v timeout >/dev/null 2>&1; then
  record_setup_failure "GNU_TIMEOUT_NOT_AVAILABLE"
fi

device_list="$LOG_DIR/adb_devices_initial.log"
"$YUJIAN_ADB_BIN" devices -l > "$device_list" 2>&1 || true
device_serials="$("$YUJIAN_ADB_BIN" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" {print $1}')"
device_count="$(awk 'NF {count++} END {print count+0}' <<< "$device_serials")"
if [[ "$device_count" -eq 1 ]]; then
  selected_serial="$(awk 'NF {print; exit}' <<< "$device_serials")"
else
  record_setup_failure "EXPECTED_ONE_ONLINE_ADB_DEVICE_FOUND_${device_count}"
fi

ADB=("$YUJIAN_ADB_BIN")
if [[ -n "$selected_serial" ]]; then ADB+=(-s "$selected_serial"); fi

app_package_from_apk=""
test_package_from_apk=""
runner_component="${YUJIAN_INSTRUMENTATION_TARGET%%(*}"
runner_component="${runner_component%% *}"
test_package_from_target="${runner_component%%/*}"
aapt_bin="${YUJIAN_AAPT_BIN:-}"
if [[ -z "$aapt_bin" ]]; then
  sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -n "$sdk_root" && -x "$sdk_root/build-tools/35.0.0/aapt" ]]; then
    aapt_bin="$sdk_root/build-tools/35.0.0/aapt"
  elif command -v aapt >/dev/null 2>&1; then
    aapt_bin="$(command -v aapt)"
  fi
fi

read_apk_package() {
  local apk="$1"
  [[ -n "$aapt_bin" && -x "$aapt_bin" && -s "$apk" ]] || return 1
  "$aapt_bin" dump badging "$apk" 2>/dev/null | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" | head -n 1
}

if [[ -n "$aapt_bin" ]]; then
  app_package_from_apk="$(read_apk_package "$APP_APK" || true)"
  test_package_from_apk="$(read_apk_package "$TEST_APK" || true)"
else
  record_setup_failure "AAPT_NOT_AVAILABLE_FOR_APK_PACKAGE_VERIFICATION"
fi
if [[ "$app_package_from_apk" == "$YUJIAN_APP_PACKAGE" ]]; then app_package_verified=1; else record_setup_failure "APP_APK_PACKAGE_MISMATCH expected=$YUJIAN_APP_PACKAGE actual=${app_package_from_apk:-MISSING}"; fi
if [[ "$test_package_from_apk" == "$test_package_from_target" && -n "$test_package_from_target" ]]; then test_package_verified=1; else record_setup_failure "TEST_APK_PACKAGE_MISMATCH target_package=$test_package_from_target actual=${test_package_from_apk:-MISSING}"; fi

pm_instrumentation="$LOG_DIR/pm_list_instrumentation.log"
pm_packages="$LOG_DIR/pm_packages.log"
pm_paths="$LOG_DIR/pm_paths.log"
if [[ -n "$selected_serial" ]]; then
  "${ADB[@]}" shell pm list instrumentation > "$pm_instrumentation" 2>&1 || true
  "${ADB[@]}" shell pm list packages > "$pm_packages" 2>&1 || true
  {
    "${ADB[@]}" shell pm path "$YUJIAN_APP_PACKAGE" 2>&1 || true
    "${ADB[@]}" shell pm path "$test_package_from_target" 2>&1 || true
  } > "$pm_paths"
  if grep -Fq "instrumentation:$YUJIAN_INSTRUMENTATION_TARGET (target=$YUJIAN_APP_PACKAGE)" "$pm_instrumentation" \
    && grep -Fxq "package:$YUJIAN_APP_PACKAGE" "$pm_packages" \
    && grep -Fxq "package:$test_package_from_target" "$pm_packages"; then
    instrumentation_verified=1
  else
    record_setup_failure "INSTALLED_PACKAGE_OR_INSTRUMENTATION_TARGET_MISMATCH"
  fi
  device_api="$("${ADB[@]}" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
  device_fingerprint="$("${ADB[@]}" shell getprop ro.build.fingerprint 2>/dev/null | tr -d '\r' || true)"
  if [[ "$device_api" != "28" ]]; then
    record_setup_failure "EXPECTED_API_28_DEVICE_FOUND_${device_api:-UNKNOWN}"
  fi
fi

wm_size_before="unknown"
wm_density_before="unknown"
initial_size_override=""
initial_density_override=""
initial_font=""
if [[ -n "$selected_serial" ]]; then
  initial_size_output="$("${ADB[@]}" shell wm size 2>&1 || true)"
  initial_density_output="$("${ADB[@]}" shell wm density 2>&1 || true)"
  wm_size_before="$(sed -n 's/^Physical size: //p' <<< "$initial_size_output" | head -n 1 | tr -d '\r')"
  wm_density_before="$(sed -n 's/^Physical density: //p' <<< "$initial_density_output" | head -n 1 | tr -d '\r')"
  initial_size_override="$(sed -n 's/^Override size: //p' <<< "$initial_size_output" | head -n 1 | tr -d '\r')"
  initial_density_override="$(sed -n 's/^Override density: //p' <<< "$initial_density_output" | head -n 1 | tr -d '\r')"
  initial_font="$("${ADB[@]}" shell settings get system font_scale 2>/dev/null | tr -d '\r' || true)"
  initial_animator_scale="$("${ADB[@]}" shell settings get global animator_duration_scale 2>/dev/null | tr -d '\r' || true)"
  initial_transition_scale="$("${ADB[@]}" shell settings get global transition_animation_scale 2>/dev/null | tr -d '\r' || true)"
  initial_window_scale="$("${ADB[@]}" shell settings get global window_animation_scale 2>/dev/null | tr -d '\r' || true)"
fi
if [[ -z "$initial_font" ]]; then initial_font="null"; fi
if [[ -z "$initial_animator_scale" ]]; then initial_animator_scale="null"; fi
if [[ -z "$initial_transition_scale" ]]; then initial_transition_scale="null"; fi
if [[ -z "$initial_window_scale" ]]; then initial_window_scale="null"; fi
last_native_size="$wm_size_before"
last_native_density="$wm_density_before"

BUILD_SHA="$BUILD_SHA" YUJIAN_ADB_BIN="$YUJIAN_ADB_BIN" YUJIAN_APP_PACKAGE="$YUJIAN_APP_PACKAGE" \
  YUJIAN_INSTRUMENTATION_TARGET="$YUJIAN_INSTRUMENTATION_TARGET" APP_APK_VALUE="$APP_APK" TEST_APK_VALUE="$TEST_APK" \
  SELECTED_SERIAL="$selected_serial" DEVICE_API="$device_api" DEVICE_FINGERPRINT="$device_fingerprint" \
  WM_SIZE_BEFORE="$wm_size_before" WM_DENSITY_BEFORE="$wm_density_before" INITIAL_FONT_SCALE="$initial_font" \
  INITIAL_ANIMATOR_SCALE="$initial_animator_scale" INITIAL_TRANSITION_SCALE="$initial_transition_scale" INITIAL_WINDOW_SCALE="$initial_window_scale" \
  INITIAL_SIZE_OVERRIDE="$initial_size_override" INITIAL_DENSITY_OVERRIDE="$initial_density_override" \
  AVD_NAME_VALUE="$AVD_NAME" \
  APP_PACKAGE_FROM_APK="$app_package_from_apk" TEST_PACKAGE_FROM_APK="$test_package_from_apk" \
  APP_PACKAGE_VERIFIED="$app_package_verified" TEST_PACKAGE_VERIFIED="$test_package_verified" \
  INSTRUMENTATION_VERIFIED="$instrumentation_verified" REMOTE_DIR_VALUE="$REMOTE_DIR" RAW_DIR_VALUE="$RAW_DIR" \
  RAW_CASE_DIR_VALUE="$RAW_CASE_DIR" MATRIX_SETUP_OK="$matrix_setup_ok" MATRIX_SETUP_REASON="$matrix_setup_reason" \
  python3 - "$MATRIX_DIR/matrix_environment.json" <<'PY'
# Re-run the pure serializer with the matrix-specific values exported for this child.
import hashlib, json, os, platform, sys
from pathlib import Path
def digest(name):
    path = Path(os.environ.get(name, ""))
    if not path.is_file(): return None
    h=hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda:f.read(1024*1024), b""): h.update(chunk)
    return h.hexdigest()
p={
 "schema":"NH01_ADAPTIVE_MATRIX_ENVIRONMENT_V1","source_sha":os.environ.get("BUILD_SHA"),
 "workflow_run_id":os.environ.get("GITHUB_RUN_ID","local"),"runner_name":os.environ.get("RUNNER_NAME","unknown"),
 "runner_os":os.environ.get("RUNNER_OS",platform.system()),"runner_arch":os.environ.get("RUNNER_ARCH",platform.machine()),
 "host_name":platform.node(),"adb_binary":os.environ.get("YUJIAN_ADB_BIN"),"adb_serial":os.environ.get("SELECTED_SERIAL") or None,
 "device_api":os.environ.get("DEVICE_API","unknown"),"device_fingerprint":os.environ.get("DEVICE_FINGERPRINT","unknown"),
 "avd_name":os.environ.get("AVD_NAME_VALUE"),
 "physical_size_before":os.environ.get("WM_SIZE_BEFORE","unknown"),"physical_density_before":os.environ.get("WM_DENSITY_BEFORE","unknown"),
 "initial_size_override":os.environ.get("INITIAL_SIZE_OVERRIDE") or None,"initial_density_override":os.environ.get("INITIAL_DENSITY_OVERRIDE") or None,
 "initial_font_scale":os.environ.get("INITIAL_FONT_SCALE","unknown"),"app_apk":os.environ.get("APP_APK_VALUE"),"app_apk_sha256":digest("APP_APK_VALUE"),
 "initial_animation_scales":{
   "animator_duration_scale":os.environ.get("INITIAL_ANIMATOR_SCALE"),
   "transition_animation_scale":os.environ.get("INITIAL_TRANSITION_SCALE"),
   "window_animation_scale":os.environ.get("INITIAL_WINDOW_SCALE"),
 },
 "matrix_animation_scales":{"animator_duration_scale":0.0,"transition_animation_scale":0.0,"window_animation_scale":0.0},
 "test_apk":os.environ.get("TEST_APK_VALUE"),"test_apk_sha256":digest("TEST_APK_VALUE"),"app_package_expected":os.environ.get("YUJIAN_APP_PACKAGE"),
 "app_package_from_apk":os.environ.get("APP_PACKAGE_FROM_APK"),"test_package_from_apk":os.environ.get("TEST_PACKAGE_FROM_APK"),
 "instrumentation_target":os.environ.get("YUJIAN_INSTRUMENTATION_TARGET"),"package_verified":os.environ.get("APP_PACKAGE_VERIFIED")=="1",
 "test_package_verified":os.environ.get("TEST_PACKAGE_VERIFIED")=="1","installed_packages_and_target_verified":os.environ.get("INSTRUMENTATION_VERIFIED")=="1",
 "device_report_directory":os.environ.get("REMOTE_DIR_VALUE"),"host_pull_root":os.environ.get("RAW_DIR_VALUE"),
 "host_case_directory_after_pull":os.environ.get("RAW_CASE_DIR_VALUE"),"setup_status":"PASS" if os.environ.get("MATRIX_SETUP_OK")=="1" else "FAIL",
 "setup_reason":os.environ.get("MATRIX_SETUP_REASON","")}
Path(sys.argv[1]).write_text(json.dumps(p,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY

restore_device() {
  [[ -n "$selected_serial" ]] || return 0
  local rc=0
  if [[ -n "$initial_size_override" ]]; then
    "${ADB[@]}" shell wm size "$initial_size_override" >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
  else
    "${ADB[@]}" shell wm size reset >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
  fi
  if [[ -n "$initial_density_override" ]]; then
    "${ADB[@]}" shell wm density "$initial_density_override" >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
  else
    "${ADB[@]}" shell wm density reset >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
  fi
  if [[ "$initial_font" == "null" ]]; then
    "${ADB[@]}" shell settings delete system font_scale >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
  else
    "${ADB[@]}" shell settings put system font_scale "$initial_font" >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
  fi
  restore_global_setting() {
    local name="$1" value="$2"
    if [[ "$value" == "null" ]]; then
      "${ADB[@]}" shell settings delete global "$name" >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
    else
      "${ADB[@]}" shell settings put global "$name" "$value" >> "$LOG_DIR/restore_device.log" 2>&1 || rc=1
    fi
  }
  restore_global_setting animator_duration_scale "$initial_animator_scale"
  restore_global_setting transition_animation_scale "$initial_transition_scale"
  restore_global_setting window_animation_scale "$initial_window_scale"
  {
    restored_size="$("${ADB[@]}" shell wm size 2>&1 || true)"
    restored_density="$("${ADB[@]}" shell wm density 2>&1 || true)"
    restored_font="$("${ADB[@]}" shell settings get system font_scale 2>&1 | tr -d '\r' || true)"
    restored_animator="$("${ADB[@]}" shell settings get global animator_duration_scale 2>&1 | tr -d '\r' || true)"
    restored_transition="$("${ADB[@]}" shell settings get global transition_animation_scale 2>&1 | tr -d '\r' || true)"
    restored_window="$("${ADB[@]}" shell settings get global window_animation_scale 2>&1 | tr -d '\r' || true)"
    restored_size_value="$(sed -n 's/^Override size: //p; s/^Physical size: //p' <<< "$restored_size" | tail -n 1 | tr -d '\r')"
    restored_density_value="$(sed -n 's/^Override density: //p; s/^Physical density: //p' <<< "$restored_density" | tail -n 1 | tr -d '\r')"
    expected_size_value="${initial_size_override:-$last_native_size}"
    expected_density_value="${initial_density_override:-$last_native_density}"
    printf 'wm_size=%s\n' "${restored_size//$'\n'/; }"
    printf 'wm_density=%s\n' "${restored_density//$'\n'/; }"
    printf 'font_scale=%s\n' "$restored_font"
    printf 'animator_duration_scale=%s\n' "$restored_animator"
    printf 'transition_animation_scale=%s\n' "$restored_transition"
    printf 'window_animation_scale=%s\n' "$restored_window"
    if [[ "$restored_size_value" != "$expected_size_value" || "$restored_density_value" != "$expected_density_value" || \
          "$restored_font" != "$initial_font" || "$restored_animator" != "$initial_animator_scale" || \
          "$restored_transition" != "$initial_transition_scale" || "$restored_window" != "$initial_window_scale" ]]; then
      rc=1
    fi
  } > "$LOG_DIR/device_settings_after_restore.txt" 2>&1 || rc=1
  return "$rc"
}
trap 'restore_device || true' EXIT INT TERM

if [[ -n "$selected_serial" ]]; then
  "${ADB[@]}" shell rm -rf "$REMOTE_DIR" > "$LOG_DIR/clear_remote_output.log" 2>&1 || {
    record_setup_failure "COULD_NOT_CLEAR_REMOTE_OUTPUT_DIRECTORY"
  }
  printf '%s\n' "System animations were disabled once by android-emulator-runner." > "$LOG_DIR/disable_system_animations.log"
  for setting in animator_duration_scale transition_animation_scale window_animation_scale; do
    current_scale="$("${ADB[@]}" shell settings get global "$setting" 2>/dev/null | tr -d '\r' || true)"
    if [[ "$current_scale" != "0.0" ]]; then
      record_setup_failure "SYSTEM_ANIMATION_NOT_PRE_DISABLED_$setting=${current_scale:-UNKNOWN}"
    fi
  done
fi

fixture_seed_status="BLOCKED"
fixture_seed_rc="null"
fixture_seed_log="$LOG_DIR/fixture_seed.log"
if [[ -n "$selected_serial" && "$app_package_verified" -eq 1 && "$test_package_verified" -eq 1 && "$instrumentation_verified" -eq 1 ]]; then
  seed_command=("${ADB[@]}" shell am instrument -w -r -e class "$FIXTURE_SEED_CLASS" "$YUJIAN_INSTRUMENTATION_TARGET")
  seed_rc=0
  if command -v timeout >/dev/null 2>&1; then
    timeout 180s "${seed_command[@]}" > "$fixture_seed_log" 2>&1 || seed_rc=$?
  else
    "${seed_command[@]}" > "$fixture_seed_log" 2>&1 || seed_rc=$?
  fi
  fixture_seed_rc="$seed_rc"
  if [[ "$seed_rc" -eq 0 ]] && grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \(1 test' "$fixture_seed_log"; then
    fixture_seed_status="PASS"
  else
    fixture_seed_status="FAIL"
    record_setup_failure "DETERMINISTIC_GUEST_FIXTURE_SEED_FAILED_EXIT_${seed_rc}"
  fi
else
  printf 'fixture seed not run: app/test package or instrumentation target is unavailable\n' > "$fixture_seed_log"
  record_setup_failure "DETERMINISTIC_GUEST_FIXTURE_SEED_BLOCKED"
fi
python3 - "$MATRIX_DIR/fixture_seed.json" "$fixture_seed_status" "$fixture_seed_rc" "$fixture_seed_log" <<'PY'
import json, pathlib, sys
path=pathlib.Path(sys.argv[1]); path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps({"schema":"NH01_PRODUCTION_HOME_FIXTURE_SEED_V1","status":sys.argv[2],
                            "exit_code":None if sys.argv[3]=="null" else int(sys.argv[3]),
                            "test":"com.yujian.ai.HomeVisualEvidenceSeedTest#seedGeminiReferenceGuestHome",
                            "log":sys.argv[4]},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY
if [[ -s "$MATRIX_DIR/matrix_environment.json" ]]; then
  python3 - "$MATRIX_DIR/matrix_environment.json" "$fixture_seed_status" "$matrix_setup_ok" "$matrix_setup_reason" <<'PY'
import json, pathlib, sys
p=pathlib.Path(sys.argv[1]); d=json.loads(p.read_text(encoding="utf-8"))
d["fixture_seed_status"]=sys.argv[2]
d["setup_status"]="PASS" if sys.argv[3]=="1" else "FAIL"
d["setup_reason"]=sys.argv[4]
p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY
fi

restart_native_avd() {
  local profile="$1" width="$2" height="$3" density="$4"
  local port="${selected_serial##*-}"
  local boot_log="$LOG_DIR/${profile}_emulator_boot.log"
  local deadline state boot_completed

  if [[ -n "$selected_serial" ]]; then
    "${ADB[@]}" -s "$selected_serial" emu kill > "$LOG_DIR/${profile}_emulator_stop.log" 2>&1 || true
    deadline="$(( $(date +%s) + 60 ))"
    while (( $(date +%s) < deadline )); do
      state="$("${ADB[@]}" -s "$selected_serial" get-state 2>/dev/null | tr -d '\r' || true)"
      [[ "$state" != "device" ]] && break
      sleep 1
    done
    sleep 5
  fi

  bash scripts/android_runtime/configure_nh01_api28_avd.sh "$AVD_NAME" "$profile" \
    > "$LOG_DIR/${profile}_avd_config.log" 2>&1 || return 1

  if [[ ! -x "$EMULATOR_BIN" ]]; then
    printf 'emulator binary not executable: %s\n' "$EMULATOR_BIN" > "$boot_log"
    return 1
  fi
  "$EMULATOR_BIN" -avd "$AVD_NAME" -port "$port" -skin "${width}x${height}" -dpi-device "$density" \
    -no-window -gpu swiftshader -noaudio \
    -no-boot-anim -camera-back none -partition-size 4096 -no-snapshot > "$boot_log" 2>&1 < /dev/null &
  local emulator_pid=$!
  printf 'emulator_pid=%s\navd=%s\nprofile=%s\nrequested_native_display=%sx%s@%sdpi\n' \
    "$emulator_pid" "$AVD_NAME" "$profile" "$width" "$height" "$density" >> "$boot_log"

  deadline="$(( $(date +%s) + 360 ))"
  while (( $(date +%s) < deadline )); do
    state="$(timeout 10s "${ADB[@]}" -s "$selected_serial" get-state 2>/dev/null | tr -d '\r' || true)"
    boot_completed="$(timeout 10s "${ADB[@]}" -s "$selected_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$state" == "device" && "$boot_completed" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  printf 'INFRA_EMULATOR_BOOT_TIMEOUT profile=%s timeout_seconds=360 last_state=%s boot_completed=%s pid=%s\n' \
    "$profile" "${state:-MISSING}" "${boot_completed:-MISSING}" "$emulator_pid" >> "$boot_log"
  return 1
}

capture_native_display_state() {
  local profile="$1" requested="$2" profile_dir="$LOG_DIR/display/$profile"
  mkdir -p "$profile_dir"
  "${ADB[@]}" shell wm size > "$profile_dir/wm_size.txt" 2>&1 || true
  "${ADB[@]}" shell wm density > "$profile_dir/wm_density.txt" 2>&1 || true
  "${ADB[@]}" shell getprop qemu.sf.lcd_density > "$profile_dir/qemu_sf_lcd_density.txt" 2>&1 || true
  "${ADB[@]}" shell getprop ro.sf.lcd_density > "$profile_dir/ro_sf_lcd_density.txt" 2>&1 || true
  "${ADB[@]}" shell getprop ro.build.version.sdk > "$profile_dir/api_level.txt" 2>&1 || true
  "${ADB[@]}" shell getprop ro.build.fingerprint > "$profile_dir/build_fingerprint.txt" 2>&1 || true
  "${ADB[@]}" shell dumpsys display > "$profile_dir/dumpsys_display.txt" 2>&1 || true
  "${ADB[@]}" shell dumpsys SurfaceFlinger > "$profile_dir/dumpsys_surfaceflinger.txt" 2>&1 || true
  "${ADB[@]}" shell settings get secure accessibility_enabled > "$profile_dir/accessibility_enabled.txt" 2>&1 || true
  "${ADB[@]}" shell settings get secure accessibility_display_magnification_enabled > "$profile_dir/accessibility_magnification_enabled.txt" 2>&1 || true
  "${ADB[@]}" shell settings get secure enabled_accessibility_services > "$profile_dir/enabled_accessibility_services.txt" 2>&1 || true
  "${ADB[@]}" shell dumpsys accessibility > "$profile_dir/dumpsys_accessibility.txt" 2>&1 || true
  "${ADB[@]}" exec-out screencap -p > "$profile_dir/launcher_surface.png" 2> "$profile_dir/launcher_surface_stderr.log" || true

  local physical_size override_size physical_density override_density qemu_density ro_density screenshot_dimensions
  local accessibility_enabled magnification_enabled accessibility_services magnification_scale api_level build_fingerprint
  physical_size="$(sed -n 's/^Physical size: //p' "$profile_dir/wm_size.txt" | head -n 1 | tr -d '\r')"
  override_size="$(sed -n 's/^Override size: //p' "$profile_dir/wm_size.txt" | head -n 1 | tr -d '\r')"
  physical_density="$(sed -n 's/^Physical density: //p' "$profile_dir/wm_density.txt" | head -n 1 | tr -d '\r')"
  override_density="$(sed -n 's/^Override density: //p' "$profile_dir/wm_density.txt" | head -n 1 | tr -d '\r')"
  qemu_density="$(tr -d '\r\n' < "$profile_dir/qemu_sf_lcd_density.txt" 2>/dev/null || true)"
  ro_density="$(tr -d '\r\n' < "$profile_dir/ro_sf_lcd_density.txt" 2>/dev/null || true)"
  api_level="$(tr -d '\r\n' < "$profile_dir/api_level.txt" 2>/dev/null || true)"
  build_fingerprint="$(tr -d '\r\n' < "$profile_dir/build_fingerprint.txt" 2>/dev/null || true)"
  accessibility_enabled="$(tr -d '\r\n' < "$profile_dir/accessibility_enabled.txt" 2>/dev/null || true)"
  magnification_enabled="$(tr -d '\r\n' < "$profile_dir/accessibility_magnification_enabled.txt" 2>/dev/null || true)"
  accessibility_services="$(tr -d '\r\n' < "$profile_dir/enabled_accessibility_services.txt" 2>/dev/null || true)"
  magnification_scale="$(grep -Eo 'scale[=:][[:space:]]*[0-9]+([.][0-9]+)?' "$profile_dir/dumpsys_accessibility.txt" | head -n 1 | tr -d ' ' || true)"
  screenshot_dimensions="$(python3 - "$profile_dir/launcher_surface.png" <<'PY'
import pathlib, struct, sys
p=pathlib.Path(sys.argv[1])
try:
    data=p.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n": raise ValueError("invalid PNG signature")
    print(f"{struct.unpack('>I',data[16:20])[0]}x{struct.unpack('>I',data[20:24])[0]}")
except Exception:
    print("INVALID")
PY
)"
  PROFILE="$profile" REQUESTED="$requested" PHYSICAL_SIZE="$physical_size" OVERRIDE_SIZE="$override_size" \
    PHYSICAL_DENSITY="$physical_density" OVERRIDE_DENSITY="$override_density" QEMU_DENSITY="$qemu_density" \
  RO_DENSITY="$ro_density" SCREENSHOT_DIMENSIONS="$screenshot_dimensions" \
  API_LEVEL_VALUE="$api_level" BUILD_FINGERPRINT_VALUE="$build_fingerprint" \
  ACCESSIBILITY_ENABLED="$accessibility_enabled" MAGNIFICATION_ENABLED="$magnification_enabled" \
  ACCESSIBILITY_SERVICES="$accessibility_services" MAGNIFICATION_SCALE="$magnification_scale" \
    python3 - "$profile_dir/native_display_validation.json" <<'PY'
import json, os, pathlib, sys
p={"profile_id":os.environ["PROFILE"],"requested_physical_size":os.environ["REQUESTED"],
   "wm_physical_size":os.environ["PHYSICAL_SIZE"],"wm_override_size":os.environ["OVERRIDE_SIZE"] or None,
   "wm_physical_density_dpi":os.environ["PHYSICAL_DENSITY"],"wm_override_density_dpi":os.environ["OVERRIDE_DENSITY"] or None,
   "qemu_sf_lcd_density":os.environ["QEMU_DENSITY"],"ro_sf_lcd_density":os.environ["RO_DENSITY"],
   "api_level":os.environ["API_LEVEL_VALUE"],"build_fingerprint":os.environ["BUILD_FINGERPRINT_VALUE"],
   "accessibility_enabled":os.environ["ACCESSIBILITY_ENABLED"],
   "accessibility_display_magnification_enabled":os.environ["MAGNIFICATION_ENABLED"],
   "enabled_accessibility_services":os.environ["ACCESSIBILITY_SERVICES"],
   "accessibility_magnification_scale":os.environ["MAGNIFICATION_SCALE"],
   "launcher_screencap_dimensions":os.environ["SCREENSHOT_DIMENSIONS"],
   "native_viewport_pass":os.environ["PHYSICAL_SIZE"]==os.environ["REQUESTED"] and not os.environ["OVERRIDE_SIZE"] and
       os.environ["PHYSICAL_DENSITY"]=="160" and not os.environ["OVERRIDE_DENSITY"] and
       os.environ["SCREENSHOT_DIMENSIONS"]==os.environ["REQUESTED"] and os.environ["API_LEVEL_VALUE"]=="28"}
pathlib.Path(sys.argv[1]).write_text(json.dumps(p,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY
}

for row in "${profiles[@]}"; do
  read -r profile width height font_scale expected_safe_height_dp <<< "$row"
  requested="${width}x${height}"
  font_log="$LOG_DIR/${profile}_font_scale.log"
  native_boot_ok=true
  profile_boot_seconds=0
  current_size="$("${ADB[@]}" shell wm size 2>/dev/null | sed -n 's/^Physical size: //p' | head -n 1 | tr -d '\r' || true)"
  current_size_override="$("${ADB[@]}" shell wm size 2>/dev/null | sed -n 's/^Override size: //p' | head -n 1 | tr -d '\r' || true)"
  current_density="$("${ADB[@]}" shell wm density 2>/dev/null | sed -n 's/^Physical density: //p' | head -n 1 | tr -d '\r' || true)"
  current_density_override="$("${ADB[@]}" shell wm density 2>/dev/null | sed -n 's/^Override density: //p' | head -n 1 | tr -d '\r' || true)"
  restart_profile=true
  if [[ "$profile" == "M04" && "$current_size" == "$requested" && -z "$current_size_override" && \
        "$current_density" == "160" && -z "$current_density_override" ]]; then
    restart_profile=false
  fi
  if [[ "$selected_serial" ]]; then
    config_started="$(date +%s)"
    if [[ "$restart_profile" == true ]]; then
      if ! restart_native_avd "$profile" "$width" "$height" 160; then
        native_boot_ok=false
      fi
    else
      bash scripts/android_runtime/configure_nh01_api28_avd.sh "$AVD_NAME" "$profile" \
        > "$LOG_DIR/${profile}_avd_config.log" 2>&1 || native_boot_ok=false
    fi
    profile_boot_seconds="$(( $(date +%s) - config_started ))"
  else
    native_boot_ok=false
  fi

  font_rc=0
  if [[ "$native_boot_ok" == true ]]; then
    "${ADB[@]}" shell settings put system font_scale "$font_scale" > "$font_log" 2>&1 || font_rc=$?
    sleep 1
    capture_native_display_state "$profile" "$requested"
  else
    echo "Native display boot failed" > "$font_log"
  fi

  physical_size="unknown"
  physical_density="unknown"
  override_size=""
  override_density=""
  qemu_density="unknown"
  ro_density="unknown"
  screenshot_dimensions="INVALID"
  native_viewport_pass=false
  if [[ -s "$LOG_DIR/display/$profile/native_display_validation.json" ]]; then
    physical_size="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["wm_physical_size"])' "$LOG_DIR/display/$profile/native_display_validation.json")"
    physical_density="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["wm_physical_density_dpi"])' "$LOG_DIR/display/$profile/native_display_validation.json")"
    override_size="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("wm_override_size") or "")' "$LOG_DIR/display/$profile/native_display_validation.json")"
    override_density="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("wm_override_density_dpi") or "")' "$LOG_DIR/display/$profile/native_display_validation.json")"
    qemu_density="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["qemu_sf_lcd_density"])' "$LOG_DIR/display/$profile/native_display_validation.json")"
    ro_density="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["ro_sf_lcd_density"])' "$LOG_DIR/display/$profile/native_display_validation.json")"
    screenshot_dimensions="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["launcher_screencap_dimensions"])' "$LOG_DIR/display/$profile/native_display_validation.json")"
    native_viewport_pass="$(python3 -c 'import json,sys; print(str(json.load(open(sys.argv[1]))["native_viewport_pass"]).lower())' "$LOG_DIR/display/$profile/native_display_validation.json")"
  fi
  if [[ "$physical_size" != "unknown" ]]; then last_native_size="$physical_size"; fi
  if [[ "$physical_density" != "unknown" ]]; then last_native_density="$physical_density"; fi

  actual_wm_size="$physical_size"
  actual_density="unknown"
  actual_font="unknown"
  actual_animator_scale="unknown"
  actual_transition_scale="unknown"
  actual_window_scale="unknown"
  if [[ -n "$selected_serial" ]]; then
    actual_density="$physical_density"
    actual_font="$("${ADB[@]}" shell settings get system font_scale 2>&1 | tr -d '\r')"
    actual_animator_scale="$("${ADB[@]}" shell settings get global animator_duration_scale 2>&1 | tr -d '\r')"
    actual_transition_scale="$("${ADB[@]}" shell settings get global transition_animation_scale 2>&1 | tr -d '\r')"
    actual_window_scale="$("${ADB[@]}" shell settings get global window_animation_scale 2>&1 | tr -d '\r')"
  fi
  profile_settings_match=true
  [[ "$native_boot_ok" == true && "$native_viewport_pass" == true && "$font_rc" -eq 0 ]] || profile_settings_match=false
  [[ "$actual_wm_size" == "$requested" && -z "$override_size" ]] || profile_settings_match=false
  [[ "$actual_density" == "160" ]] || profile_settings_match=false
  [[ -z "$override_density" ]] || profile_settings_match=false
  [[ "$actual_font" == "$font_scale" ]] || profile_settings_match=false
  [[ "$actual_animator_scale" == "0.0" && "$actual_transition_scale" == "0.0" && "$actual_window_scale" == "0.0" ]] || profile_settings_match=false
python3 - "$LOG_DIR/${profile}_device_settings.json" "$profile" "$requested" "$font_scale" "$actual_wm_size" "$actual_density" "$actual_font" "$actual_animator_scale" "$actual_transition_scale" "$actual_window_scale" "$native_boot_ok" "$override_size" "$override_density" "$qemu_density" "$ro_density" "$screenshot_dimensions" "$native_viewport_pass" "$profile_boot_seconds" <<'PY'
import json, sys
from pathlib import Path
p={"profile_id":sys.argv[2],"requested_native_size_px":sys.argv[3],"requested_density_dpi":160,"requested_font_scale":float(sys.argv[4]),
   "actual_wm_size":sys.argv[5],"actual_wm_density":sys.argv[6],"actual_font_scale":sys.argv[7],
   "android_animation_scales":{"animator_duration_scale":sys.argv[8],"transition_animation_scale":sys.argv[9],"window_animation_scale":sys.argv[10]},
   "native_avd_boot_pass":sys.argv[11]=="true","wm_override_size":sys.argv[12] or None,"wm_override_density_dpi":sys.argv[13] or None,
   "qemu_sf_lcd_density":sys.argv[14],"ro_sf_lcd_density":sys.argv[15],"launcher_screencap_dimensions":sys.argv[16],
   "native_viewport_pass":sys.argv[17]=="true","profile_boot_seconds":int(sys.argv[18])}
Path(sys.argv[1]).write_text(json.dumps(p,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY

  for variant in "${variants[@]}"; do
    variant_token="$(tr '[:upper:]' '[:lower:]' <<< "$variant" | tr -d '\r\n')"
    case_key="${profile}_${variant_token}"
    case_dir="$LOG_DIR/diagnostics/$case_key"
    mkdir -p "$case_dir"
    stdout="$LOG_DIR/${case_key}_stdout.log"
    stderr="$LOG_DIR/${case_key}_stderr.log"
    combined="$LOG_DIR/${case_key}.log"
    report_name="$profile-${variant_token}.json"
    initial_name="${profile}_${width}x${height}_initial.png"
    swipe_name="${profile}_${width}x${height}_after_swipe.png"
    failure_name="${profile}_failure.png"
    if [[ "$matrix_setup_ok" -ne 1 ]]; then
      reason="MATRIX_SETUP_BLOCKED: ${matrix_setup_reason:-UNSPECIFIED}"
      write_case_record "$profile" "$variant_token" "MISSING" "null" "$reason" false false "$profile_settings_match"
      continue
    fi
    if [[ "$native_boot_ok" != true ]]; then
      all_ok=0
      reason="INFRA_EMULATOR_BOOT_TIMEOUT_OR_AVD_CONFIG_FAILED"
      write_case_record "$profile" "$variant_token" "FAIL" "null" "$reason" false false false "$profile_boot_seconds"
      printf 'CASE %s %s status=FAIL reason=%s\n' "$profile" "$variant" "$reason"
      continue
    fi

    case_started_epoch="$(date +%s)"
    "${ADB[@]}" logcat -c > "$case_dir/logcat_clear.stdout.log" 2> "$case_dir/logcat_clear.stderr.log" || true
    "${ADB[@]}" shell am force-stop "$YUJIAN_APP_PACKAGE" > "$case_dir/force_stop.stdout.log" 2> "$case_dir/force_stop.stderr.log" || true
    write_case_record "$profile" "$variant_token" "RUNNING" "null" "INSTRUMENTATION_STARTED" false false "$profile_settings_match"
    instrumentation_args=(
      -e class "$TEST_CLASS"
      -e profile_id "$profile"
      -e layout_variant "$variant"
      -e font_scale "$font_scale"
      -e expected_display_px "$requested"
      -e source_sha "$BUILD_SHA"
      -e scenario "$RECORD_SCENARIO"
    )
    if [[ -n "$expected_safe_height_dp" ]]; then
      instrumentation_args+=(-e expected_safe_height_dp "$expected_safe_height_dp")
    fi
    instrumentation_command=(
      "${ADB[@]}" shell am instrument -w -r
      "${instrumentation_args[@]}"
      "$YUJIAN_INSTRUMENTATION_TARGET"
    )
    if command -v timeout >/dev/null 2>&1; then
      timeout 300s "${instrumentation_command[@]}" > "$stdout" 2> "$stderr"
    else
      "${instrumentation_command[@]}" > "$stdout" 2> "$stderr"
    fi
    instrumentation_rc=$?
    case_elapsed_seconds="$(( $(date +%s) - case_started_epoch ))"
    {
      printf 'COMMAND='
      printf '%q ' "${instrumentation_command[@]}"
      printf '\n'
      echo "EXIT_CODE=$instrumentation_rc"
      echo "--- stdout ---"
      cat "$stdout"
      echo "--- stderr ---"
      cat "$stderr"
    } > "$combined"

    profile_diag_dir="$LOG_DIR/display/$profile"
    mkdir -p "$profile_diag_dir"
    "${ADB[@]}" shell wm size > "$profile_diag_dir/wm_size_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell wm density > "$profile_diag_dir/wm_density_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell getprop qemu.sf.lcd_density > "$profile_diag_dir/qemu_sf_lcd_density_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell getprop ro.sf.lcd_density > "$profile_diag_dir/ro_sf_lcd_density_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell dumpsys window windows > "$profile_diag_dir/dumpsys_window_windows_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell dumpsys activity activities > "$profile_diag_dir/dumpsys_activity_activities_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell dumpsys display > "$profile_diag_dir/dumpsys_display_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell dumpsys SurfaceFlinger > "$profile_diag_dir/dumpsys_surfaceflinger_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell dumpsys accessibility > "$profile_diag_dir/dumpsys_accessibility_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell settings get secure accessibility_enabled > "$profile_diag_dir/accessibility_enabled_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell settings get secure accessibility_display_magnification_enabled > "$profile_diag_dir/accessibility_magnification_enabled_after_app.txt" 2>&1 || true
    "${ADB[@]}" shell settings get secure enabled_accessibility_services > "$profile_diag_dir/enabled_accessibility_services_after_app.txt" 2>&1 || true
    "${ADB[@]}" exec-out screencap -p > "$case_dir/adb_screencap_after_instrumentation.png" 2> "$case_dir/adb_screencap_after_instrumentation.stderr.log" || true

    pull_log="$case_dir/adb_pull_after_case.log"
    "${ADB[@]}" pull "$REMOTE_DIR" "$RAW_DIR" > "$pull_log" 2>&1 || all_ok=0
    device_report="$RAW_CASE_DIR/$report_name"
    report_present=false
    screenshots_present=false
    report_status="MISSING"
    if [[ -s "$device_report" ]]; then
      report_present=true
      report_status="$(python3 - "$device_report" <<'PY'
import json,sys
try:
 print(json.load(open(sys.argv[1],encoding="utf-8")).get("status","INVALID"))
except Exception:
 print("INVALID")
PY
)"
    fi
    device_failure_detail=""
    device_failure_stage=""
    if [[ -s "$device_report" ]]; then
      device_failure_detail="$(python3 - "$device_report" <<'PY'
import json,sys
try:
    p=json.load(open(sys.argv[1],encoding="utf-8"))
    fields=[]
    for key in ("failure_stage","current_step","last_successful_step","failure_class","failure_message"):
        value=p.get(key)
        if value:
            fields.append(f"{key}={str(value).replace(chr(10),' ').replace(chr(13),' ')[:800]}")
    print("; ".join(fields))
except Exception:
    print("device_failure_report_unreadable")
PY
)"
      device_failure_stage="$(python3 - "$device_report" <<'PY'
import json,sys
try: print(json.load(open(sys.argv[1],encoding="utf-8")).get("failure_stage", ""))
except Exception: print("")
PY
)"
    fi
    if [[ -s "$device_report" ]]; then
      screenshots_present="$(python3 - "$device_report" "$RAW_CASE_DIR/$initial_name" "$RAW_CASE_DIR/$swipe_name" "$RAW_CASE_DIR/$failure_name" <<'PY'
import json, pathlib, sys
report_path, initial_path, swipe_path, failure_path = map(pathlib.Path, sys.argv[1:])
try:
    report=json.loads(report_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError):
    print("false"); raise SystemExit
screens=report.get("screenshots") or {}
status=str(report.get("status","")).upper()
required=[initial_path]
if status=="PASS" or screens.get("after_swipe"):
    required.append(swipe_path)
if status=="FAIL":
    required.append(failure_path)
print("true" if all(path.is_file() and path.stat().st_size for path in required) else "false")
PY
)"
    fi

    reason=""
    case_status="PASS"
    if [[ "$instrumentation_rc" -ne 0 ]]; then
      case_status="FAIL"
      reason="INSTRUMENTATION_EXIT_$instrumentation_rc"
    elif grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)|shortMsg=Process crashed' "$combined"; then
      case_status="FAIL"
      reason="INSTRUMENTATION_FAILURE_MARKER"
    elif ! grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \(1 test' "$combined"; then
      case_status="FAIL"
      reason="INSTRUMENTATION_SUCCESS_MARKER_MISSING"
    elif [[ "$report_present" != true ]]; then
      case_status="FAIL"
      reason="DEVICE_CASE_REPORT_MISSING_AFTER_SUCCESSFUL_COMMAND"
    elif [[ "$report_status" != "PASS" ]]; then
      case_status="FAIL"
      reason="DEVICE_CASE_STATUS_$report_status"
    elif [[ "$screenshots_present" != true ]]; then
      case_status="FAIL"
      reason="INITIAL_OR_AFTER_SWIPE_SCREENSHOT_MISSING"
    elif [[ "$profile_settings_match" != true ]]; then
      case_status="FAIL"
      reason="PROFILE_SETTINGS_DID_NOT_MATCH_REQUEST"
    fi
    if [[ "$native_viewport_pass" != true ]]; then
      case_status="FAIL"
      reason="FAIL_CAPTURE_PIPELINE_NATIVE_VIEWPORT"
    elif [[ "$device_failure_stage" == "VERIFY_INITIAL_SCREENSHOT_VIEWPORT_PIPELINE" ]]; then
      case_status="FAIL"
      reason="FAIL_CAPTURE_PIPELINE_SCREENSHOT_CHANNELS"
    elif [[ "$device_failure_stage" == "SWIPE_TO_NEXT_REAL_REPOSITORY_CATCH" ]]; then
      case_status="FAIL"
      reason="PAGER_SINGLE_PAGE_INTERACTION_FAILED"
    fi
    if [[ -n "$device_failure_detail" ]]; then
      if [[ -n "$reason" ]]; then reason+="; $device_failure_detail"; else reason="$device_failure_detail"; fi
    fi

    if [[ "$case_status" == "FAIL" ]]; then
      all_ok=0
      {
        echo "profile=$profile"
        echo "variant=$variant"
        echo "requested_display_px=$requested"
        echo "requested_density_dpi=160"
        echo "requested_font_scale=$font_scale"
        echo "actual_wm_size=$actual_wm_size"
        echo "actual_density=$actual_density"
        echo "actual_font_scale=$actual_font"
        echo "animator_duration_scale=$actual_animator_scale"
        echo "transition_animation_scale=$actual_transition_scale"
        echo "window_animation_scale=$actual_window_scale"
        echo "api=$device_api"
        echo "fingerprint=$device_fingerprint"
        echo "adb_serial=$selected_serial"
        echo "app_package=$YUJIAN_APP_PACKAGE"
        echo "instrumentation_target=$YUJIAN_INSTRUMENTATION_TARGET"
        echo "reason=$reason"
      } > "$case_dir/device_parameters.txt"
      "${ADB[@]}" devices -l > "$case_dir/adb_devices_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell getprop > "$case_dir/getprop_after_failure.txt" 2>&1 || true
      "${ADB[@]}" shell wm size > "$case_dir/wm_size_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell wm density > "$case_dir/wm_density_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell settings get system font_scale > "$case_dir/font_scale_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell settings get global animator_duration_scale > "$case_dir/animator_duration_scale_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell settings get global transition_animation_scale > "$case_dir/transition_animation_scale_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell settings get global window_animation_scale > "$case_dir/window_animation_scale_after_failure.log" 2>&1 || true
      "${ADB[@]}" shell dumpsys window windows > "$case_dir/window_after_failure.txt" 2>&1 || true
      "${ADB[@]}" logcat -d -v threadtime -t 4000 > "$case_dir/logcat_after_failure.txt" 2>&1 || true
      "${ADB[@]}" exec-out screencap -p > "$case_dir/screenshot_after_failure.png" 2> "$case_dir/screenshot_capture.stderr.log" || true
      cat "$case_dir/logcat_after_failure.txt" >> "$LOG_DIR/${case_key}_failure_logcat.txt" 2>/dev/null || true
      "${ADB[@]}" pull "$REMOTE_DIR" "$RAW_DIR" >> "$pull_log" 2>&1 || true
    fi
    write_case_record "$profile" "$variant_token" "$case_status" "$instrumentation_rc" "$reason" "$report_present" "$screenshots_present" "$profile_settings_match" "$case_elapsed_seconds"
    printf 'CASE %s %s status=%s rc=%s report=%s screenshots=%s reason=%s\n' \
      "$profile" "$variant" "$case_status" "$instrumentation_rc" "$report_present" "$screenshots_present" "$reason"
  done
done

final_pull_log="$LOG_DIR/pull_runtime_matrix.log"
if [[ -n "$selected_serial" ]]; then
  "${ADB[@]}" pull "$REMOTE_DIR" "$RAW_DIR" > "$final_pull_log" 2>&1 || all_ok=0
else
  echo "No unique online ADB device selected; remote pull not run." > "$final_pull_log"
  all_ok=0
fi

mkdir -p "$MATRIX_DIR/screenshots/sources"
for expected_screen in \
  M04_360x640_initial.png M04_360x640_after_swipe.png \
  M07_390x844_initial.png M07_390x844_after_swipe.png \
  M10_430x932_initial.png M10_430x932_after_swipe.png; do
  profile="${expected_screen%%_*}"
  case_key="${profile}_adaptive_v2"
  native_pass="$(python3 - "$LOG_DIR/display/$profile/native_display_validation.json" <<'PY'
import json, pathlib, sys
try: print(str(json.loads(pathlib.Path(sys.argv[1]).read_text()).get("native_viewport_pass",False)).lower())
except Exception: print("false")
PY
)"
  case_pass="$(python3 - "$EXEC_DIR/${profile}-adaptive_v2.json" <<'PY'
import json, pathlib, sys
try: print(str(json.loads(pathlib.Path(sys.argv[1]).read_text()).get("status","")).upper()=="PASS")
except Exception: print("false")
PY
)"
  stem="${expected_screen%.png}"
  if [[ -s "$RAW_CASE_DIR/$expected_screen" && "$native_pass" == true && "$case_pass" == True && \
        -s "$RAW_CASE_DIR/${stem}_adb.png" && -s "$RAW_CASE_DIR/${stem}_pixelcopy.png" ]]; then
    cp "$RAW_CASE_DIR/$expected_screen" "$MATRIX_DIR/screenshots/$expected_screen"
    cp "$RAW_CASE_DIR/${stem}_adb.png" "$MATRIX_DIR/screenshots/sources/"
    cp "$RAW_CASE_DIR/${stem}_pixelcopy.png" "$MATRIX_DIR/screenshots/sources/"
  fi
done

restore_device
restore_rc=$?
trap - EXIT INT TERM
if [[ "$restore_rc" -ne 0 ]]; then
  all_ok=0
  printf 'DEVICE_SETTINGS_RESTORE=FAIL\n' | tee -a "$LOG_DIR/restore_device.log"
else
  printf 'DEVICE_SETTINGS_RESTORE=PASS\n' | tee -a "$LOG_DIR/restore_device.log"
fi

aggregate_rc=0
if [[ -n "$profile_filter" || -n "$variant_filter" ]]; then
  report_stem="${YUJIAN_NH01_REPORT_STEM:-NH01_REPRESENTATIVE_DEVICE_REPORT}"
  expected_case_count=$((${#profiles[@]} * ${#variants[@]}))
  python3 - "$EXEC_DIR" "$profile_filter" "$variant_filter" "$YUJIAN_EVIDENCE_DIR/${report_stem}.md" "$YUJIAN_EVIDENCE_DIR/${report_stem}.json" "$expected_case_count" "$BUILD_SHA" "$selected_serial" "$device_api" <<'PY'
import json, pathlib, sys
execution_dir=pathlib.Path(sys.argv[1])
profiles=sys.argv[2].split(",") if sys.argv[2] else []
variants=[v.lower() for v in sys.argv[3].split(",") if v]
md_path,json_path=map(pathlib.Path,sys.argv[4:6])
expected_cases=int(sys.argv[6])
source_sha,serial,api=sys.argv[7:10]
rows=[]
for profile in profiles:
  for variant in variants:
    path=execution_dir/f"{profile}-{variant}.json"
    data=json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {"status":"MISSING","reason":"execution record missing"}
    rows.append({"profile":profile,"variant":variant,"status":data.get("status","MISSING"),
                 "duration_seconds":data.get("duration_seconds"),"reason":data.get("reason",""),
                 "screenshot_evidence":data.get("required_screenshots_present"),
                 "settings_match":data.get("profile_settings_match"),"execution":data})
result={"schema":"NH01_REPRESENTATIVE_DEVICE_MATRIX_REPORT_V1","source_sha":source_sha,"device_api":api,
        "adb_serial":serial,"status":"PASS" if len(rows)==expected_cases and all(r["status"]=="PASS" for r in rows) else "FAIL",
        "expected_cases":expected_cases,"executed_cases":len(rows),"cases":rows}
json_path.write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
lines=["# NH01 Representative-Device API 28 Validation","",
       f"- Source SHA: `{source_sha}`",f"- API: {api}",f"- ADB serial: {serial}",
       "- Profiles: " + ", ".join(profiles), "- Variants: " + ", ".join(variants),
       f"- Result: **{result['status']}**","",
       "| Profile | Mode | Duration (s) | Screenshot evidence | Device settings | Status | Reason |",
       "|---|---|---:|---|---|---|---|"]
for row in rows:
  lines.append(f"| {row['profile']} | {row['variant']} | {row['duration_seconds']} | {row['screenshot_evidence']} | {row['settings_match']} | {row['status']} | {row['reason']} |")
md_path.write_text("\n".join(lines)+"\n",encoding="utf-8")
if result["status"]!="PASS": raise SystemExit(1)
PY
  aggregate_rc=$?
else
  python3 scripts/android_runtime/aggregate_nh01_adaptive_matrix.py \
    --raw-dir "$RAW_CASE_DIR" \
    --execution-dir "$EXEC_DIR" \
    --output-dir "$YUJIAN_RUNTIME_EVIDENCE" \
    --source-sha "$BUILD_SHA" \
    --run-id "${GITHUB_RUN_ID:-local}" \
    --run-url "${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-pan277942135/Yujian_App}/actions/runs/${GITHUB_RUN_ID:-local}" \
    --contract design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V2.md || aggregate_rc=$?
fi
if [[ "$aggregate_rc" -ne 0 ]]; then all_ok=0; fi

python3 - "$MATRIX_DIR/NH01_API28_NATIVE_VIEWPORT_AND_PAGER_FIX_REPORT.md" "$LOG_DIR/display" "$EXEC_DIR" "$RAW_CASE_DIR" "$MATRIX_DIR/screenshots" "$BUILD_SHA" "$AVD_NAME" <<'PY'
import json
import pathlib
import sys

report_path, display_root, execution_root, raw_root, screenshot_root = map(pathlib.Path, sys.argv[1:6])
source_sha, avd_name = sys.argv[6:8]
profiles = [
    ("M04", "360x640", "M04_360x640_initial.png", "M04_360x640_after_swipe.png"),
    ("M07", "390x844", "M07_390x844_initial.png", "M07_390x844_after_swipe.png"),
    ("M10", "430x932", "M10_430x932_initial.png", "M10_430x932_after_swipe.png"),
]
def load(path):
    try: return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError): return {}

lines = [
    "# NH01 API 28 Native Viewport and Pager Fix Report", "",
    f"- Source SHA: `{source_sha}`", f"- AVD: `{avd_name}`", "- API: 28, Pixel 2 x86_64 System Image",
    "- Display method: AVD hardware LCD width / height / density configured before boot; no runtime `wm size` override.",
    "- Pager contract: fling is explicitly capped at one page; test swipe is one 600 ms real touch gesture.", "",
    "## Root causes and corrections", "",
    "- The earlier M07/M10 runs changed `wm size` on a 1080×1920 AVD. That changed the logical display but left the emulator's physical surface at the Pixel 2 dimensions; the retained 96/97 px black edge also appeared on Launcher after the app exited. Each profile now has its own versioned `hw.lcd.width`, `hw.lcd.height`, and `hw.lcd.density` file applied before boot, with `-skin`/`-dpi-device` on profile restarts. Physical size, override absence, density, and launcher screencap dimensions are gates.",
    "- The earlier swipe used Compose test's default `swipeLeft()` gesture and settled at page 2. The production pager now uses `PagerSnapDistance.atMost(1)`. The test injects one slow 72%→28% Pager-width touch over 600 ms, captures the settled state and transition history, and preserves the second-state screenshot before checking the final record ID.", "",
    "| Profile | Requested physical viewport | WM physical | WM override | Density | Launcher screencap | Native viewport | Case | Initial | After swipe |",
    "|---|---:|---:|---:|---:|---:|---|---|---|---|",
]
for profile, size, initial, after in profiles:
    display = load(display_root / profile / "native_display_validation.json")
    case = load(execution_root / f"{profile}-adaptive_v2.json")
    status = str(case.get("status", "MISSING"))
    initial_file = screenshot_root / initial
    after_file = screenshot_root / after
    initial_state = f"[PNG](screenshots/{initial})" if initial_file.is_file() else "MISSING / NOT VALIDATED"
    after_state = f"[PNG](screenshots/{after})" if after_file.is_file() else "MISSING / NOT VALIDATED"
    lines.append("| {} | {} | {} | {} | {} dpi | {} | {} | {} | {} | {} |".format(
        profile, size, display.get("wm_physical_size", "unknown"), display.get("wm_override_size") or "none",
        display.get("wm_physical_density_dpi", "unknown"), display.get("launcher_screencap_dimensions", "unknown"),
        "PASS" if display.get("native_viewport_pass") else "FAIL",
        status, initial_state, after_state))
    case_report = load(raw_root / f"{profile}-adaptive_v2.json")
    gesture = case_report.get("pager_gesture", {})
    if gesture:
        lines.extend(["", f"### {profile} Pager trace", "", "```json", json.dumps(gesture, ensure_ascii=False, indent=2), "```"])
lines.extend(["", "## Native display and accessibility records", ""])
for profile, _, _, _ in profiles:
    native = load(display_root / profile / "native_display_validation.json")
    lines.extend([
        f"### {profile}", "",
        f"AVD config: [`nh01_api28_{profile}.ini`](avd_profiles/nh01_api28_{profile}.ini)", "",
        "```json", json.dumps(native, ensure_ascii=False, indent=2), "```",
    ])
lines.extend(["", "## Initial view comparison", ""])
for profile, _, initial, _ in profiles:
    if (screenshot_root / initial).is_file():
        lines.append(f"### {profile}\n\n![{profile} initial view](screenshots/{initial})")
    else:
        lines.append(f"### {profile}\n\n`MISSING / NOT VALIDATED` — {initial}")
lines.extend(["", "## After-swipe view comparison", ""])
for profile, _, _, after in profiles:
    if (screenshot_root / after).is_file():
        lines.append(f"### {profile}\n\n![{profile} after swipe](screenshots/{after})")
    else:
        lines.append(f"### {profile}\n\n`MISSING / NOT VALIDATED` — {after}")
lines.extend(["", "## Screenshot source files", "", "The app-side report records UiAutomation, ADB `screencap -p`, and Window PixelCopy dimensions and pixel-comparison diagnostics for the initial and post-swipe frames. Full raw source PNGs are retained in the case evidence directory; only cases with a passing native viewport and complete test case enter the formal screenshot directory.", ""])
report_path.write_text("\n".join(lines), encoding="utf-8")
PY

MATRIX_STATUS="PASS"
if [[ "$all_ok" -ne 1 ]]; then MATRIX_STATUS="FAIL"; fi
MATRIX_STATUS="$MATRIX_STATUS" BUILD_SHA="$BUILD_SHA" SELECTED_SERIAL="$selected_serial" \
  DEVICE_API="$device_api" DEVICE_FINGERPRINT="$device_fingerprint" SETUP_REASON="$matrix_setup_reason" \
  MATRIX_ELAPSED_SECONDS="$(( $(date +%s) - matrix_started_epoch ))" PROFILE_FILTER="$profile_filter" VARIANT_FILTER="$variant_filter" \
  python3 - "$MATRIX_DIR/execution_summary.json" "$EXEC_DIR" <<'PY'
import json, os, sys
from pathlib import Path
root=Path(sys.argv[2])
records=[]
for path in sorted(root.glob("*.json")):
    try: records.append(json.loads(path.read_text(encoding="utf-8")))
    except (OSError,json.JSONDecodeError): records.append({"status":"INVALID","profile_id":path.stem})
payload={"schema":"NH01_ADAPTIVE_MATRIX_EXECUTION_SUMMARY_V1","status":os.environ["MATRIX_STATUS"],
 "source_sha":os.environ["BUILD_SHA"],"adb_serial":os.environ.get("SELECTED_SERIAL") or None,
 "device_api":os.environ.get("DEVICE_API"),"device_fingerprint":os.environ.get("DEVICE_FINGERPRINT"),
 "setup_reason":os.environ.get("SETUP_REASON"),"profile_filter":os.environ.get("PROFILE_FILTER") or None,
 "variant_filter":os.environ.get("VARIANT_FILTER") or None,
 "matrix_elapsed_seconds":int(os.environ.get("MATRIX_ELAPSED_SECONDS","0")),"execution_records":records}
Path(sys.argv[1]).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY

if [[ "$all_ok" -ne 1 ]]; then
  printf '%s\n' "NH01_ADAPTIVE_DEVICE_MATRIX=FAIL logs=$MATRIX_DIR/logs executions=$EXEC_DIR" >&2
  exit 1
fi
printf '%s\n' "NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=$BUILD_SHA"
