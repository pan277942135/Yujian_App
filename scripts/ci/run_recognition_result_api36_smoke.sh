#!/usr/bin/env bash
set -uo pipefail

evidence_dir="${EVIDENCE_DIR:-evidence/runtime/recognition-result-api36}"
apk_dir="${APK_DIR:-build/runtime-apks}"
source_sha="${SOURCE_SHA:-unknown}"
app_package="com.yujian.ai.uiv2"
test_package="com.yujian.ai.uiv2.test"
runner="androidx.test.runner.AndroidJUnitRunner"
test_class="com.yujian.ai.RecognitionRevealProductionRuntimeTest"
test_method="realNightCatchHandoffDrivesTheNativeResultReveal"
result_classification="INFRA_BLOCKED"
failure_reason="SMOKE_NOT_STARTED"
instrumentation_attempted=false
test_started=false
adb_ready=false
emulator_boot_pass=false
instrument_status=-1
instrumentation_timed_out=false
validation_reason="NOT_RUN"
logcat_pid=""
memory_sampler_pid=""
screenrecord_pid=""
screenrecord_exit_status=not_started
video_pull_status=not_run
pre_screenshot_pull_status=not_run
post_screenshot_pull_status=not_run

mkdir -p "$evidence_dir/screenshots" "$evidence_dir/business-events"

capture_memory_snapshot() {
  local label="$1"
  {
    printf '\n===== HOST_MEMORY_SNAPSHOT %s %s =====\n' "$label" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    cat /proc/meminfo 2>&1
    cat /proc/swaps 2>&1
    cat /proc/pressure/memory 2>&1
    grep -E '^(oom_kill|pgmajfault|pswpin|pswpout) ' /proc/vmstat 2>&1
    for cgroup_file in /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.events; do
      if [[ -r "$cgroup_file" ]]; then
        printf '\n--- %s ---\n' "$cgroup_file"
        cat "$cgroup_file"
      fi
    done
  } >> "$evidence_dir/host-memory-snapshots.txt"

  [[ "$adb_ready" == true ]] || return 0
  {
    printf '\n===== EMULATOR_MEMORY_SNAPSHOT %s %s =====\n' "$label" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    adb shell cat /proc/meminfo 2>&1
    adb shell cat /proc/swaps 2>&1
    printf '\n--- /proc/uptime ---\n'
    adb shell cat /proc/uptime 2>&1
    adb shell cat /proc/pressure/memory 2>&1
    adb shell "grep -E '^(oom_kill|pgmajfault|pswpin|pswpout) ' /proc/vmstat" 2>&1
    adb shell getprop 2>&1 | grep -E 'dalvik.vm.(heap|growth)|ro.config.low_ram' || true
    printf '\n--- dumpsys meminfo %s ---\n' "$app_package"
    adb shell dumpsys meminfo "$app_package" 2>&1
    local app_pid
    app_pid="$(adb shell pidof "$app_package" 2>/dev/null | tr -d '\r' | awk '{print $1}')"
    if [[ "$app_pid" =~ ^[0-9]+$ ]]; then
      printf '\n--- app process status pid=%s ---\n' "$app_pid"
      adb shell "grep -E '^(Name|VmPeak|VmSize|VmHWM|VmRSS|VmSwap|RssAnon|RssFile|RssShmem):' /proc/$app_pid/status" 2>&1
    else
      printf 'app_process_status=NOT_RUNNING pid=%s\n' "${app_pid:-none}"
    fi
    printf '\n--- activity low-memory state ---\n'
    adb shell dumpsys activity lmk 2>&1
  } >> "$evidence_dir/emulator-memory-snapshots.txt"
}

pull_private_evidence() {
  [[ "$adb_ready" == true ]] || return 0
  local cache_root="$evidence_dir/business-events"
  adb exec-out run-as "$app_package" tar -C cache -cf - rr09-gold-transfer-production \
    > "$cache_root/production-test-cache.tar" 2> "$cache_root/production-test-cache-pull.log"
  if [[ -s "$cache_root/production-test-cache.tar" ]]; then
    tar -xf "$cache_root/production-test-cache.tar" -C "$cache_root" >> "$cache_root/production-test-cache-pull.log" 2>&1
    rm -f "$cache_root/production-test-cache.tar"
  fi
  adb exec-out run-as "$app_package" tar -C cache -cf - recognition-runtime-result-handoff \
    > "$cache_root/production-runtime-evidence.tar" 2> "$cache_root/production-runtime-evidence-pull.log"
  if [[ -s "$cache_root/production-runtime-evidence.tar" ]]; then
    tar -xf "$cache_root/production-runtime-evidence.tar" -C "$cache_root" >> "$cache_root/production-runtime-evidence-pull.log" 2>&1
    rm -f "$cache_root/production-runtime-evidence.tar"
  fi
}

