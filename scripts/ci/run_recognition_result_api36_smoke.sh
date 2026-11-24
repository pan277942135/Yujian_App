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
current_phase="INITIALIZATION"
phase_pending_failure_reason="SMOKE_NOT_STARTED"
failure_phase="INITIALIZATION"
instrumentation_attempted=false
test_started=false
adb_ready=false
emulator_boot_pass=false
instrument_status=-1
instrumentation_timed_out=false
validation_reason="NOT_RUN"
slimsam_validation_reason="NOT_RUN"
logcat_pid=""
memory_sampler_pid=""
screenrecord_pid=""
screenrecord_exit_status=not_started
video_pull_status=not_run
pre_screenshot_pull_status=not_run
post_screenshot_pull_status=not_run

mkdir -p "$evidence_dir/screenshots" "$evidence_dir/business-events"

enter_phase() {
  current_phase="$1"
  phase_pending_failure_reason="$2"
  failure_reason="$phase_pending_failure_reason"
  failure_phase="$current_phase"
}

run_optional_diagnostic() {
  local label="$1"
  shift
  local capture_file=""
  if [[ "${1:-}" == "--capture-to" ]]; then
    capture_file="$2"
    shift 2
  fi

  local command_repr
  printf -v command_repr '%q ' "$@"
  local log_fd
  local snapshot_file="$diagnostic_snapshot_file"
  if ! exec {log_fd}>>"$snapshot_file"; then
    printf 'optional diagnostic log is not writable: %s\n' "$snapshot_file" >&2
    return 1
  fi
  if ! printf '\n--- OPTIONAL_DIAGNOSTIC phase=%s label=%s command=%s ---\n' \
      "$current_phase" "$label" "$command_repr" >&"$log_fd"; then
    exec {log_fd}>&-
    return 1
  fi

  local -a pipeline_status=()
  if [[ -n "$capture_file" ]]; then
    if "$@" 2>&1 | tee -a "$snapshot_file" "$capture_file" >/dev/null; then
      pipeline_status=("${PIPESTATUS[@]}")
    else
      pipeline_status=("${PIPESTATUS[@]}")
    fi
  else
    if "$@" 2>&1 | tee -a "$snapshot_file" >/dev/null; then
      pipeline_status=("${PIPESTATUS[@]}")
    else
      pipeline_status=("${PIPESTATUS[@]}")
    fi
  fi

  local diagnostic_rc="${pipeline_status[0]:-125}"
  local tee_rc="${pipeline_status[1]:-125}"
  if ! printf 'diagnostic_exit_code=%s\n' "$diagnostic_rc" >&"$log_fd"; then
    exec {log_fd}>&-
    return 1
  fi
  exec {log_fd}>&-
  if [[ "$tee_rc" -ne 0 ]]; then
    printf 'optional diagnostic evidence write failed: %s (tee exit %s)\n' \
      "$snapshot_file" "$tee_rc" >&2
    return 1
  fi
  return 0
}

