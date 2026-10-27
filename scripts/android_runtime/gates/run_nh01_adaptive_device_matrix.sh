#!/usr/bin/env bash
set -uo pipefail

: "${YUJIAN_ADB_BIN:?YUJIAN_ADB_BIN is required}"
: "${YUJIAN_APP_PACKAGE:?YUJIAN_APP_PACKAGE is required}"
: "${YUJIAN_INSTRUMENTATION_TARGET:?YUJIAN_INSTRUMENTATION_TARGET is required}"
: "${YUJIAN_EVIDENCE_DIR:?YUJIAN_EVIDENCE_DIR is required}"
: "${BUILD_SHA:?BUILD_SHA is required}"

readonly TEST_CLASS="com.yujian.ai.ui.home.GeminiHomeDeviceMatrixTest#capturesActualWindowForScenario"
readonly APP_APK="${YUJIAN_APP_APK:-build/runtime-apks/app-debug.apk}"
readonly TEST_APK="${YUJIAN_TEST_APK:-build/runtime-apks/app-debug-androidTest.apk}"
readonly RAW_DIR="$YUJIAN_EVIDENCE_DIR/nh01-adaptive-raw"
readonly RAW_CASE_DIR="$RAW_DIR/nh01-adaptive-visual-balance-v2"
readonly MATRIX_DIR="$YUJIAN_EVIDENCE_DIR/NH01_RUNTIME_MATRIX_V1"
readonly EXEC_DIR="$MATRIX_DIR/executions"
readonly LOG_DIR="$MATRIX_DIR/logs"
readonly REMOTE_DIR="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/nh01-adaptive-visual-balance-v2"

rm -rf "$RAW_DIR" "$MATRIX_DIR"
mkdir -p "$RAW_CASE_DIR" "$EXEC_DIR" "$LOG_DIR/diagnostics"

profiles=(
  "M01 320 480 1.0" "M02 320 480 1.3" "M03 320 480 1.5"
  "M04 360 640 1.0" "M05 360 640 1.3" "M06 360 640 1.5"
  "M07 390 844 1.0" "M08 390 844 1.3" "M09 390 844 1.5"
  "M10 430 932 1.0" "M11 430 932 1.3" "M12 430 932 1.5"
  "SUP-W480-H840-F10 480 840 1.0" "SUP-W480-H840-F13 480 840 1.3" "SUP-W480-H840-F15 480 840 1.5"
  "SUP-W412-H915-F10 412 915 1.0" "SUP-W412-H915-F13 412 915 1.3" "SUP-W412-H915-F15 412 915 1.5"
  "SUP-W375-H720-F10 375 720 1.0" "SUP-W375-H720-F13 375 720 1.3" "SUP-W375-H720-F15 375 720 1.5"
  "BOUNDARY-SAFE459-F10 360 SAFE459 1.0" "BOUNDARY-SAFE459-F13 360 SAFE459 1.3" "BOUNDARY-SAFE459-F15 360 SAFE459 1.5"
  "BOUNDARY-SAFE460-F10 360 SAFE460 1.0" "BOUNDARY-SAFE460-F13 360 SAFE460 1.3" "BOUNDARY-SAFE460-F15 360 SAFE460 1.5"
  "BOUNDARY-SAFE499-F10 360 SAFE499 1.0" "BOUNDARY-SAFE499-F13 360 SAFE499 1.3" "BOUNDARY-SAFE499-F15 360 SAFE499 1.5"
  "BOUNDARY-SAFE500-F10 360 SAFE500 1.0" "BOUNDARY-SAFE500-F13 360 SAFE500 1.3" "BOUNDARY-SAFE500-F15 360 SAFE500 1.5"
  "BOUNDARY-SAFE699-F10 360 SAFE699 1.0" "BOUNDARY-SAFE699-F13 360 SAFE699 1.3" "BOUNDARY-SAFE699-F15 360 SAFE699 1.5"
  "BOUNDARY-SAFE700-F10 360 SAFE700 1.0" "BOUNDARY-SAFE700-F13 360 SAFE700 1.3" "BOUNDARY-SAFE700-F15 360 SAFE700 1.5"
  "BOUNDARY-SAFE839-F10 360 SAFE839 1.0" "BOUNDARY-SAFE839-F13 360 SAFE839 1.3" "BOUNDARY-SAFE839-F15 360 SAFE839 1.5"
  "BOUNDARY-SAFE840-F10 360 SAFE840 1.0" "BOUNDARY-SAFE840-F13 360 SAFE840 1.3" "BOUNDARY-SAFE840-F15 360 SAFE840 1.5"
  "SUP-SINGLE-M01 320 480 1.0 single_record"
  "SUP-IMAGEFAIL-M01 320 480 1.0 image_failure"
  "SUP-THREE-M03 390 844 1.0 three_unique_catches"
)
variants=("ADAPTIVE_V2" "V3_BASELINE")
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