finish() {
  local rc=$?
  trap - EXIT
  set +e
  if [[ -n "$memory_sampler_pid" ]]; then
    kill "$memory_sampler_pid" 2>/dev/null
    wait "$memory_sampler_pid" 2>/dev/null
  fi
  if [[ -n "$logcat_pid" ]]; then
    kill "$logcat_pid" 2>/dev/null
    wait "$logcat_pid" 2>/dev/null
  fi
  if [[ "$adb_ready" == true ]]; then
    capture_memory_snapshot final
    adb shell 'pidof screenrecord >/dev/null 2>&1 && kill -INT $(pidof screenrecord)' \
      > "$evidence_dir/screenrecord-stop.log" 2>&1
    if [[ -n "$screenrecord_pid" ]]; then
      wait "$screenrecord_pid" 2>/dev/null
      screenrecord_exit_status=$?
    fi
    adb shell screencap -p /sdcard/Download/recognition-result-api36-post-smoke.png \
      > "$evidence_dir/screencap.log" 2>&1
    adb pull /sdcard/Download/recognition-result-api36.mp4 \
      "$evidence_dir/recognition-result-api36.mp4" \
      > "$evidence_dir/video-pull.log" 2>&1
    video_pull_status=$?
    adb pull /sdcard/Download/recognition-result-api36-post-smoke.png \
      "$evidence_dir/screenshots/post-smoke.png" \
      > "$evidence_dir/screenshot-pull.log" 2>&1
    post_screenshot_pull_status=$?
    adb logcat -b all -d -v threadtime > "$evidence_dir/logcat.txt" 2>&1
    grep -Ei 'lowmemorykiller|lmkd|am_kill|am_proc_died|out of memory|oom.*kill|low memory' \
      "$evidence_dir/logcat.txt" > "$evidence_dir/lowmemorykiller-events.txt" || true
    grep -Ei 'SLIMSAM_(START|END|PREWARM_[A-Z]+)|SLIMSAM_(ENCODER|DECODER)_(START|END)|SLIMSAM_.*duration_ms|GC |GC:|Native Heap|lowmemorykiller|lmkd' \
      "$evidence_dir/logcat.txt" > "$evidence_dir/model-gc-native-memory-events.txt" || true
    adb devices -l > "$evidence_dir/adb-devices-final.txt" 2>&1
    pull_private_evidence
  fi

  python3 - "$evidence_dir/memory-analysis.json" "$evidence_dir" <<'PY'
import json, pathlib, re, sys
out, root = map(pathlib.Path, sys.argv[1:])
def read(name):
    p = root / name
    return p.read_text(encoding='utf-8', errors='replace') if p.is_file() else ''
def number(pattern, text):
    match = re.search(pattern, text, re.MULTILINE)
    return int(match.group(1).replace(',', '')) if match else None
host_text = read('host-memory-snapshots.txt')
emulator_text = read('emulator-memory-snapshots.txt')
host_blocks = re.split(r'(?=^===== HOST_MEMORY_SNAPSHOT )', host_text, flags=re.MULTILINE)
emulator_blocks = re.split(r'(?=^===== EMULATOR_MEMORY_SNAPSHOT )', emulator_text, flags=re.MULTILINE)
host = []
for block in host_blocks:
    if not block.startswith('===== HOST_MEMORY_SNAPSHOT '):
        continue
    host.append({
        'label': re.search(r'^===== HOST_MEMORY_SNAPSHOT (\S+)', block, re.MULTILINE).group(1),
        'mem_total_kb': number(r'^MemTotal:\s+(\d+)', block),
        'mem_available_kb': number(r'^MemAvailable:\s+(\d+)', block),
        'swap_total_kb': number(r'^SwapTotal:\s+(\d+)', block),
        'swap_free_kb': number(r'^SwapFree:\s+(\d+)', block),
    })
emulator = []
for block in emulator_blocks:
    if not block.startswith('===== EMULATOR_MEMORY_SNAPSHOT '):
        continue
    uptime = re.search(r'^([0-9]+(?:\.[0-9]+)?)\s+[0-9]+(?:\.[0-9]+)?\s*$', block, re.MULTILINE)
    emulator.append({
        'label': re.search(r'^===== EMULATOR_MEMORY_SNAPSHOT (\S+)', block, re.MULTILINE).group(1),
        'uptime_ms': round(float(uptime.group(1)) * 1000) if uptime else None,
        'mem_available_kb': number(r'^MemAvailable:\s+(\d+)', block),
        'process_rss_kb': number(r'^VmRSS:\s+(\d+)', block),
        'process_rss_peak_kb': number(r'^VmHWM:\s+(\d+)', block),
        'process_swap_kb': number(r'^VmSwap:\s+(\d+)', block),
        'app_total_pss_kb': number(r'^\s*TOTAL PSS:\s*([\d,]+)', block),
        'native_heap_kb': number(r'^\s*Native Heap:\s*([\d,]+)', block),
    })
logcat = read('logcat.txt')
def model_event(name):
    values = []
    for line in logcat.splitlines():
        if name not in line:
            continue
        at = re.search(r'uptime_ms=(\d+)', line)
        duration = re.search(r'duration_ms=(\d+)', line)
        if at or duration:
            values.append({'uptime_ms': int(at.group(1)) if at else None,
                           'duration_ms': int(duration.group(1)) if duration else None,
                           'line': line.strip()})
    return values
slimsam = {name: model_event(name) for name in (
    'SLIMSAM_START', 'SLIMSAM_END', 'SLIMSAM_ENCODER_START',
    'SLIMSAM_ENCODER_END', 'SLIMSAM_DECODER_START', 'SLIMSAM_DECODER_END')}
slim_start = next((row['uptime_ms'] for row in slimsam['SLIMSAM_START'] if row['uptime_ms'] is not None), None)
slim_end = next((row['uptime_ms'] for row in slimsam['SLIMSAM_END'] if row['uptime_ms'] is not None), None)
model_window = [row for row in emulator if row['uptime_ms'] is not None and slim_start is not None and slim_end is not None and slim_start <= row['uptime_ms'] <= slim_end]
kill_text = read('lowmemorykiller-events.txt')
app_kill = bool(re.search(r'(lowmemorykiller|lmkd|am_kill|am_proc_died).*com\.yujian\.ai\.uiv2|com\.yujian\.ai\.uiv2.*(killed|kill)', kill_text, re.I))
min_host_available = min((row['mem_available_kb'] for row in host if row['mem_available_kb'] is not None), default=None)
min_emulator_available = min((row['mem_available_kb'] for row in emulator if row['mem_available_kb'] is not None), default=None)
peak_model = max((row['process_rss_kb'] for row in model_window if row['process_rss_kb'] is not None), default=None)
final_rss = next((row['process_rss_kb'] for row in reversed(emulator) if row['process_rss_kb'] is not None), None)
if app_kill:
    resource = 'RESOURCE_EXHAUSTED_APP_KILLED_BY_LOW_MEMORY_MANAGER'
elif min_host_available is not None and min_host_available < 512 * 1024:
    resource = 'HOST_MEMORY_PRESSURE_OBSERVED'
elif min_emulator_available is not None and min_emulator_available < 256 * 1024:
    resource = 'EMULATOR_MEMORY_PRESSURE_OBSERVED'
else:
    resource = 'NO_SYSTEM_KILL_OR_CRITICAL_MEMORY_PRESSURE_OBSERVED'
if peak_model is not None and final_rss is not None and peak_model > final_rss * 1.10:
    model_peak = 'TRANSIENT_RSS_PEAK_DURING_SLIMSAM_WINDOW'
else:
    model_peak = 'NO_TRANSIENT_SLIMSAM_RSS_PEAK_CONFIRMED'
payload = {
    'resource_classification': resource,
    'model_peak_assessment': model_peak,
    'memory_leak_assessment': 'NOT_DIAGNOSABLE_FROM_ONE_PRODUCTION_ATTEMPT; repeated workload or heap profiling is required',
    'host_memory_min_available_kb': min_host_available,
    'emulator_memory_min_available_kb': min_emulator_available,
    'slimsam_model_events': slimsam,
    'slimsam_memory_window_samples': model_window,
    'app_rss_peak_in_slimsam_window_kb': peak_model,
    'final_app_rss_kb': final_rss,
    'lowmemorykiller_app_kill_observed': app_kill,
    'host_snapshots': host,
    'emulator_snapshots': emulator,
}
out.write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
PY

  if [[ "$result_classification" == AUTO_SMOKE_PASS ]]; then
    for required_evidence in \
      "$evidence_dir/instrumentation.log" \
      "$evidence_dir/logcat.txt" \
      "$evidence_dir/memory-analysis.json" \
      "$evidence_dir/recognition-result-api36.mp4" \
      "$evidence_dir/screenshots/pre-smoke.png" \
      "$evidence_dir/screenshots/post-smoke.png" \
      "$evidence_dir/business-events/rr09-gold-transfer-production/Recognition_Result_Automatic_Handoff.png" \
      "$evidence_dir/business-events/rr09-gold-transfer-production/Stage_Timeline.json" \
      "$evidence_dir/business-events/rr09-gold-transfer-production/Result_Navigation_Trace.json" \
      "$evidence_dir/business-events/rr09-gold-transfer-production/Result_Geometry.json" \
      "$evidence_dir/business-events/rr09-gold-transfer-production/Return_Event.json" \
      "$evidence_dir/business-events/rr09-gold-transfer-production/Result_Handoff_Trace.json"; do
      if [[ ! -s "$required_evidence" ]]; then
        result_classification=INFRA_BLOCKED
        failure_reason="REQUIRED_RUNTIME_EVIDENCE_MISSING:${required_evidence#"$evidence_dir"/}"
        rc=1
        break
      fi
    done
    if [[ "$video_pull_status" != 0 || "$pre_screenshot_pull_status" != 0 ||
          "$post_screenshot_pull_status" != 0 ]]; then
      result_classification=INFRA_BLOCKED
      failure_reason="SCREENRECORD_OR_SCREENSHOT_CAPTURE_FAILED:video=$video_pull_status:screenrecord=$screenrecord_exit_status:pre=$pre_screenshot_pull_status:post=$post_screenshot_pull_status"
      rc=1
    fi
  fi

  python3 - "$evidence_dir/runtime-result.json" "$source_sha" "$result_classification" \
    "$failure_reason" "$rc" "$instrument_status" "$instrumentation_timed_out" "$test_started" \
    "$instrumentation_attempted" "$adb_ready" "$emulator_boot_pass" "${BUILD_PASS:-false}" \
    "${KVM_READY:-false}" "$validation_reason" "$apk_dir" "$screenrecord_exit_status" \
    "$video_pull_status" "$pre_screenshot_pull_status" "$post_screenshot_pull_status" <<'PY'
import hashlib, json, pathlib, sys, time
(path, source_sha, classification, reason, exit_code, instrument_status, timed_out,
 test_started, attempted, adb_ready, emulator_boot_pass, build_pass, kvm_ready,
 validation_reason, apk_dir, screenrecord_exit_status, video_pull_status,
 pre_screenshot_pull_status, post_screenshot_pull_status) = sys.argv[1:]
def sha256_file(name):
    p = pathlib.Path(apk_dir) / name
    if not p.is_file():
        return None
    digest = hashlib.sha256()
    with p.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()
passed = classification == 'AUTO_SMOKE_PASS'
result = {
    'source_sha': source_sha,
    'api_level_expected': 36,
    'test': 'com.yujian.ai.RecognitionRevealProductionRuntimeTest#realNightCatchHandoffDrivesTheNativeResultReveal',
    'classification': classification,
    'failure_reason': reason,
    'validation_reason': validation_reason,
    'instrumentation_started': test_started == 'true',
    'instrumentation_attempted': attempted == 'true',
    'instrumentation_command_exit_code': int(instrument_status),
    'instrumentation_timed_out': timed_out == 'true',
    'adb_ready': adb_ready == 'true',
    'emulator_boot_pass': emulator_boot_pass == 'true',
    'BUILD_PASS': build_pass == 'true',
    'KVM_READY': kvm_ready == 'true',
    'EMULATOR_BOOT_PASS': emulator_boot_pass == 'true',
    'AUTO_SMOKE_PASS': passed,
    'DEVICE_QA_PENDING': True,
    'app_apk_sha256': sha256_file('app-debug.apk'),
    'android_test_apk_sha256': sha256_file('app-debug-androidTest.apk'),
    'screenrecord_exit_status': screenrecord_exit_status,
    'video_pull_status': video_pull_status,
    'pre_screenshot_pull_status': pre_screenshot_pull_status,
    'post_screenshot_pull_status': post_screenshot_pull_status,
    'captured_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
}
pathlib.Path(path).write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
PY
  find "$evidence_dir" -type f ! -name evidence-sha256sums.txt -print0 \
    | sort -z | xargs -0 -r sha256sum > "$evidence_dir/evidence-sha256sums.txt"
  printf 'API36_AUTO_SMOKE_CLASSIFICATION=%s reason=%s\n' "$result_classification" "$failure_reason"
  exit "$rc"
}
trap finish EXIT

