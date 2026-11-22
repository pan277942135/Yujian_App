#!/usr/bin/env bash
set -uo pipefail

evidence_dir="${EVIDENCE_DIR:-evidence/runtime/recognition-result-api36}"
apk_dir="${APK_DIR:-build/runtime-apks}"
source_sha="${SOURCE_SHA:-unknown}"
app_package="com.yujian.ai.uiv2"
test_package="com.yujian.ai.uiv2.test"
runner="androidx.test.runner.AndroidJUnitRunner"
result_classification="INFRA_BLOCKED"
failure_reason="SMOKE_NOT_STARTED"
instrumentation_attempted=false
test_started=false
adb_ready=false

mkdir -p "$evidence_dir/screenshots" "$evidence_dir/business-events"

finish() {
  local rc=$?
  set +e
  if command -v adb >/dev/null 2>&1 && [[ "$adb_ready" == true ]]; then
    adb shell 'pidof screenrecord >/dev/null 2>&1 && kill -INT $(pidof screenrecord)' \
      > "$evidence_dir/screenrecord-stop.log" 2>&1
    adb shell screencap -p /sdcard/Download/recognition-result-api36-post-smoke.png \
      > "$evidence_dir/screencap.log" 2>&1
    adb pull /sdcard/Download/recognition-result-api36.mp4 \
      "$evidence_dir/recognition-result-api36.mp4" \
      > "$evidence_dir/video-pull.log" 2>&1
    adb pull /sdcard/Download/recognition-result-api36-post-smoke.png \
      "$evidence_dir/screenshots/post-smoke.png" \
      > "$evidence_dir/screenshot-pull.log" 2>&1
    adb logcat -d -v threadtime > "$evidence_dir/logcat.txt" 2>&1
    adb exec-out run-as "$app_package" tar -C cache -cf - rr09-gold-transfer-production \
      > "$evidence_dir/business-events/test-cache.tar" 2> "$evidence_dir/business-events/pull.log"
    if [[ -s "$evidence_dir/business-events/test-cache.tar" ]]; then
      tar -xf "$evidence_dir/business-events/test-cache.tar" -C "$evidence_dir/business-events" \
        >> "$evidence_dir/business-events/pull.log" 2>&1
      rm -f "$evidence_dir/business-events/test-cache.tar"
    fi
    adb devices -l > "$evidence_dir/adb-devices-final.txt" 2>&1
  fi
  python3 - "$evidence_dir/runtime-result.json" "$source_sha" "$result_classification" \
    "$failure_reason" "$rc" "$test_started" "$instrumentation_attempted" "$adb_ready" <<'PY'
import json, pathlib, sys, time
path, source_sha, classification, reason, exit_code, test_started, instrumentation_attempted, adb_ready = sys.argv[1:]
pathlib.Path(path).write_text(json.dumps({
    "source_sha": source_sha,
    "api_level_expected": 36,
    "test": "RecognitionRevealProductionRuntimeTest#realNightCatchHandoffDrivesTheNativeResultReveal",
    "classification": classification,
    "failure_reason": reason,
    "instrumentation_started": test_started == "true",
    "instrumentation_attempted": instrumentation_attempted == "true",
    "adb_ready": adb_ready == "true",
    "exit_code": int(exit_code),
    "captured_at_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
}, indent=2) + "\n", encoding="utf-8")
PY
  find "$evidence_dir" -type f ! -name evidence-sha256sums.txt -print0 \
    | sort -z | xargs -0 -r sha256sum > "$evidence_dir/evidence-sha256sums.txt"
  printf 'API36_AUTO_SMOKE_CLASSIFICATION=%s\n' "$result_classification"
  exit "$rc"
}
trap finish EXIT

if ! timeout 30s adb wait-for-device; then
  failure_reason="ADB_WAIT_FOR_DEVICE_FAILED"
  exit 30
fi
adb_ready=true

boot_ready=false
for attempt in $(seq 1 300); do
  device_state="$(adb get-state 2>/dev/null | tr -d '\r' || true)"
  boot_complete="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$device_state" == "device" && "$boot_complete" == "1" ]]; then
    boot_ready=true
    break
  fi
  sleep 1
done
if [[ "$boot_ready" != true ]]; then
  adb devices -l > "$evidence_dir/adb-devices-boot-failure.txt" 2>&1
  adb shell getprop > "$evidence_dir/device-properties-boot-failure.txt" 2>&1
  adb logcat -d -v threadtime > "$evidence_dir/logcat-boot-failure.txt" 2>&1
  failure_reason="BOOT_COMPLETED_OR_ADB_READY_TIMEOUT"
  exit 31
fi

api_level="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
printf 'adb_state=%s\nsys.boot_completed=%s\napi_level=%s\n' \
  "$(adb get-state 2>/dev/null | tr -d '\r')" \
  "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" \
  "$api_level" > "$evidence_dir/device-readiness.txt"
adb devices -l > "$evidence_dir/adb-devices-ready.txt"
adb shell getprop > "$evidence_dir/device-properties.txt"
if [[ "$api_level" != "36" ]]; then
  failure_reason="API_LEVEL_MISMATCH:$api_level"
  exit 32
fi

if ! adb install -r "$apk_dir/app-debug.apk" > "$evidence_dir/app-install.log" 2>&1; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="EXACT_HEAD_APP_APK_INSTALL_FAILED"
  exit 40
fi
if ! adb install -r "$apk_dir/app-debug-androidTest.apk" > "$evidence_dir/test-apk-install.log" 2>&1; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="EXACT_HEAD_ANDROID_TEST_APK_INSTALL_FAILED"
  exit 41
fi

adb logcat -c
adb shell screenrecord --time-limit 180 /sdcard/Download/recognition-result-api36.mp4 \
  > "$evidence_dir/screenrecord.log" 2>&1 &
sleep 2
instrumentation_attempted=true
failure_reason="INSTRUMENTATION_DID_NOT_COMPLETE"
set +e
timeout 240s adb shell am instrument -w -r \
  -e class 'com.yujian.ai.RecognitionRevealProductionRuntimeTest#realNightCatchHandoffDrivesTheNativeResultReveal' \
  "$test_package/$runner" 2>&1 | tee "$evidence_dir/instrumentation.log"
instrument_status=${PIPESTATUS[0]}
set -e

if grep -Fq 'INSTRUMENTATION_STATUS: test=realNightCatchHandoffDrivesTheNativeResultReveal' \
  "$evidence_dir/instrumentation.log"; then
  test_started=true
fi
if [[ "$instrument_status" -eq 0 && "$test_started" == true ]] && \
  ! grep -q '^FAILURES!!!' "$evidence_dir/instrumentation.log"; then
  result_classification="AUTO_SMOKE_PASS"
  failure_reason="NONE"
  exit 0
fi
if [[ "$test_started" == true ]] || grep -Eiq \
  'FAILURES!!!|AssertionError|expected:.*but was:|MODEL_TIMEOUT|BUSINESS_LOGIC_FAILURE' \
  "$evidence_dir/instrumentation.log"; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  if [[ "$instrument_status" -eq 124 ]]; then
    failure_reason="PRODUCTION_AUTO_INSTRUMENTATION_TIMEOUT"
  else
    failure_reason="PRODUCTION_AUTO_INSTRUMENTATION_ASSERTION_OR_RUNTIME_FAILURE"
  fi
else
  result_classification="INFRA_BLOCKED"
  failure_reason="INSTRUMENTATION_DID_NOT_REACH_A_TEST_ASSERTION"
fi
exit 50
