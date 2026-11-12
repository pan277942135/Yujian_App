#!/usr/bin/env bash
# Run the exact-head DET_FISH v0.3 + Classifier v0.2 instrumentation on a real API 36 device.
# Usage: bash run_det_fish_v03_android_device_test.sh [evidence-output-dir]
set -Eeuo pipefail

ROOT="$(cd "$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
APP_APK="$ROOT/app-debug.apk"
TEST_APK="$ROOT/app-debug-androidTest.apk"
OUT="\${1:-$ROOT/device-evidence}"
APP_ID="com.yujian.ai.uiv2"
TEST_COMPONENT="com.yujian.ai.uiv2.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.yujian.ai.DetectorOrientationRetryRuntimeTest#caseBNightFlashRunsV03DetectorClassifierAndMatchesPythonCpuReference"
DEVICE_FILES="/sdcard/Android/data/$APP_ID/files/det-fish-v03-evidence"

if ! command -v adb >/dev/null 2>&1; then
  echo "BLOCKED: adb is not installed or not on PATH." >&2
  exit 2
fi
if [[ ! -s "$APP_APK" || ! -s "$TEST_APK" ]]; then
  echo "BLOCKED: app-debug.apk and app-debug-androidTest.apk must be beside this script." >&2
  exit 2
fi
if [[ -f "$ROOT/SHA256SUMS" ]]; then
  (cd "$ROOT" && sha256sum -c SHA256SUMS)
fi

ADB=(adb)
if [[ -n "\${ANDROID_SERIAL:-}" ]]; then
  ADB+=( -s "$ANDROID_SERIAL" )
fi
"\${ADB[@]}" wait-for-device
API="$("\${ADB[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
MODEL="$("\${ADB[@]}" shell getprop ro.product.model | tr -d '\r')"
MANUFACTURER="$("\${ADB[@]}" shell getprop ro.product.manufacturer | tr -d '\r')"
BOOT="$("\${ADB[@]}" shell getprop sys.boot_completed | tr -d '\r')"

if [[ "$API" != "36" || "$BOOT" != "1" ]]; then
  echo "BLOCKED: expected a boot-complete Android API 36 device; got api=$API boot=$BOOT model=$MODEL." >&2
  exit 2
fi
if [[ "$MODEL" != *"CMS-AL20"* && "\${ALLOW_NON_CMS_AL20:-0}" != "1" ]]; then
  echo "BLOCKED: connected device is '$MANUFACTURER $MODEL', not the requested HUAWEI CMS-AL20." >&2
  echo "Set ALLOW_NON_CMS_AL20=1 only if this run is intentionally on another physical API 36 device." >&2
  exit 2
fi

mkdir -p "$OUT"
{
  echo "device_manufacturer=$MANUFACTURER"
  echo "device_model=$MODEL"
  echo "android_api=$API"
  echo "source_sha=$(grep -m1 'build_sha' "$ROOT/apk_model_trace.json" | sed -E 's/.*: *"([^"]+)".*/\1/' || true)"
  echo "app_apk_sha256=$(sha256sum "$APP_APK" | awk '{print $1}')"
  echo "test_apk_sha256=$(sha256sum "$TEST_APK" | awk '{print $1}')"
  adb version
} > "$OUT/device-provenance.txt"

"\${ADB[@]}" install -r "$APP_APK"
"\${ADB[@]}" install -r "$TEST_APK"
"\${ADB[@]}" shell pm list instrumentation > "$OUT/installed-instrumentation.txt"
"\${ADB[@]}" shell pm path "$APP_ID" > "$OUT/installed-app-path.txt"
"\${ADB[@]}" logcat -c

set +e
"\${ADB[@]}" shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  "$TEST_COMPONENT" 2>&1 | tee "$OUT/instrumentation.log"
INSTRUMENTATION_PIPE_STATUS=( "\${PIPESTATUS[@]}" )
set -e
INSTRUMENTATION_EXIT="\${INSTRUMENTATION_PIPE_STATUS[0]:-1}"

"\${ADB[@]}" logcat -d -v threadtime > "$OUT/logcat-full.txt"
"\${ADB[@]}" shell dumpsys package "$APP_ID" > "$OUT/package-dump.txt" || true
"\${ADB[@]}" shell dumpsys meminfo "$APP_ID" > "$OUT/memory-after-test.txt" || true

mkdir -p "$OUT/android-app-files"
"\${ADB[@]}" pull "$DEVICE_FILES" "$OUT/android-app-files/" || {
  echo "WARNING: could not pull app-specific evidence directory: $DEVICE_FILES" | tee "$OUT/evidence-pull-warning.txt"
}

if [[ "$INSTRUMENTATION_EXIT" != "0" ]] || grep -q "FAILURES!!!" "$OUT/instrumentation.log" || \
   ! grep -Eq 'OK \([0-9]+ tests?\)' "$OUT/instrumentation.log"; then
  echo "FAIL: Android instrumentation did not report a clean JUnit pass." >&2
  exit 1
fi
if ! grep -q "DET_FISH_V03_REAL_INFERENCE" "$OUT/instrumentation.log" || \
   ! grep -q "DET_FISH_V03_EVIDENCE_JSON" "$OUT/instrumentation.log"; then
  echo "FAIL: expected real-inference evidence markers were not emitted." >&2
  exit 1
fi
if [[ ! -s "$OUT/android-app-files/det-fish-v03-evidence/android_inference_evidence.json" ]]; then
  echo "FAIL: Android inference evidence JSON was not pulled from the device." >&2
  exit 1
fi

(
  cd "$OUT"
  find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS
)
ARCHIVE="\${OUT%/}.zip"
rm -f "$ARCHIVE"
zip -qr "$ARCHIVE" "$OUT"
echo "ANDROID_DEVICE_INFERENCE_PASS"
echo "EVIDENCE_DIR=$OUT"
echo "EVIDENCE_ARCHIVE=$ARCHIVE"