if [[ ! -s "$apk_dir/app-debug.apk" || ! -s "$apk_dir/app-debug-androidTest.apk" ]]; then
  failure_reason="EXACT_HEAD_APK_PAIR_MISSING"
  exit 20
fi
sha256sum "$apk_dir/app-debug.apk" "$apk_dir/app-debug-androidTest.apk" \
  > "$evidence_dir/apk-pair-sha256sums.txt"

if ! timeout 30s adb wait-for-device; then
  failure_reason="ADB_WAIT_FOR_DEVICE_FAILED"
  exit 30
fi
adb_ready=true

boot_ready=false
for attempt in $(seq 1 300); do
  device_state="$(adb get-state 2>/dev/null | tr -d '\r' || true)"
  boot_complete="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  api_level="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
  if [[ "$device_state" == "device" && "$boot_complete" == "1" && "$api_level" == "36" ]]; then
    boot_ready=true
    break
  fi
  sleep 1
done
if [[ "$boot_ready" != true ]]; then
  adb devices -l > "$evidence_dir/adb-devices-boot-failure.txt" 2>&1
  adb shell getprop > "$evidence_dir/device-properties-boot-failure.txt" 2>&1
  adb logcat -b all -d -v threadtime > "$evidence_dir/logcat-boot-failure.txt" 2>&1
  failure_reason="BOOT_COMPLETED_ADB_READY_OR_API36_TIMEOUT"
  exit 31
