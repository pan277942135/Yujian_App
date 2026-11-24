#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../../.." && pwd)"
runner="$repo_root/scripts/ci/run_recognition_result_api36_smoke.sh"
test_root="$(mktemp -d "${TMPDIR:-/tmp}/api36-smoke-runner-test.XXXXXX")"
trap 'rm -rf "$test_root"' EXIT

mkdir -p "$test_root/bin" "$test_root/home/.android/avd/Fake_API_36.avd" \
  "$test_root/apks" "$test_root/evidence"
printf 'hw.ramSize=4096M\nhw.heapSize=512M\n' \
  > "$test_root/home/.android/avd/Fake_API_36.avd/config.ini"
printf 'fake app apk\n' > "$test_root/apks/app-debug.apk"
printf 'fake test apk\n' > "$test_root/apks/app-debug-androidTest.apk"

cat > "$test_root/bin/adb" <<'FAKE_ADB'
#!/usr/bin/env bash
set -u
printf '%s\n' "$*" >> "$FAKE_ADB_CALLS"

if [[ "${1:-}" == exec-out ]]; then
  exit 0
fi
if [[ "${1:-}" == pull ]]; then
  destination="${3:?missing adb pull destination}"
  mkdir -p "$(dirname "$destination")"
  printf 'fake pulled evidence\n' > "$destination"
  exit 0
fi
if [[ "${1:-}" == install ]]; then
  printf 'Success\n'
  exit 0
fi
if [[ "${1:-}" == devices ]]; then
  printf 'List of devices attached\nfake-api36 device\n'
  exit 0
fi
if [[ "${1:-}" == get-state ]]; then
  printf 'device\n'
  exit 0
fi
if [[ "${1:-}" == wait-for-device ]]; then
  exit 0
fi
if [[ "${1:-}" == logcat ]]; then
  args="$*"
  if [[ "$args" == *"-c"* ]]; then
    exit 0
  fi
  if [[ "$args" == *"-d"* && "$args" == *"SlimSamTiming:I"* ]]; then
    if [[ -f "$FAKE_APP_STARTED" ]]; then
      printf 'I/SlimSamTiming: event=APP_FIRST_FRAME\n'
    fi
    exit 0
  fi
  if [[ "$args" == *"-d"* ]]; then
    if [[ -f "$FAKE_APP_STARTED" ]]; then
      printf '11-24 00:00:01.000 100 100 I SlimSamTiming: event=APP_FIRST_FRAME\n'
    fi
    exit 0
  fi
  for attempt in $(seq 1 40); do
    if [[ -f "$FAKE_APP_STARTED" ]]; then
      printf '11-24 00:00:01.000 100 100 I SlimSamTiming: event=APP_FIRST_FRAME\n'
      exec sleep 60
    fi
    sleep 0.1
  done
  exit 0
fi

if [[ "${1:-}" == shell ]]; then
  shift
  shell_args="$*"
  case "$shell_args" in
    "getprop sys.boot_completed") printf '1\n'; exit 0 ;;
    "getprop ro.build.version.sdk") printf '36\n'; exit 0 ;;
    "getprop ro.boot.qemu.avd_name") printf 'Fake_API_36\n'; exit 0 ;;
    "getprop")
      printf '[dalvik.vm.heapgrowthlimit]: [512m]\n[dalvik.vm.heapsize]: [1024m]\n[ro.config.low_ram]: [false]\n'
      exit 0
      ;;
    "cat /proc/swaps") printf 'cat: /proc/swaps: Permission denied\n' >&2; exit 1 ;;
    "cat /proc/pressure/memory") printf 'cat: /proc/pressure/memory: Permission denied\n' >&2; exit 1 ;;
    "cat /proc/meminfo") printf 'MemTotal: 4194304 kB\nMemAvailable: 3145728 kB\nSwapTotal: 0 kB\nSwapFree: 0 kB\n'; exit 0 ;;
    "cat /proc/uptime") printf '1000.00 2000.00\n'; exit 0 ;;
    *"/proc/vmstat"*) printf 'oom_kill 0\npgmajfault 0\npswpin 0\npswpout 0\n'; exit 0 ;;
    *"dumpsys meminfo"*) printf 'Native Heap 12000 13000 14000\n TOTAL PSS: 42000\n'; exit 0 ;;
    *"pidof com.yujian.ai.uiv2"*) printf '42\n'; exit 0 ;;
    *"/proc/42/status"*) printf 'Name: com.yujian.ai.uiv2\nVmRSS: 43000 kB\nVmHWM: 44000 kB\nVmSwap: 0 kB\n'; exit 0 ;;
    *"dumpsys activity lmk"*) printf 'No low memory events\n'; exit 0 ;;
    *"am start"*)
      : > "$FAKE_APP_STARTED"
      printf 'Starting: Intent { cmp=com.yujian.ai.uiv2/.MainActivity }\nStatus: ok\nLaunchState: COLD\nTotalTime: 3362\n'
      exit 0
      ;;
    *"am instrument"*)
      : > "$FAKE_INSTRUMENTATION_INVOKED"
      printf 'INSTRUMENTATION_STATUS: class=com.yujian.ai.RecognitionRevealProductionRuntimeTest\n'
      printf 'INSTRUMENTATION_STATUS: test=realNightCatchHandoffDrivesTheNativeResultReveal\n'
      printf 'INSTRUMENTATION_STATUS_CODE: 1\n'
      printf 'INSTRUMENTATION_RESULT: stream=\n'
      printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n'
      printf 'INSTRUMENTATION_CODE: 0\n'
      exit 0
      ;;
    *"screencap"*|*"rm -f /sdcard/Download"*|*"screenrecord"*) exit 0 ;;
  esac
  printf 'unexpected fake adb shell command: %s\n' "$shell_args" >&2
  exit 2