write_case_record() {
  local profile="$1" variant="$2" status="$3" exit_code="$4" reason="$5"
  local report_present="$6" screenshots_present="$7" settings_match="$8" output="$EXEC_DIR/${profile}-${variant}.json"
  PROFILE_ID="$profile" LAYOUT_VARIANT="$variant" CASE_STATUS="$status" CASE_EXIT_CODE="$exit_code" \
    CASE_REASON="$reason" REPORT_PRESENT="$report_present" SCREENSHOTS_PRESENT="$screenshots_present" \
    SETTINGS_MATCH="$settings_match" SOURCE_SHA="$BUILD_SHA" APP_PACKAGE="$YUJIAN_APP_PACKAGE" \
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
  read -r profile _width _height _font _scenario <<< "$row"
  if [[ "$profile" =~ ^M[0-9]{2}$ ]]; then profile_variants=("${variants[@]}"); else profile_variants=("ADAPTIVE_V2"); fi
  for variant in "${profile_variants[@]}"; do
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

BUILD_SHA="$BUILD_SHA" YUJIAN_ADB_BIN="$YUJIAN_ADB_BIN" YUJIAN_APP_PACKAGE="$YUJIAN_APP_PACKAGE" \
  YUJIAN_INSTRUMENTATION_TARGET="$YUJIAN_INSTRUMENTATION_TARGET" APP_APK_VALUE="$APP_APK" TEST_APK_VALUE="$TEST_APK" \
  SELECTED_SERIAL="$selected_serial" DEVICE_API="$device_api" DEVICE_FINGERPRINT="$device_fingerprint" \
  WM_SIZE_BEFORE="$wm_size_before" WM_DENSITY_BEFORE="$wm_density_before" INITIAL_FONT_SCALE="$initial_font" \
  INITIAL_ANIMATOR_SCALE="$initial_animator_scale" INITIAL_TRANSITION_SCALE="$initial_transition_scale" INITIAL_WINDOW_SCALE="$initial_window_scale" \
  INITIAL_SIZE_OVERRIDE="$initial_size_override" INITIAL_DENSITY_OVERRIDE="$initial_density_override" \
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
    expected_size_value="${initial_size_override:-$wm_size_before}"
    expected_density_value="${initial_density_override:-$wm_density_before}"
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
  for setting in animator_duration_scale transition_animation_scale window_animation_scale; do
    "${ADB[@]}" shell settings put global "$setting" 0.0 >> "$LOG_DIR/disable_system_animations.log" 2>&1 || {
      record_setup_failure "COULD_NOT_DISABLE_SYSTEM_ANIMATION_$setting"
    }
  done
fi

for row in "${profiles[@]}"; do
  read -r profile width height font_scale scenario <<< "$row"
  scenario="${scenario:-two_unique_catches}"
  expected_safe_height=""
  if [[ "$height" == SAFE* ]]; then
    expected_safe_height="${height#SAFE}"
    height=""
    inset_source="$RAW_CASE_DIR/M04-adaptive_v2.json"
    if [[ -s "$inset_source" ]]; then
      inset_total="$(python3 - "$inset_source" <<'PY'
import json, sys
from pathlib import Path
payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
insets = payload["safe_insets_dp"]
print(float(insets["top"]) + float(insets["bottom"]))
PY
      )"
      height="$(python3 - "$expected_safe_height" "$inset_total" <<'PY'