fi
emulator_boot_pass=true
printf 'adb_state=device\nsys.boot_completed=1\napi_level=36\n' > "$evidence_dir/device-readiness.txt"
adb devices -l > "$evidence_dir/adb-devices-ready.txt"
adb shell getprop > "$evidence_dir/device-properties.txt"

avd_home="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
find "$avd_home" -maxdepth 3 -name config.ini -type f -print -exec cat {} \; \
  > "$evidence_dir/avd-config-and-memory.txt" 2>&1 || true
if ! grep -Eiq '^hw\.ramSize=4096(M|MB)?$' "$evidence_dir/avd-config-and-memory.txt" ||
   ! grep -Eiq '^hw\.heapSize=512(M|MB)?$' "$evidence_dir/avd-config-and-memory.txt"; then
  printf 'AVD_MEMORY_CONFIG_MISMATCH expected_hw.ramSize=4096M expected_hw.heapSize=512M observed:\n'
  grep -Ei '^(hw\.(ramSize|heapSize)|vm\.heapSize)=' \
    "$evidence_dir/avd-config-and-memory.txt" || true
  failure_reason="AVD_RAM_OR_VM_HEAP_CONFIGURATION_MISMATCH"
  exit 32
fi
adb shell cat /proc/meminfo > "$evidence_dir/emulator-meminfo-before.txt" 2>&1
adb shell cat /proc/swaps > "$evidence_dir/emulator-swaps-before.txt" 2>&1
adb shell getprop > "$evidence_dir/emulator-properties-before.txt" 2>&1
adb shell getprop | grep -E 'dalvik.vm.(heap|growth)|ro.config.low_ram' \
  > "$evidence_dir/emulator-heap-properties.txt" 2>&1 || true