fi

printf 'unexpected fake adb command: %s\n' "$*" >&2
exit 2
FAKE_ADB
chmod +x "$test_root/bin/adb"

set +e
(cd "$repo_root" && env \
  PATH="$test_root/bin:$PATH" \
  HOME="$test_root/home" \
  ANDROID_AVD_HOME="$test_root/home/.android/avd" \
  EVIDENCE_DIR="$test_root/evidence" \
  APK_DIR="$test_root/apks" \
  SOURCE_SHA=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
  FAKE_ADB_CALLS="$test_root/adb-calls.log" \
  FAKE_APP_STARTED="$test_root/app-started" \
  FAKE_INSTRUMENTATION_INVOKED="$test_root/instrumentation-invoked" \
  bash "$runner") > "$test_root/runner.stdout" 2>&1
runner_status=$?
set -e

runtime_result="$test_root/evidence/runtime-result.json"
emulator_snapshots="$test_root/evidence/emulator-memory-snapshots.txt"

assert_permission_diagnostic_recorded() {
  local diagnostic_label="$1"
  awk -v label="$diagnostic_label" '
    index($0, "phase=POST_FIRST_FRAME_DIAGNOSTICS label=" label " ") {
      in_record = 1
      denied = 0
      next
    }
    in_record && /^--- OPTIONAL_DIAGNOSTIC / { in_record = 0 }
    in_record && /Permission denied/ { denied = 1 }
    in_record && /^diagnostic_exit_code=1$/ {
      if (denied) found = 1
      in_record = 0
    }
    END { exit(found ? 0 : 1) }
  ' "$emulator_snapshots"
}

assert_permission_diagnostic_recorded 'emulator /proc/swaps'
assert_permission_diagnostic_recorded 'emulator /proc/pressure/memory'
grep -Fq 'Status: ok' "$test_root/evidence/app-startup.txt"
grep -Fq 'event=APP_FIRST_FRAME' "$test_root/evidence/logcat-live.txt"
grep -Fq 'am instrument' "$test_root/adb-calls.log"
[[ -f "$test_root/instrumentation-invoked" ]]

python3 - "$runtime_result" "$runner_status" <<'PY'
import json, pathlib, sys
result = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
runner_status = int(sys.argv[2])
assert runner_status != 0, "controlled failing instrumentation must fail the real runner"
assert result["instrumentation_attempted"] is True, result
assert result["instrumentation_started"] is True, result
assert result["AUTO_SMOKE_PASS"] is False, result
assert result["classification"] != "AUTO_SMOKE_PASS", result
assert result["failure_phase"] == "BUSINESS_VALIDATION", result
assert result["failure_reason"] != "APP_STARTUP_DID_NOT_COMPLETE", result
PY

printf '%s\n' \
  'PROC_SWAPS_PERMISSION_DENIED=RECORDED' \
  'PROC_PRESSURE_PERMISSION_DENIED=RECORDED' \
  'APP_STARTUP_PASS=TRUE' \
  'APP_FIRST_FRAME_PASS=TRUE' \
  'INSTRUMENTATION_COMMAND_INVOKED=TRUE' \
  'INSTRUMENTATION_ATTEMPTED=TRUE' \
  'AUTO_SMOKE_PASS=FALSE'