import sys
print(int(round(float(sys.argv[1]) + float(sys.argv[2]))))
PY
      )"
    else
      record_setup_failure "MISSING_M04_SAFE_INSET_CALIBRATION_FOR_${profile}"
      height="480"
    fi
  fi
  if [[ "$profile" =~ ^M[0-9]{2}$ ]]; then profile_variants=("${variants[@]}"); else profile_variants=("ADAPTIVE_V2"); fi
  requested="${width}x${height}"
  size_log="$LOG_DIR/${profile}_wm_size.log"
  density_log="$LOG_DIR/${profile}_wm_density.log"
  font_log="$LOG_DIR/${profile}_font_scale.log"
  size_rc=0; density_rc=0; font_rc=0
  if [[ -n "$selected_serial" ]]; then
    "${ADB[@]}" shell wm size "$requested" > "$size_log" 2>&1 || size_rc=$?
    "${ADB[@]}" shell wm density 160 > "$density_log" 2>&1 || density_rc=$?
    "${ADB[@]}" shell settings put system font_scale "$font_scale" > "$font_log" 2>&1 || font_rc=$?
    sleep 2
  else
    echo "no unique online ADB device" > "$size_log"
    echo "no unique online ADB device" > "$density_log"
    echo "no unique online ADB device" > "$font_log"
    size_rc=1; density_rc=1; font_rc=1
  fi

  actual_wm_size="unknown"
  actual_density="unknown"
  actual_font="unknown"
  actual_animator_scale="unknown"
  actual_transition_scale="unknown"
  actual_window_scale="unknown"
  if [[ -n "$selected_serial" ]]; then
    actual_wm_size="$("${ADB[@]}" shell wm size 2>&1 | sed -n 's/^Override size: //p; s/^Physical size: //p' | tail -n 1 | tr -d '\r')"
    actual_density="$("${ADB[@]}" shell wm density 2>&1 | sed -n 's/^Override density: //p; s/^Physical density: //p' | tail -n 1 | tr -d '\r')"
    actual_font="$("${ADB[@]}" shell settings get system font_scale 2>&1 | tr -d '\r')"
    actual_animator_scale="$("${ADB[@]}" shell settings get global animator_duration_scale 2>&1 | tr -d '\r')"
    actual_transition_scale="$("${ADB[@]}" shell settings get global transition_animation_scale 2>&1 | tr -d '\r')"
    actual_window_scale="$("${ADB[@]}" shell settings get global window_animation_scale 2>&1 | tr -d '\r')"
  fi
  profile_settings_match=true
  [[ "$size_rc" -eq 0 && "$density_rc" -eq 0 && "$font_rc" -eq 0 ]] || profile_settings_match=false
  [[ "$actual_wm_size" == "$requested" ]] || profile_settings_match=false
  [[ "$actual_density" == "160" ]] || profile_settings_match=false
  [[ "$actual_font" == "$font_scale" ]] || profile_settings_match=false
  [[ "$actual_animator_scale" == "0.0" && "$actual_transition_scale" == "0.0" && "$actual_window_scale" == "0.0" ]] || profile_settings_match=false
  python3 - "$LOG_DIR/${profile}_device_settings.json" "$profile" "$requested" "$font_scale" "$actual_wm_size" "$actual_density" "$actual_font" "$actual_animator_scale" "$actual_transition_scale" "$actual_window_scale" "$size_rc" "$density_rc" "$font_rc" <<'PY'
import json, sys
from pathlib import Path
p={"profile_id":sys.argv[2],"requested_size_px":sys.argv[3],"requested_density_dpi":160,"requested_font_scale":float(sys.argv[4]),
   "actual_wm_size":sys.argv[5],"actual_wm_density":sys.argv[6],"actual_font_scale":sys.argv[7],
   "android_animation_scales":{"animator_duration_scale":sys.argv[8],"transition_animation_scale":sys.argv[9],"window_animation_scale":sys.argv[10]},
   "wm_size_exit":int(sys.argv[11]),"wm_density_exit":int(sys.argv[12]),"font_scale_exit":int(sys.argv[13])}
