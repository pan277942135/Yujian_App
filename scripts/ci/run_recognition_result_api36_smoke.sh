#!/usr/bin/env bash
set -uo pipefail

evidence_dir="${EVIDENCE_DIR:-evidence/runtime/recognition-result-api36}"
apk_dir="${APK_DIR:-build/runtime-apks}"
source_sha="${SOURCE_SHA:-unknown}"
app_package="com.yujian.ai.uiv2"
test_package="com.yujian.ai.uiv2.test"
runner="androidx.test.runner.AndroidJUnitRunner"
test_a_class="com.yujian.ai.ClassifierRealTfliteTensorBindingTest"
test_a_method="packagedClassifierRunsWithFrozenTensorBinding"
test_class="com.yujian.ai.RecognitionRevealProductionRuntimeTest"
test_method="realNightCatchHandoffDrivesTheNativeResultReveal"
test_c_class="com.yujian.ai.RecognitionResultHandoffProductionNavigationTest"
test_c_method="hostedApi36HomePickerAutoRecognitionCompletesResultHandoff"
result_classification="INFRA_BLOCKED"
failure_reason="SMOKE_NOT_STARTED"
current_phase="INITIALIZATION"
phase_pending_failure_reason="SMOKE_NOT_STARTED"
failure_phase="INITIALIZATION"
instrumentation_attempted=false
test_started=false
test_a_passed=false
test_b_passed=false
test_c_passed=false
test_a_force_stop_verified=false
test_b_force_stop_verified=false
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

mkdir -p "$evidence_dir/screenshots" "$evidence_dir/business-events" \
  "$evidence_dir/test-cases/classifier" "$evidence_dir/test-cases/production-reveal" \
  "$evidence_dir/test-cases/production-auto"

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

record_instrumentation_process_state() {
  local case_dir="$1"
  local label="$2"
  local app_pid=""
  local test_pid=""
  {
    printf 'sample_utc=%s label=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$label"
    if app_pid="$(timeout 15s adb shell pidof "$app_package" 2>/dev/null)"; then
      app_pid="${app_pid//$'\r'/}"
      printf 'app_pid=%s\n' "$app_pid"
      printf '%s\n' "$app_pid" >> "$case_dir/app-pids.txt"
    else
      printf 'app_pid=NOT_RUNNING\n'
    fi
    if test_pid="$(timeout 15s adb shell pidof "$test_package" 2>/dev/null)"; then
      test_pid="${test_pid//$'\r'/}"
      printf 'instrumentation_pid=%s\n' "$test_pid"
      printf '%s\n' "$test_pid" >> "$case_dir/instrumentation-pids.txt"
    elif [[ -n "$app_pid" ]]; then
      printf 'instrumentation_pid=%s (instrumentation-hosted-in-target-app-process)\n' "$app_pid"
      printf '%s\n' "$app_pid" >> "$case_dir/instrumentation-pids.txt"
    else
      printf 'instrumentation_pid=NOT_OBSERVED\n'
    fi
  } >> "$case_dir/process-pids.log" 2>&1
}