capture_memory_snapshot before_install

if ! adb install -r "$apk_dir/app-debug.apk" > "$evidence_dir/app-install.log" 2>&1; then
  result_classification="INFRA_BLOCKED"
  failure_reason="EXACT_HEAD_APP_APK_INSTALL_FAILED"
  exit 40
fi
if ! adb install -r "$apk_dir/app-debug-androidTest.apk" > "$evidence_dir/test-apk-install.log" 2>&1; then
  result_classification="INFRA_BLOCKED"
  failure_reason="EXACT_HEAD_ANDROID_TEST_APK_INSTALL_FAILED"
  exit 41
fi
adb shell screencap -p /sdcard/Download/recognition-result-api36-pre-smoke.png \
  > "$evidence_dir/screencap-pre.log" 2>&1
adb pull /sdcard/Download/recognition-result-api36-pre-smoke.png \
  "$evidence_dir/screenshots/pre-smoke.png" > "$evidence_dir/screenshot-pre-pull.log" 2>&1
pre_screenshot_pull_status=$?
capture_memory_snapshot after_install

adb logcat -b all -c
adb shell rm -f /sdcard/Download/recognition-result-api36.mp4 \
  /sdcard/Download/recognition-result-api36-pre-smoke.png \
  /sdcard/Download/recognition-result-api36-post-smoke.png