Path(sys.argv[1]).write_text(json.dumps(p,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY

  for variant in "${profile_variants[@]}"; do
    variant_token="$(tr '[:upper:]' '[:lower:]' <<< "$variant" | tr -d '\r\n')"
    case_key="${profile}_${variant_token}"
    case_dir="$LOG_DIR/diagnostics/$case_key"
    mkdir -p "$case_dir"
    stdout="$LOG_DIR/${case_key}_stdout.log"
    stderr="$LOG_DIR/${case_key}_stderr.log"
    combined="$LOG_DIR/${case_key}.log"
    report_name="$profile-${variant_token}.json"
    initial_name="$profile-${variant_token}.png"
    swipe_name="$profile-${variant_token}-after-swipe.png"
    fallback_name="$profile-${variant_token}-fallback.png"
    if [[ "$matrix_setup_ok" -ne 1 ]]; then
      reason="MATRIX_SETUP_BLOCKED: ${matrix_setup_reason:-UNSPECIFIED}"
      write_case_record "$profile" "$variant_token" "MISSING" "null" "$reason" false false "$profile_settings_match"
      continue
    fi

    "${ADB[@]}" logcat -c > "$case_dir/logcat_clear.stdout.log" 2> "$case_dir/logcat_clear.stderr.log" || true
    "${ADB[@]}" shell am force-stop "$YUJIAN_APP_PACKAGE" > "$case_dir/force_stop.stdout.log" 2> "$case_dir/force_stop.stderr.log" || true
    write_case_record "$profile" "$variant_token" "RUNNING" "null" "INSTRUMENTATION_STARTED" false false "$profile_settings_match"
    instrumentation_args=(
      -e class "$TEST_CLASS"
      -e profile_id "$profile"
      -e layout_variant "$variant"
      -e font_scale "$font_scale"
      -e expected_display_px "$requested"
      -e scenario "$scenario"
      -e source_sha "$BUILD_SHA"
    )
    if [[ -n "$expected_safe_height" ]]; then
      instrumentation_args+=(-e expected_safe_height_dp "$expected_safe_height")
    fi
    if command -v timeout >/dev/null 2>&1; then
      timeout 300s "${ADB[@]}" shell am instrument -w -r \
        "${instrumentation_args[@]}" "$YUJIAN_INSTRUMENTATION_TARGET" > "$stdout" 2> "$stderr"
    else
      "${ADB[@]}" shell am instrument -w -r \
        "${instrumentation_args[@]}" "$YUJIAN_INSTRUMENTATION_TARGET" > "$stdout" 2> "$stderr"
    fi
    instrumentation_rc=$?
    {
      echo "COMMAND=adb shell am instrument -w -r -e class $TEST_CLASS -e profile_id $profile -e layout_variant $variant -e font_scale $font_scale -e expected_display_px $requested -e scenario $scenario -e expected_safe_height_dp ${expected_safe_height:-UNSET} -e source_sha $BUILD_SHA $YUJIAN_INSTRUMENTATION_TARGET"
      echo "EXIT_CODE=$instrumentation_rc"
      echo "--- stdout ---"
      cat "$stdout"
      echo "--- stderr ---"
      cat "$stderr"
    } > "$combined"

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
    fi
    if [[ -s "$RAW_CASE_DIR/$initial_name" ]]; then
      case "$scenario" in
        two_unique_catches)
          [[ -s "$RAW_CASE_DIR/$swipe_name" ]] && screenshots_present=true
          ;;
        single_record)
          screenshots_present=true
          ;;
        image_failure)
          [[ -s "$RAW_CASE_DIR/$fallback_name" ]] && screenshots_present=true
          ;;
      esac
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
      reason="REQUIRED_SCENARIO_SCREENSHOT_MISSING"
    elif [[ "$profile_settings_match" != true ]]; then
      case_status="FAIL"
      reason="PROFILE_SETTINGS_DID_NOT_MATCH_REQUEST"
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
    write_case_record "$profile" "$variant_token" "$case_status" "$instrumentation_rc" "$reason" "$report_present" "$screenshots_present" "$profile_settings_match"
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
python3 scripts/android_runtime/aggregate_nh01_adaptive_matrix.py \
  --raw-dir "$RAW_CASE_DIR" \
  --execution-dir "$EXEC_DIR" \
  --output-dir "$YUJIAN_EVIDENCE_DIR" \
  --source-sha "$BUILD_SHA" \
  --run-id "${GITHUB_RUN_ID:-local}" \
  --run-url "${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-pan277942135/Yujian_App}/actions/runs/${GITHUB_RUN_ID:-local}" \
  --contract design/qa/NH01_ADAPTIVE_VISUAL_BALANCE_V2.md || aggregate_rc=$?
if [[ "$aggregate_rc" -ne 0 ]]; then all_ok=0; fi

supplemental_rc=0
python3 scripts/android_runtime/validate_nh01_supplemental_matrix.py \
  --raw-dir "$RAW_CASE_DIR" \
  --execution-dir "$EXEC_DIR" \
  --output-dir "$YUJIAN_EVIDENCE_DIR" \
  --source-sha "$BUILD_SHA" \
  --run-id "${GITHUB_RUN_ID:-local}" \
  --run-url "${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY:-pan277942135/Yujian_App}/actions/runs/${GITHUB_RUN_ID:-local}" \
  || supplemental_rc=$?
if [[ "$supplemental_rc" -ne 0 ]]; then all_ok=0; fi

MATRIX_STATUS="PASS"
if [[ "$all_ok" -ne 1 ]]; then MATRIX_STATUS="FAIL"; fi
MATRIX_STATUS="$MATRIX_STATUS" BUILD_SHA="$BUILD_SHA" SELECTED_SERIAL="$selected_serial" \
  DEVICE_API="$device_api" DEVICE_FINGERPRINT="$device_fingerprint" SETUP_REASON="$matrix_setup_reason" \
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
 "setup_reason":os.environ.get("SETUP_REASON"),"execution_records":records,
 "baseline_execution_count":24,"supplemental_matrix_execution_count":33,
 "scoped_state_execution_count":2,"total_execution_count":len(records)}
Path(sys.argv[1]).write_text(json.dumps(payload,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
PY

if [[ "$all_ok" -ne 1 ]]; then
  printf '%s\n' "NH01_ADAPTIVE_DEVICE_MATRIX=FAIL logs=$MATRIX_DIR/logs executions=$EXEC_DIR" >&2
  exit 1
fi
printf '%s\n' "NH01_ADAPTIVE_DEVICE_MATRIX=PASS source_sha=$BUILD_SHA"