run_instrumentation_case() {
  local label="$1"
  local class_name="$2"
  local method_name="$3"
  local case_dir="$4"
  local raw_log="$case_dir/instrumentation.log"
  local start_time end_time command_pid command_status timed_out=false
  local had_errexit=false
  [[ "$-" == *e* ]] && had_errexit=true
  start_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  record_instrumentation_process_state "$case_dir" "${label}_before"
  timeout 15s adb shell dumpsys activity processes > "$case_dir/activity-processes-before.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$app_package" > "$case_dir/app-meminfo-before.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$test_package" > "$case_dir/test-meminfo-before.txt" 2>&1

  set +e
  timeout 1200s adb shell am instrument -w -r \
    -e class "$class_name#$method_name" \
    "$test_package/$runner" > "$raw_log" 2>&1 &
  command_pid=$!
  printf 'local_instrumentation_command_pid=%s\n' "$command_pid" > "$case_dir/local-command-pid.txt"
  while kill -0 "$command_pid" 2>/dev/null; do
    record_instrumentation_process_state "$case_dir" "${label}_running"
    sleep 5
  done
  if wait "$command_pid"; then command_status=0; else command_status=$?; fi
  if [[ "$command_status" -eq 124 || "$command_status" -eq 137 ]]; then timed_out=true; fi
  if [[ "$had_errexit" == true ]]; then set -e; fi
  end_time="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  record_instrumentation_process_state "$case_dir" "${label}_after"
  timeout 15s adb shell dumpsys activity processes > "$case_dir/activity-processes-after.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$app_package" > "$case_dir/app-meminfo-after.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$test_package" > "$case_dir/test-meminfo-after.txt" 2>&1
  adb logcat -b all -d -v threadtime > "$case_dir/logcat.txt" 2>&1

  local -a parser_args=(
    --class-name "$class_name" --method-name "$method_name"
    --log "$raw_log" --auxiliary-log "$case_dir/logcat.txt"
    --command-exit-code "$command_status"
    --app-pids "$case_dir/app-pids.txt"
    --instrumentation-pids "$case_dir/instrumentation-pids.txt"
    --start-time-utc "$start_time" --end-time-utc "$end_time"
    --json-out "$case_dir/result.json" --xml-out "$case_dir/results.xml"
  )
  [[ "$timed_out" == true ]] && parser_args+=(--timed-out)
  if python3 scripts/ci/api36_instrumentation_case.py "${parser_args[@]}"; then
    python3 - "$case_dir/results.xml" "$class_name" "$method_name" <<'PY'
import sys, xml.etree.ElementTree as ET
path, expected_class, expected_method = sys.argv[1:]
root = ET.parse(path).getroot()
cases = root.findall(".//testcase")
assert len(cases) == 1, f"expected one JUnit testcase, found {len(cases)}"
case = cases[0]
assert case.get("classname") == expected_class, case.attrib
assert case.get("name") == expected_method, case.attrib
assert not case.findall("failure") and not case.findall("error") and not case.findall("skipped"), ET.tostring(case)
suite = root.find("testsuite") if root.tag == "testsuites" else root
assert suite is not None and suite.get("tests") == "1", ET.tostring(root)
assert suite.get("failures") == "0" and suite.get("errors") == "0" and suite.get("skipped") == "0", suite.attrib
PY
    cat "$raw_log"
    return 0
  else
    local parser_status=$?
    cat "$raw_log"
    return "$parser_status"
  fi
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
case_results = []
for case_file in (root / 'test-cases').glob('*/result.json'):
    try:
        case_results.append(json.loads(case_file.read_text(encoding='utf-8')))
    except (OSError, ValueError):
        pass
app_kill = any(case.get('lmk_confirmed') is True for case in case_results)
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

  python3 - "$evidence_dir/runtime-result.json" "$evidence_dir" "$source_sha" "$result_classification" \
    "$failure_reason" "$rc" "$instrument_status" "$instrumentation_timed_out" "$test_started" \
    "$failure_phase" "$instrumentation_attempted" "$adb_ready" "$emulator_boot_pass" "${BUILD_PASS:-false}" \
    "${KVM_READY:-false}" "$validation_reason" "$slimsam_validation_reason" "$apk_dir" "$screenrecord_exit_status" \
    "$video_pull_status" "$pre_screenshot_pull_status" "$post_screenshot_pull_status" \
    "$test_a_force_stop_verified" "$test_b_force_stop_verified" <<'PY'
import hashlib, json, pathlib, sys, time
(path, evidence_root, source_sha, classification, reason, exit_code, instrument_status, timed_out,
 test_started, failure_phase, attempted, adb_ready, emulator_boot_pass, build_pass, kvm_ready,
 validation_reason, slimsam_validation_reason, apk_dir, screenrecord_exit_status, video_pull_status,
 pre_screenshot_pull_status, post_screenshot_pull_status, force_stop_verified,
 reveal_force_stop_verified) = sys.argv[1:]
path = pathlib.Path(path)
evidence_root = pathlib.Path(evidence_root)
def load_case(name):
    p = evidence_root / 'test-cases' / name / 'result.json'
    try:
        return json.loads(p.read_text(encoding='utf-8'))
    except Exception:
        return {'passed': False, 'reason': 'CASE_RESULT_MISSING', 'test': None}
test_a = load_case('classifier')
test_b = load_case('production-reveal')
test_c = load_case('production-auto')
def sha256_file(name):
    p = pathlib.Path(apk_dir) / name
    if not p.is_file():
        return None
    digest = hashlib.sha256()
    with p.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()
passed = (classification == 'AUTO_SMOKE_PASS' and test_a.get('passed') is True and
          test_b.get('passed') is True and test_c.get('passed') is True and
          force_stop_verified == 'true' and reveal_force_stop_verified == 'true')
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
    'instrumentation_cases': {
        'classifier_tensor_binding': test_a,
        'production_night_catch_reveal': test_b,
        'production_home_picker_auto': test_c,
    },
    'CLASSIFIER_TFLITE_RUNTIME_PASS': test_a.get('passed') is True,
    'PRODUCTION_REVEAL_INSTRUMENTATION_PASS': test_b.get('passed') is True,
    'AUTO_PRODUCTION_SMOKE_PASS': test_c.get('passed') is True,
    'API36_INSTRUMENTATION_PASS': test_a.get('passed') is True and test_b.get('passed') is True and test_c.get('passed') is True,
    'APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS': force_stop_verified == 'true',
    'APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO': reveal_force_stop_verified == 'true',
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
avd_cpu_arch="$(awk -F= '{ key=$1; gsub(/[[:space:]]/, "", key); if (tolower(key) == "hw.cpu.arch") { value=$2; gsub(/[[:space:]]/, "", value); result=value } } END { print result }' "$evidence_dir/avd-config-and-memory.txt")"
avd_image_sysdir="$(awk -F= '{ key=$1; gsub(/[[:space:]]/, "", key); if (tolower(key) == "image.sysdir.1") { value=$2; sub(/\/+$/, "", value); result=value } } END { print result }' "$evidence_dir/avd-config-and-memory.txt")"
if [[ "$avd_ram_config_mb" != "4096" || "$avd_heap_config_mb" != "512" ||
      "$avd_cpu_arch" != "x86_64" || "$avd_image_sysdir" != *"system-images/android-36/google_apis/x86_64" ]]; then
  failure_reason="AVD_CONFIGURATION_MISMATCH:hw.ramSize=${avd_ram_config_mb:-missing}:hw.heapSize=${avd_heap_config_mb:-missing}:hw.cpu.arch=${avd_cpu_arch:-missing}:image.sysdir.1=${avd_image_sysdir:-missing}:avd_home=${avd_home}"
  printf '::error title=AVD configuration mismatch::expected hw.ramSize=4096M hw.heapSize=512M hw.cpu.arch=x86_64 image.sysdir.1=*system-images/android-36/google_apis/x86_64; observed hw.ramSize=%sM hw.heapSize=%sM hw.cpu.arch=%s image.sysdir.1=%s; HOME=%s; ANDROID_AVD_HOME=%s; active_avd=%s; config=%s\n' \
    "${avd_ram_config_mb:-missing}" "${avd_heap_config_mb:-missing}" \
    "${avd_cpu_arch:-missing}" "${avd_image_sysdir:-missing}" \
    "${HOME:-unset}" "${ANDROID_AVD_HOME:-unset}" "$active_avd_name" "$active_avd_config"
  printf 'AVD_MEMORY_CONFIG_MISMATCH %s\n' "$failure_reason"
  grep -Ei '^[[:space:]]*(hw\.(ramSize|heapSize|cpu\.arch)|image\.sysdir\.1|vm\.heapSize)=' \
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
enter_phase CLASSIFIER_TFLITE_RUNTIME CLASSIFIER_INSTRUMENTATION_DID_NOT_COMPLETE
if run_instrumentation_case classifier_tflite "$test_a_class" "$test_a_method" \
    "$evidence_dir/test-cases/classifier"; then
  test_a_passed=true
  printf 'CLASSIFIER_TFLITE_INSTRUMENTATION_PASS source_sha=%s\n' "$source_sha"
else
  test_a_status=$?
  printf 'CLASSIFIER_TFLITE_INSTRUMENTATION_FAIL command_or_parser_status=%s\n' "$test_a_status"
fi
if ! capture_memory_snapshot after_classifier_tflite_test; then
  failure_reason="MEMORY_DIAGNOSTIC_EVIDENCE_WRITE_FAILED_AFTER_CLASSIFIER_TEST"
  failure_phase="CLASSIFIER_TFLITE_RUNTIME"
  exit 44
fi

enter_phase BETWEEN_INSTRUMENTATION_TESTS APP_FORCE_STOP_NOT_VERIFIED
if timeout 30s adb shell am force-stop "$app_package" > "$evidence_dir/force-stop-after-classifier.log" 2>&1; then
  for attempt in $(seq 1 30); do
    if app_pid_after_force_stop="$(timeout 10s adb shell pidof "$app_package" 2>/dev/null)"; then
      app_pid_after_force_stop="${app_pid_after_force_stop//$'\r'/}"
      if [[ -n "$app_pid_after_force_stop" ]]; then
        sleep 1
      else
        test_a_force_stop_verified=true
        break
      fi
    else
      force_stop_pidof_status=$?
      if [[ "$force_stop_pidof_status" -eq 1 ]]; then
        test_a_force_stop_verified=true
        break
      fi
      printf 'force_stop_pidof_exit_status=%s\n' "$force_stop_pidof_status" \
        >> "$evidence_dir/force-stop-after-classifier.log"
      break
    fi
  done
fi
printf 'APP_FORCE_STOP_VERIFIED_AFTER_TEST_A=%s\n' "$test_a_force_stop_verified" \
  | tee "$evidence_dir/force-stop-after-classifier-verification.txt"
adb logcat -b all -c

enter_phase PRODUCTION_REVEAL_INSTRUMENTATION PRODUCTION_REVEAL_TEST_DID_NOT_COMPLETE
if run_instrumentation_case production_reveal "$test_class" "$test_method" \
    "$evidence_dir/test-cases/production-reveal"; then
  test_b_passed=true
  instrument_status=0
else
  instrument_status=$?
fi
if [[ "$instrument_status" -eq 124 || "$instrument_status" -eq 137 ]]; then
  instrumentation_timed_out=true
fi
cp "$evidence_dir/test-cases/production-reveal/instrumentation.log" "$evidence_dir/instrumentation.log"
cp "$evidence_dir/test-cases/production-reveal/results.xml" "$evidence_dir/instrumentation_results.xml"
if grep -Fq "INSTRUMENTATION_STATUS: test=$test_method" "$evidence_dir/instrumentation.log"; then
  test_started=true
fi

enter_phase BETWEEN_REVEAL_AND_AUTO APP_FORCE_STOP_AFTER_REVEAL_NOT_VERIFIED
if timeout 30s adb shell am force-stop "$app_package" > "$evidence_dir/force-stop-after-reveal.log" 2>&1; then
  for attempt in $(seq 1 30); do
    if app_pid_after_reveal="$(timeout 10s adb shell pidof "$app_package" 2>/dev/null)"; then
      app_pid_after_reveal="${app_pid_after_reveal//$'\r'/}"
      if [[ -n "$app_pid_after_reveal" ]]; then sleep 1; else test_b_force_stop_verified=true; break; fi
    else
      pidof_status=$?
      if [[ "$pidof_status" -eq 1 ]]; then test_b_force_stop_verified=true; fi
      break
    fi
  done
fi
printf 'APP_FORCE_STOP_VERIFIED_AFTER_TEST_B=%s\n' "$test_b_force_stop_verified" \
  | tee "$evidence_dir/force-stop-after-reveal-verification.txt"
adb logcat -b all -c

enter_phase PRODUCTION_AUTO_INSTRUMENTATION PRODUCTION_AUTO_TEST_DID_NOT_COMPLETE
if run_instrumentation_case production_auto "$test_c_class" "$test_c_method" \
    "$evidence_dir/test-cases/production-auto"; then
  test_c_passed=true
  instrument_status=0
else
  instrument_status=$?
fi
if [[ "$instrument_status" -eq 124 || "$instrument_status" -eq 137 ]]; then
  instrumentation_timed_out=true
fi
if grep -Fq "INSTRUMENTATION_STATUS: test=$test_c_method" \
    "$evidence_dir/test-cases/production-auto/instrumentation.log"; then
  test_started=true
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

# Pull the app-private trace before applying the verdict so crashes retain partial evidence.
enter_phase BUSINESS_VALIDATION BUSINESS_EVIDENCE_VALIDATION_DID_NOT_COMPLETE
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

if python3 - "$evidence_dir/test-cases" <<'PY'
import json
import sys
from pathlib import Path
for path in Path(sys.argv[1]).glob('*/result.json'):
    if json.loads(path.read_text(encoding='utf-8')).get('lmk_confirmed') is True:
        raise SystemExit(0)
raise SystemExit(1)
PY
then
  result_classification="RESOURCE_EXHAUSTED"
  failure_reason="APP_PROCESS_KILLED_BY_LOW_MEMORY_MANAGER"
  failure_phase="INSTRUMENTATION"
elif [[ "$test_a_passed" != true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  test_a_reason="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("reason", "RESULT_INVALID"))' \
    "$evidence_dir/test-cases/classifier/result.json" 2>/dev/null || printf '%s' 'CLASSIFIER_TEST_RESULT_MISSING')"
  failure_reason="CLASSIFIER_TFLITE_INSTRUMENTATION_FAILED:$test_a_reason"
  failure_phase="CLASSIFIER_TFLITE_RUNTIME"
elif [[ "$test_a_force_stop_verified" != true ]]; then
  result_classification="INFRA_BLOCKED"
  failure_reason="APP_FORCE_STOP_BETWEEN_INSTRUMENTATION_TESTS_NOT_VERIFIED"
  failure_phase="BETWEEN_INSTRUMENTATION_TESTS"
elif [[ "$test_b_passed" != true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="PRODUCTION_REVEAL_INSTRUMENTATION_FAILED"
  failure_phase="PRODUCTION_REVEAL_INSTRUMENTATION"
elif [[ "$test_b_force_stop_verified" != true ]]; then
  result_classification="INFRA_BLOCKED"
  failure_reason="APP_FORCE_STOP_BETWEEN_REVEAL_AND_AUTO_NOT_VERIFIED"
  failure_phase="BETWEEN_REVEAL_AND_AUTO"
elif [[ "$test_c_passed" != true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  failure_reason="PRODUCTION_HOME_PICKER_AUTO_INSTRUMENTATION_FAILED"
  failure_phase="PRODUCTION_AUTO_INSTRUMENTATION"
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