adb logcat -b all -v threadtime > "$evidence_dir/logcat-live.txt" 2>&1 &
logcat_pid=$!
adb shell screenrecord --time-limit 180 /sdcard/Download/recognition-result-api36.mp4 \
  > "$evidence_dir/screenrecord.log" 2>&1 &
screenrecord_pid=$!
sleep 2

(
  while true; do
    capture_memory_snapshot "during_$(date -u +%H%M%S)"
    sleep 10
  done
) &
memory_sampler_pid=$!

instrumentation_attempted=true
failure_reason="INSTRUMENTATION_DID_NOT_COMPLETE"
set +e
timeout 240s adb shell am instrument -w -r \
  -e class "$test_class#$test_method" \
  "$test_package/$runner" 2>&1 | tee "$evidence_dir/instrumentation.log"
instrument_status=${PIPESTATUS[0]}
set -e
if [[ "$instrument_status" -eq 124 || "$instrument_status" -eq 137 ]]; then
  instrumentation_timed_out=true
fi
if [[ -n "$memory_sampler_pid" ]]; then
  kill "$memory_sampler_pid" 2>/dev/null || true
  wait "$memory_sampler_pid" 2>/dev/null || true
  memory_sampler_pid=""
fi
if [[ -n "$logcat_pid" ]]; then
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true
  logcat_pid=""
fi
adb logcat -b all -d -v threadtime > "$evidence_dir/logcat-pre-verdict.txt" 2>&1
if grep -Fq "INSTRUMENTATION_STATUS: test=$test_method" "$evidence_dir/instrumentation.log"; then
  test_started=true
fi

# Pull the app-private trace before applying the verdict so crashes retain partial evidence.
pull_private_evidence
business_dir="$evidence_dir/business-events/rr09-gold-transfer-production"
business_report="$business_dir/AUTO_SMOKE_BUSINESS_CONTRACT.json"
validator_args=(
  --instrumentation-log "$evidence_dir/instrumentation.log"
  --business-report "$business_report"
  --business-evidence-dir "$business_dir"
  --source-sha "$source_sha"
  --command-exit-code "$instrument_status"
)
if [[ "$instrumentation_timed_out" == true ]]; then
  validator_args+=(--timed-out)
fi
set +e
validation_output="$(python3 scripts/ci/validate_recognition_result_api36_smoke.py "${validator_args[@]}" 2>&1)"
validation_status=$?
set -e
printf '%s\n' "$validation_output" | tee "$evidence_dir/business-integrity-report.json"
validation_reason="$(python3 -c 'import json,sys; print(json.loads(sys.stdin.read()).get("reason", "VALIDATOR_OUTPUT_INVALID"))' <<< "$validation_output" 2>/dev/null || printf '%s' 'VALIDATOR_OUTPUT_INVALID')"

if grep -Eiq '(lowmemorykiller|lmkd|am_kill|am_proc_died).*(com\.yujian\.ai\.uiv2|oom|low memory)|(oom.*kill.*com\.yujian\.ai\.uiv2)' \
    "$evidence_dir/instrumentation.log" "$evidence_dir/logcat-pre-verdict.txt" "$evidence_dir/logcat-live.txt" "$evidence_dir/emulator-memory-snapshots.txt"; then
  result_classification="RESOURCE_EXHAUSTED"
  failure_reason="APP_PROCESS_KILLED_BY_LOW_MEMORY_MANAGER"
elif [[ "$validation_status" -eq 0 && "$validation_reason" == "NONE" ]]; then
  result_classification="AUTO_SMOKE_PASS"
  failure_reason="NONE"
  printf 'AUTO_SMOKE_PASS source_sha=%s\n' "$source_sha"
  exit 0
elif [[ "$instrumentation_attempted" == true && "$test_started" == true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="$validation_reason"
elif grep -Eiq 'device offline|device not found|no devices/emulators|unable to connect|instrumentation .*not found|INSTRUMENTATION_FAILED' \
    "$evidence_dir/instrumentation.log" "$evidence_dir/test-apk-install.log" 2>/dev/null; then
  result_classification="INFRA_BLOCKED"
  failure_reason="ANDROID_TEST_RUNNER_OR_ADB_INFRA_FAILURE:$validation_reason"
elif [[ "$instrumentation_attempted" == true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="$validation_reason"
fi
exit 50