capture_memory_snapshot() {
  local label="$1"
  local diagnostic_snapshot_file="$evidence_dir/host-memory-snapshots.txt"
  if ! {
    printf '\n===== HOST_MEMORY_SNAPSHOT %s %s =====\n' "$label" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >> "$diagnostic_snapshot_file"; then
    return 1
  fi
  run_optional_diagnostic 'host /proc/meminfo' cat /proc/meminfo || return $?
  run_optional_diagnostic 'host /proc/swaps' cat /proc/swaps || return $?
  run_optional_diagnostic 'host /proc/pressure/memory' cat /proc/pressure/memory || return $?
  run_optional_diagnostic 'host /proc/vmstat' grep -E '^(oom_kill|pgmajfault|pswpin|pswpout) ' /proc/vmstat || return $?
  for cgroup_file in /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.events; do
    if [[ -r "$cgroup_file" ]]; then
      run_optional_diagnostic "host $cgroup_file" cat "$cgroup_file" || return $?
    fi
  done

  [[ "$adb_ready" == true ]] || return 0
  diagnostic_snapshot_file="$evidence_dir/emulator-memory-snapshots.txt"
  if ! {
    printf '\n===== EMULATOR_MEMORY_SNAPSHOT %s %s =====\n' "$label" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >> "$diagnostic_snapshot_file"; then
    return 1
  fi
  run_optional_diagnostic 'emulator /proc/meminfo' adb shell cat /proc/meminfo || return $?
  run_optional_diagnostic 'emulator /proc/swaps' adb shell cat /proc/swaps || return $?
  run_optional_diagnostic 'emulator /proc/uptime' adb shell cat /proc/uptime || return $?
  run_optional_diagnostic 'emulator /proc/pressure/memory' adb shell cat /proc/pressure/memory || return $?
  run_optional_diagnostic 'emulator /proc/vmstat' adb shell "grep -E '^(oom_kill|pgmajfault|pswpin|pswpout) ' /proc/vmstat" || return $?
  run_optional_diagnostic 'emulator Dalvik heap properties' bash -o pipefail -c \
    'adb shell getprop | grep -E "dalvik.vm.(heap|growth)|ro.config.low_ram"' || return $?
  run_optional_diagnostic "emulator dumpsys meminfo $app_package" adb shell dumpsys meminfo "$app_package" || return $?
  local pid_capture_file
  if ! pid_capture_file="$(mktemp "$evidence_dir/app-pid.XXXXXX")"; then
    return 1
  fi
  run_optional_diagnostic 'emulator app pidof' --capture-to "$pid_capture_file" \
    adb shell pidof "$app_package" || return $?
  local app_pid
  if ! app_pid="$(awk '{ gsub(/\r/, ""); if ($0 ~ /^[0-9]+$/) { print $1; exit } }' "$pid_capture_file")"; then
    rm -f "$pid_capture_file"
    return 1
  fi
  rm -f "$pid_capture_file" || return $?
  if [[ "$app_pid" =~ ^[0-9]+$ ]]; then
    run_optional_diagnostic "emulator app /proc/$app_pid/status" adb shell \
      "grep -E '^(Name|VmPeak|VmSize|VmHWM|VmRSS|VmSwap|RssAnon|RssFile|RssShmem):' /proc/$app_pid/status" || return $?
  else
    printf 'app_process_status=NOT_RUNNING pid=%s\n' "${app_pid:-none}" >> "$diagnostic_snapshot_file" || return $?
  fi
  run_optional_diagnostic 'emulator dumpsys activity lmk' adb shell dumpsys activity lmk || return $?
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
  adb exec-out run-as "$app_package" tar -C cache -cf - YuJian_SlimSAM_Preload_Evidence \
    > "$cache_root/slimsam-preload-evidence.tar" 2> "$cache_root/slimsam-preload-evidence-pull.log"
  if [[ -s "$cache_root/slimsam-preload-evidence.tar" ]]; then
    tar -xf "$cache_root/slimsam-preload-evidence.tar" -C "$cache_root" >> "$cache_root/slimsam-preload-evidence-pull.log" 2>&1
    rm -f "$cache_root/slimsam-preload-evidence.tar"
  fi
}

finish() {
  local rc=$?
  trap - EXIT
  set +e
  if [[ "$rc" -ne 0 && "$failure_reason" == "SMOKE_NOT_STARTED" ]]; then
    failure_reason="$phase_pending_failure_reason"
    failure_phase="$current_phase"
  fi
  if [[ -n "$memory_sampler_pid" ]]; then
    kill "$memory_sampler_pid" 2>/dev/null
    wait "$memory_sampler_pid" 2>/dev/null
  fi
  if [[ -n "$logcat_pid" ]]; then
    kill "$logcat_pid" 2>/dev/null
    wait "$logcat_pid" 2>/dev/null
  fi
  if [[ "$adb_ready" == true ]]; then
    if ! capture_memory_snapshot final; then
      result_classification=INFRA_BLOCKED
      failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
      failure_phase="$current_phase"
      rc=1
    fi
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
    local slim_evidence="$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence"
    mkdir -p "$slim_evidence/videos" "$slim_evidence/screenshots"
    [[ -s "$evidence_dir/recognition-result-api36.mp4" ]] && \
      cp "$evidence_dir/recognition-result-api36.mp4" "$slim_evidence/videos/recognition-result-api36.mp4"
    [[ -s "$evidence_dir/screenshots/pre-smoke.png" ]] && \
      cp "$evidence_dir/screenshots/pre-smoke.png" "$slim_evidence/screenshots/pre-smoke.png"
    [[ -s "$evidence_dir/screenshots/post-smoke.png" ]] && \
      cp "$evidence_dir/screenshots/post-smoke.png" "$slim_evidence/screenshots/post-smoke.png"
    [[ -s "$evidence_dir/logcat.txt" ]] && cp "$evidence_dir/logcat.txt" "$slim_evidence/logcat.txt"
    [[ -s "$evidence_dir/instrumentation.log" ]] && cp "$evidence_dir/instrumentation.log" "$slim_evidence/instrumentation.log"
    [[ -s "$evidence_dir/instrumentation_results.xml" ]] && cp "$evidence_dir/instrumentation_results.xml" "$slim_evidence/instrumentation_results.xml"
    [[ -s "$evidence_dir/slimsam-preload-integrity-report.json" ]] && \
      cp "$evidence_dir/slimsam-preload-integrity-report.json" "$slim_evidence/slimsam-preload-integrity-report.json"
    [[ -s "$evidence_dir/business-integrity-report.json" ]] && \
      cp "$evidence_dir/business-integrity-report.json" "$slim_evidence/business-integrity-report.json"
    if [[ -d "$slim_evidence" ]]; then
      find "$slim_evidence" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 -r sha256sum > "$slim_evidence/SHA256SUMS"
    fi
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
      "$evidence_dir/business-events/rr09-gold-transfer-production/Result_Handoff_Trace.json" \
      "$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence/model_runtime_state.json" \
      "$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence/slimsam_timing.json" \
      "$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence/recognition_stage_timing.json" \
      "$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence/memory_profile.json" \
      "$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence/slimsam_execution_trace.csv" \
      "$evidence_dir/instrumentation_results.xml"; do
      if [[ ! -s "$required_evidence" ]]; then
        result_classification=INFRA_BLOCKED
        failure_reason="REQUIRED_RUNTIME_EVIDENCE_MISSING:${required_evidence#"$evidence_dir"/}"
        failure_phase="EVIDENCE_FINALIZATION"
        rc=1
        break
      fi
    done
    if [[ "$video_pull_status" != 0 || "$pre_screenshot_pull_status" != 0 ||
          "$post_screenshot_pull_status" != 0 ]]; then
      result_classification=INFRA_BLOCKED
      failure_reason="SCREENRECORD_OR_SCREENSHOT_CAPTURE_FAILED:video=$video_pull_status:screenrecord=$screenrecord_exit_status:pre=$pre_screenshot_pull_status:post=$post_screenshot_pull_status"
      failure_phase="EVIDENCE_FINALIZATION"
      rc=1
    fi
  fi

  python3 - "$evidence_dir/runtime-result.json" "$source_sha" "$result_classification" \
    "$failure_reason" "$rc" "$instrument_status" "$instrumentation_timed_out" "$test_started" \
    "$failure_phase" "$instrumentation_attempted" "$adb_ready" "$emulator_boot_pass" "${BUILD_PASS:-false}" \
    "${KVM_READY:-false}" "$validation_reason" "$slimsam_validation_reason" "$apk_dir" "$screenrecord_exit_status" \
    "$video_pull_status" "$pre_screenshot_pull_status" "$post_screenshot_pull_status" <<'PY'
import hashlib, json, pathlib, sys, time
(path, source_sha, classification, reason, exit_code, instrument_status, timed_out,
 test_started, failure_phase, attempted, adb_ready, emulator_boot_pass, build_pass, kvm_ready,
 validation_reason, slimsam_validation_reason, apk_dir, screenrecord_exit_status, video_pull_status,
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
    'failure_phase': failure_phase,
    'validation_reason': validation_reason,
    'slimsam_validation_reason': slimsam_validation_reason,
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
active_avd_name="$(adb shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r' || true)"
active_avd_config="$avd_home/${active_avd_name}.avd/config.ini"
{
  printf 'HOME=%s\nANDROID_HOME=%s\nANDROID_AVD_HOME=%s\navd_home_used=%s\n' \
    "${HOME:-unset}" "${ANDROID_HOME:-unset}" "${ANDROID_AVD_HOME:-unset}" "$avd_home"
  printf 'active_avd_name=%s\nactive_avd_config=%s\n' "$active_avd_name" "$active_avd_config"
  if [[ -e "$active_avd_config" ]]; then
    printf '%s\n' '--- active AVD config ---'
    ls -l "$active_avd_config"
    cat "$active_avd_config"
  else
    printf 'ACTIVE_AVD_CONFIG_NOT_FOUND=%s\n' "$active_avd_config"
    printf '%s\n' '--- candidate AVD config files ---'
    if [[ -d "$avd_home" ]]; then
      find -L "$avd_home" -maxdepth 5 -type f \( -name config.ini -o -name hardware-qemu.ini \) \
        -print -exec cat {} \;
    else
      printf 'AVD_HOME_NOT_FOUND=%s\n' "$avd_home"
      ls -la "$(dirname "$avd_home")" 2>&1 || true
    fi
  fi
} > "$evidence_dir/avd-config-and-memory.txt" 2>&1

# Capture the booted AVD's baseline before the config gate so an infra failure
# still carries RAM, swap, Dalvik heap and host memory evidence.
adb shell cat /proc/meminfo > "$evidence_dir/emulator-meminfo-before.txt" 2>&1
adb shell cat /proc/swaps > "$evidence_dir/emulator-swaps-before.txt" 2>&1
adb shell getprop > "$evidence_dir/emulator-properties-before.txt" 2>&1
adb shell getprop | grep -E 'dalvik.vm.(heap|growth)|ro.config.low_ram' \
  > "$evidence_dir/emulator-heap-properties.txt" 2>&1 || true
if ! capture_memory_snapshot before_install; then
  failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
  failure_phase="$current_phase"
  exit 21
fi

# android-emulator-runner writes these AVD properties directly. Normalize
# optional units and whitespace because AVD Manager versions vary in formatting.
avd_ram_config_mb="$(awk -F= '{ key=$1; gsub(/[[:space:]]/, "", key); if (tolower(key) == "hw.ramsize") { value=$2; gsub(/[[:space:]]/, "", value); sub(/[mM]([bB])?$/, "", value); result=value } } END { print result }' "$evidence_dir/avd-config-and-memory.txt")"
avd_heap_config_mb="$(awk -F= '{ key=$1; gsub(/[[:space:]]/, "", key); if (tolower(key) == "hw.heapsize") { value=$2; gsub(/[[:space:]]/, "", value); sub(/[mM]([bB])?$/, "", value); result=value } } END { print result }' "$evidence_dir/avd-config-and-memory.txt")"
if [[ "$avd_ram_config_mb" != "4096" || "$avd_heap_config_mb" != "512" ]]; then
  failure_reason="AVD_RAM_OR_VM_HEAP_CONFIGURATION_MISMATCH:hw.ramSize_mb=${avd_ram_config_mb:-missing}:hw.heapSize_mb=${avd_heap_config_mb:-missing}:avd_home=${avd_home}"
  printf '::error title=AVD memory configuration mismatch::expected hw.ramSize=4096M hw.heapSize=512M; observed hw.ramSize=%sM hw.heapSize=%sM; HOME=%s; ANDROID_AVD_HOME=%s; active_avd=%s; config=%s\n' \
    "${avd_ram_config_mb:-missing}" "${avd_heap_config_mb:-missing}" \
    "${HOME:-unset}" "${ANDROID_AVD_HOME:-unset}" "$active_avd_name" "$active_avd_config"
  printf 'AVD_MEMORY_CONFIG_MISMATCH %s\n' "$failure_reason"
  grep -Ei '^[[:space:]]*(hw\.(ramSize|heapSize)|vm\.heapSize)=' \
    "$evidence_dir/avd-config-and-memory.txt" || true
  exit 32
fi

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
if ! capture_memory_snapshot after_install; then
  failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
  failure_phase="$current_phase"
  exit 21
fi

adb logcat -b all -c
adb shell rm -f /sdcard/Download/recognition-result-api36.mp4 \
  /sdcard/Download/recognition-result-api36-pre-smoke.png \
  /sdcard/Download/recognition-result-api36-post-smoke.png
adb logcat -b all -v threadtime > "$evidence_dir/logcat-live.txt" 2>&1 &
logcat_pid=$!
adb shell screenrecord --time-limit 300 /sdcard/Download/recognition-result-api36.mp4 \
  > "$evidence_dir/screenrecord.log" 2>&1 &
screenrecord_pid=$!
sleep 2

enter_phase APP_STARTUP APP_STARTUP_DID_NOT_COMPLETE
set +e
timeout 60s adb shell am start -W -n "$app_package/com.yujian.ai.MainActivity" \
  > "$evidence_dir/app-startup.txt" 2>&1
app_start_status=$?
set -e
if [[ "$app_start_status" -ne 0 ]] || ! grep -Fq 'Status: ok' "$evidence_dir/app-startup.txt"; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="APP_STARTUP_FAILED:status=$app_start_status"
  exit 42
fi
enter_phase APP_FIRST_FRAME APP_FIRST_FRAME_EVENT_MISSING
first_frame_seen=false
for attempt in $(seq 1 30); do
  if adb logcat -d -s SlimSamTiming:I 2>/dev/null | grep -Fq 'event=APP_FIRST_FRAME'; then
    first_frame_seen=true
    break
  fi
  sleep 1
done
if [[ "$first_frame_seen" != true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="APP_FIRST_FRAME_EVENT_MISSING"
  exit 43
fi
enter_phase POST_FIRST_FRAME_DIAGNOSTICS POST_FIRST_FRAME_SETUP_DID_NOT_COMPLETE
adb shell screencap -p /sdcard/Download/recognition-result-api36-app-first-frame.png \
  > "$evidence_dir/app-first-frame-screencap.log" 2>&1
adb pull /sdcard/Download/recognition-result-api36-app-first-frame.png \
  "$evidence_dir/screenshots/app-first-frame.png" > "$evidence_dir/app-first-frame-pull.log" 2>&1
if ! capture_memory_snapshot after_app_first_frame; then
  failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
  failure_phase="$current_phase"
  exit 44
fi

memory_sampler_stop_file="$evidence_dir/memory-sampler-stop"
if ! rm -f "$memory_sampler_stop_file"; then
  failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
  failure_phase="$current_phase"
  exit 44
fi
(
  while [[ ! -e "$memory_sampler_stop_file" ]]; do
    if ! capture_memory_snapshot "during_$(date -u +%H%M%S)"; then
      exit 1
    fi
    sleep 10
  done
) &
memory_sampler_pid=$!

instrumentation_attempted=true
enter_phase INSTRUMENTATION INSTRUMENTATION_DID_NOT_COMPLETE
set +e
timeout 1200s adb shell am instrument -w -r \
  -e class "$test_class#$test_method" \
  "$test_package/$runner" 2>&1 | tee "$evidence_dir/instrumentation.log"
instrument_status=${PIPESTATUS[0]}
set -e
if [[ "$instrument_status" -eq 124 || "$instrument_status" -eq 137 ]]; then
  instrumentation_timed_out=true
fi
if [[ -n "$memory_sampler_pid" ]]; then
  if ! : > "$memory_sampler_stop_file"; then
    failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
    failure_phase="POST_FIRST_FRAME_DIAGNOSTICS"
    exit 44
  fi
  if wait "$memory_sampler_pid" 2>/dev/null; then
    memory_sampler_status=0
  else
    memory_sampler_status=$?
  fi
  memory_sampler_pid=""
  if [[ "$memory_sampler_status" -ne 0 ]]; then
    failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED"
    failure_phase="POST_FIRST_FRAME_DIAGNOSTICS"
    exit 44
  fi
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
enter_phase BUSINESS_VALIDATION BUSINESS_EVIDENCE_VALIDATION_DID_NOT_COMPLETE
pull_private_evidence
python3 - "$evidence_dir/instrumentation.log" "$evidence_dir/instrumentation_results.xml" <<'PY'
import pathlib, re, sys, xml.etree.ElementTree as ET
raw_path, xml_path = map(pathlib.Path, sys.argv[1:])
raw = raw_path.read_text(encoding='utf-8', errors='replace') if raw_path.is_file() else ''
test_name = 'realNightCatchHandoffDrivesTheNativeResultReveal'
final_codes = re.findall(r'^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$', raw, re.M)
failed = bool(re.search(r'FAILURES!!!|INSTRUMENTATION_RESULT: shortMsg=|INSTRUMENTATION_STATUS_CODE: -\d+', raw))
passed = bool(final_codes and final_codes[-1] == '-1' and not failed and re.search(r'OK \(1 test\)', raw))
root = ET.Element('testsuites')
suite = ET.SubElement(root, 'testsuite', {
    'name': 'com.yujian.ai.RecognitionRevealProductionRuntimeTest',
    'tests': '1', 'failures': '0' if passed else '1', 'errors': '0',
})
case = ET.SubElement(suite, 'testcase', {
    'classname': 'com.yujian.ai.RecognitionRevealProductionRuntimeTest',
    'name': test_name,
})
if not passed:
    failure = ET.SubElement(case, 'failure', {'message': 'AndroidJUnitRunner did not report a normal passing completion'})
    failure.text = raw[-12000:]
ET.ElementTree(root).write(xml_path, encoding='utf-8', xml_declaration=True)
PY
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

slimsam_evidence_dir="$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence"
enter_phase SLIMSAM_VALIDATION SLIMSAM_EVIDENCE_VALIDATION_DID_NOT_COMPLETE
set +e
slimsam_validation_output="$(python3 scripts/ci/validate_slimsam_preload_evidence.py --evidence-dir "$slimsam_evidence_dir" 2>&1)"
slimsam_validation_status=$?
set -e
printf '%s\n' "$slimsam_validation_output" | tee "$evidence_dir/slimsam-preload-integrity-report.json"
slimsam_validation_reason="$(python3 -c 'import json,sys; print(json.loads(sys.stdin.read()).get("reason", "VALIDATOR_OUTPUT_INVALID"))' <<< "$slimsam_validation_output" 2>/dev/null || printf '%s' 'VALIDATOR_OUTPUT_INVALID')"
if [[ "$slimsam_validation_status" -ne 0 ]]; then
  validation_status=1
  if [[ "$validation_reason" == NONE ]]; then
    validation_reason="SLIMSAM_RUNTIME_EVIDENCE_INVALID:$slimsam_validation_reason"
  fi
fi

if grep -Eiq '(lowmemorykiller|lmkd|am_kill|am_proc_died).*(com\.yujian\.ai\.uiv2|oom|low memory)|(oom.*kill.*com\.yujian\.ai\.uiv2)' \
    "$evidence_dir/instrumentation.log" "$evidence_dir/logcat-pre-verdict.txt" "$evidence_dir/logcat-live.txt" "$evidence_dir/emulator-memory-snapshots.txt"; then
  result_classification="RESOURCE_EXHAUSTED"
  failure_reason="APP_PROCESS_KILLED_BY_LOW_MEMORY_MANAGER"
  failure_phase="INSTRUMENTATION"
elif [[ "$validation_status" -eq 0 && "$validation_reason" == "NONE" ]]; then
  result_classification="AUTO_SMOKE_PASS"
  enter_phase COMPLETE NONE
  printf 'AUTO_SMOKE_PASS source_sha=%s\n' "$source_sha"
  exit 0
elif [[ "$instrumentation_attempted" == true && "$test_started" == true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="$validation_reason"
  if [[ "$validation_reason" == SLIMSAM_RUNTIME_EVIDENCE_INVALID:* ]]; then
    failure_phase="SLIMSAM_VALIDATION"
  else
    failure_phase="BUSINESS_VALIDATION"
  fi
elif grep -Eiq 'device offline|device not found|no devices/emulators|unable to connect|instrumentation .*not found|INSTRUMENTATION_FAILED' \
    "$evidence_dir/instrumentation.log" "$evidence_dir/test-apk-install.log" 2>/dev/null; then
  result_classification="INFRA_BLOCKED"
  failure_reason="ANDROID_TEST_RUNNER_OR_ADB_INFRA_FAILURE:$validation_reason"
  failure_phase="INSTRUMENTATION"
elif [[ "$instrumentation_attempted" == true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="$validation_reason"
  failure_phase="BUSINESS_VALIDATION"
fi
exit 50
