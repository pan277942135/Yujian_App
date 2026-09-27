#!/usr/bin/env bash
set -euo pipefail

echo "FROZEN_GATE_DEVICE_DIAGNOSTICS_BEGIN"
adb devices -l || true
initial_adb_state="$(adb get-state 2>&1 || true)"
echo "ADB_INITIAL_STATE=${initial_adb_state}"

# android-emulator-runner waits for the platform boot property, but ddmlib can
# still inherit an offline adb transport after a cold boot. Restart once only
# when the initial diagnostic proves the transport is unstable.
if [[ "${initial_adb_state}" != "device" ]]; then
  echo "ADB_RECOVERY=restart_once"
  adb kill-server
  adb start-server
fi

adb wait-for-device
boot_completed=""
current_adb_state=""
for attempt in $(seq 1 30); do
  current_adb_state="$(adb get-state 2>&1 || true)"
  boot_completed="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  echo "ADB_READINESS attempt=${attempt} state=${current_adb_state} boot_completed=${boot_completed}"
  if [[ "${current_adb_state}" == "device" && "${boot_completed}" == "1" ]]; then
    break
  fi
  sleep 2
done
if [[ "${current_adb_state}" != "device" || "${boot_completed}" != "1" ]]; then
  echo "FROZEN_GATE_DEVICE_NOT_READY state=${current_adb_state} boot_completed=${boot_completed}"
  adb devices -l || true
  exit 1
fi

# Explicit pre-install diagnostics required for API 28 gate triage.
adb wait-for-device
adb shell getprop sys.boot_completed
adb devices -l
adb shell pm list packages | head -n 20 || true

# Remove stale packages before UTP installs the exact APK pair built by CI.
adb uninstall com.yujian.ai.uiv2 || true
adb uninstall com.yujian.ai.uiv2.test || true

adb shell df -h /data
data_free_kb="$(adb shell df -k /data | awk 'NR == 2 { print $4 }' | tr -d '\r')"
echo "DATA_FREE_KB=${data_free_kb}"
if ! [[ "${data_free_kb}" =~ ^[0-9]+$ ]] || (( data_free_kb < 524288 )); then
  echo "FROZEN_GATE_DATA_INSUFFICIENT free_kb=${data_free_kb} required_kb=524288"
  exit 1
fi

set +e
adb logcat -c
gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.yujian.ai.RecognitionFrozenFlowEmulatorTest \
  --stacktrace
gate_status=$?
set -e
adb logcat -d -v threadtime RecognitionFrozenGate:I '*:S' || true

mkdir -p evidence/ui_rework_v1/recognition
for name in \
  01_capture_transition.png \
  02_ai_understanding.png \
  03_fish_highlight.png \
  04_fish_identifying.png \
  05_result_high.png \
  06_result_medium.png \
  07_result_low.png \
  08_error_no_fish.png \
  09_error_image_quality.png
do
  adb exec-out run-as com.yujian.ai.uiv2 cat "cache/recognition-evidence/${name}" \
    > "evidence/ui_rework_v1/recognition/${name}" || true
  if [[ ! -s "evidence/ui_rework_v1/recognition/${name}" ]]; then
    rm -f "evidence/ui_rework_v1/recognition/${name}"
  fi
done

exit "${gate_status}"
