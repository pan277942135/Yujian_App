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
test_perf_class="com.yujian.ai.FishMaskApi36PerformanceBaselineTest"
test_perf_method="tenPresetFishCacheMissesRunProductionRecognitionAndMask"
result_classification="INFRA_BLOCKED"
failure_reason="SMOKE_NOT_STARTED"
current_phase="INITIALIZATION"
phase_pending_failure_reason="SMOKE_NOT_STARTED"
failure_phase="INITIALIZATION"
instrumentation_attempted=false
test_started=false
test_a_passed=false
test_b_passed=false
test_b_status=-1
test_b_timed_out=false
test_c_passed=false
single_photo_status="NOT_RUN"
five_photo_status="NOT_RUN_SINGLE_PHOTO_GATE_BLOCKED"
single_photo_reason="NOT_RUN"
test_perf_passed=false
test_perf_status=-1
test_perf_timed_out=false
test_perf_force_stop_verified=false
cold_start_passed_count=0
cold_start_force_stop_verified=true
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
private_evidence_pull_failed=false

mkdir -p "$evidence_dir/screenshots" "$evidence_dir/business-events" \
  "$evidence_dir/test-cases/classifier" "$evidence_dir/test-cases/production-reveal" \
  "$evidence_dir/test-cases/production-auto" "$evidence_dir/test-cases/fishmask-performance-baseline"

enter_phase() {
  current_phase="$1"
  phase_pending_failure_reason="$2"
  failure_reason="$phase_pending_failure_reason"
  failure_phase="$current_phase"
}

record_unhandled_shell_failure() {
  local status="$1"
  local command="$2"
  local line="$3"
  [[ "$-" == *e* ]] || return 0
  local command_repr
  trap - ERR
  printf -v command_repr '%q' "$command"
  printf 'phase=%s line=%s status=%s command=%s\n' \
    "$current_phase" "$line" "$status" "$command_repr" \
    >> "$evidence_dir/unhandled-command-failure.log"
  if [[ "$failure_reason" == "$phase_pending_failure_reason" ]]; then
    failure_reason="UNHANDLED_SHELL_COMMAND_FAILURE:line=$line:status=$status"
    failure_phase="$current_phase"
  fi
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
  run_optional_diagnostic "emulator process exit info $app_package" adb shell dumpsys activity exit-info "$app_package" || return $?
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
  shift 4
  local -a extra_args=("$@")
  local raw_log="$case_dir/instrumentation.log"
  local start_time end_time command_pid command_status timed_out=false case_timeout=1200
  if [[ "$label" == fishmask-performance-baseline ]]; then case_timeout=2400; fi
  if [[ "$label" == production-auto || "$label" == single-photo-e2e ]]; then case_timeout=240; fi
  local had_errexit=false
  [[ "$-" == *e* ]] && had_errexit=true
  start_time="$(date -u +%Y-%m-%dT%H:%M:%S.%6NZ)"
  record_instrumentation_process_state "$case_dir" "${label}_before"
  timeout 15s adb shell dumpsys activity processes > "$case_dir/activity-processes-before.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$app_package" > "$case_dir/app-meminfo-before.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$test_package" > "$case_dir/test-meminfo-before.txt" 2>&1

  set +e
  timeout "${case_timeout}s" adb shell am instrument -w -r \
    -e class "$class_name#$method_name" \
    -e source_sha "$source_sha" \
    "${extra_args[@]}" \
    "$test_package/$runner" > "$raw_log" 2>&1 &
  command_pid=$!
  printf 'local_instrumentation_command_pid=%s\n' "$command_pid" > "$case_dir/local-command-pid.txt"
  local watchdog_started=$SECONDS last_progress=$SECONDS last_checkpoint_signature="" last_runtime_signature=""
  local last_stall_capture=0 encoder_run_id="" encoder_start_ns="" encoder_capture_45=false encoder_capture_55=false
  while kill -0 "$command_pid" 2>/dev/null; do
    record_instrumentation_process_state "$case_dir" "${label}_running"
    local checkpoint_tmp="$case_dir/last-checkpoint-live.json"
    local checkpoint_signature=""
    if timeout 10s adb shell run-as "$app_package" cat \
        "/data/user/0/$app_package/cache/recognition-result-handoff/instrumentation/last_checkpoint.json" \
        > "$checkpoint_tmp" 2>/dev/null; then
      checkpoint_signature="$(sha256sum "$checkpoint_tmp" | awk '{print $1}')"
      if [[ "$checkpoint_signature" != "$last_checkpoint_signature" ]]; then
        last_checkpoint_signature="$checkpoint_signature"
        last_progress=$SECONDS
        cp "$checkpoint_tmp" "$case_dir/last_checkpoint.json"
      fi
    fi
    local runtime_tmp="$case_dir/runtime-events-live.log"
    timeout 10s adb logcat -d -s SlimSamTiming:I > "$runtime_tmp" 2>/dev/null || true
    local runtime_signature
    runtime_signature="$(tail -n 1 "$runtime_tmp" 2>/dev/null | sha256sum | awk '{print $1}')"
    if [[ -n "$runtime_signature" && "$runtime_signature" != "$last_runtime_signature" ]]; then
      last_runtime_signature="$runtime_signature"
      if tail -n 20 "$runtime_tmp" | grep -Fq 'event=NATIVE_MEMORY_SAMPLE'; then last_progress=$SECONDS; fi
    fi

    local current_event=""
    current_event="$(sed -n 's/.*"event"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$case_dir/last_checkpoint.json" 2>/dev/null | head -1)"
    local no_progress=$((SECONDS - last_progress))
    local ui_wait=false
    case "$current_event" in
      HOME_ACTIVITY_LAUNCH_REQUESTED|PHOTO_PICKER_OPEN_REQUESTED|PHOTO_PICKER_ACTIVITY_RESUMED|PHOTO_SELECTION_TAPPED|PHOTO_INPUT_READY_PROCESSING_VISIBLE|RESULT_PAGE_RESUMED_AND_VISIBLE)
        ui_wait=true
        ;;
    esac
    if { [[ "$ui_wait" == true && "$no_progress" -ge 20 ]] || [[ "$no_progress" -ge 30 ]]; } && \
        (( SECONDS - last_stall_capture >= 20 )); then
      capture_instrumentation_snapshot "$case_dir" "NO_PROGRESS_${current_event:-UNKNOWN}"
      last_stall_capture=$SECONDS
    fi

    local latest_encoder_line latest_encoder_run end_for_encoder device_uptime
    latest_encoder_line="$(grep -F 'event=VISION_ENCODER_START' "$runtime_tmp" | tail -1 || true)"
    latest_encoder_run="$(sed -n 's/.*runOptionsId=\([^ ]*\).*/\1/p' <<< "$latest_encoder_line")"
    if [[ -n "$latest_encoder_run" ]]; then
      end_for_encoder="$(grep -F "event=VISION_ENCODER_NATIVE_END" "$runtime_tmp" | grep -F "runOptionsId=$latest_encoder_run" | tail -1 || true)"
      if [[ -z "$end_for_encoder" ]]; then
        encoder_run_id="$latest_encoder_run"
        encoder_start_ns="$(sed -n 's/.*startUptimeNs=\([0-9]*\).*/\1/p' <<< "$latest_encoder_line")"
        device_uptime="$(timeout 10s adb shell cat /proc/uptime 2>/dev/null | awk '{print $1}' | tr -d '\r' || true)"
        if [[ "$encoder_start_ns" =~ ^[0-9]+$ && "$device_uptime" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
          local encoder_elapsed_ms
          encoder_elapsed_ms="$(awk -v now="$device_uptime" -v start="$encoder_start_ns" 'BEGIN { elapsed=(now-start/1000000000)*1000; if (elapsed<0) elapsed=0; printf "%d", elapsed }')"
          if [[ "$encoder_elapsed_ms" -ge 45000 && "$encoder_capture_45" != true ]]; then
            capture_instrumentation_snapshot "$case_dir" "ENCODER_45S"
            encoder_capture_45=true
          fi
          if [[ "$encoder_elapsed_ms" -ge 55000 && "$encoder_capture_55" != true ]]; then
            capture_instrumentation_snapshot "$case_dir" "ENCODER_55S"
            encoder_capture_55=true
          fi
          printf 'active_encoder_run_options_id=%s elapsed_ms=%s\n' "$encoder_run_id" "$encoder_elapsed_ms" \
            >> "$case_dir/encoder-watchdog.log"
        fi
      fi
    fi

    if (( SECONDS - watchdog_started >= case_timeout - 20 )); then
      capture_instrumentation_snapshot "$case_dir" TOTAL_TIMEOUT_BEFORE_TERMINATION
    fi
    sleep 5
  done
  if wait "$command_pid"; then command_status=0; else command_status=$?; fi
  if [[ "$command_status" -eq 124 || "$command_status" -eq 137 ]]; then timed_out=true; fi
  if [[ "$timed_out" == true ]]; then
    capture_instrumentation_snapshot "$case_dir" TOTAL_TIMEOUT_AFTER_ADB_CLIENT_EXIT
    timeout 20s adb shell am force-stop "$test_package" > "$case_dir/force-stop-instrumentation-after-timeout.log" 2>&1 || true
    timeout 20s adb shell am force-stop "$app_package" > "$case_dir/force-stop-app-after-timeout.log" 2>&1 || true
  fi
  if [[ "$had_errexit" == true ]]; then set -e; fi
  end_time="$(date -u +%Y-%m-%dT%H:%M:%S.%6NZ)"

  record_instrumentation_process_state "$case_dir" "${label}_after"
  timeout 15s adb shell dumpsys activity processes > "$case_dir/activity-processes-after.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$app_package" > "$case_dir/app-meminfo-after.txt" 2>&1
  timeout 15s adb shell dumpsys meminfo "$test_package" > "$case_dir/test-meminfo-after.txt" 2>&1
  adb logcat -b all -d -v threadtime > "$case_dir/logcat.txt" 2>&1

  local -a parser_args=(
    --class-name "$class_name" --method-name "$method_name"
    --log "$raw_log" --auxiliary-log "$case_dir/logcat.txt"
    --command-exit-code "$command_status" --failure-phase "$current_phase"
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

capture_instrumentation_snapshot() {
  local case_dir="$1"
  local reason="$2"
  local snapshot_dir="$case_dir/watchdog/${reason}_$(date -u +%Y%m%dT%H%M%SZ)"
  mkdir -p "$snapshot_dir"
  printf 'reason=%s\nwall_time_utc=%s\n' "$reason" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    > "$snapshot_dir/watchdog_reason.txt"
  if [[ -s "$case_dir/last_checkpoint.json" ]]; then
    cp "$case_dir/last_checkpoint.json" "$snapshot_dir/last_checkpoint.json"
  else
    timeout 10s adb shell run-as "$app_package" cat \
      "/data/user/0/$app_package/cache/recognition-result-handoff/instrumentation/last_checkpoint.json" \
      > "$snapshot_dir/last_checkpoint.json" 2>&1 || true
  fi
  timeout 15s adb shell dumpsys activity activities > "$snapshot_dir/activity_state.txt" 2>&1 || true
  timeout 15s adb shell dumpsys window windows > "$snapshot_dir/window_state.txt" 2>&1 || true
  timeout 15s adb shell dumpsys activity exit-info "$app_package" > "$snapshot_dir/process_exit_info.txt" 2>&1 || true
  timeout 15s adb shell dumpsys meminfo "$app_package" > "$snapshot_dir/app_meminfo.txt" 2>&1 || true
  timeout 15s adb shell dumpsys meminfo "$test_package" > "$snapshot_dir/test_meminfo.txt" 2>&1 || true
  local pid
  for pid in $(timeout 10s adb shell pidof "$app_package" "$test_package" 2>/dev/null | tr -d '\r' || true); do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    printf 'pid=%s\n' "$pid" >> "$snapshot_dir/thread_dump_requests.txt"
    timeout 10s adb shell "kill -3 $pid" >> "$snapshot_dir/thread_dump_requests.txt" 2>&1 || true
    timeout 10s adb shell "grep -E '^(Name|VmPeak|VmSize|VmHWM|VmRSS|VmSwap|RssAnon|RssFile|RssShmem):' /proc/$pid/status" \
      >> "$snapshot_dir/process_status.txt" 2>&1 || true
  done
  timeout 15s adb shell uiautomator dump /sdcard/yujian-api36-watchdog-window.xml \
    > "$snapshot_dir/ui_dump_command.txt" 2>&1 || true
  timeout 10s adb pull /sdcard/yujian-api36-watchdog-window.xml "$snapshot_dir/ui_hierarchy.xml" \
    >> "$snapshot_dir/ui_dump_command.txt" 2>&1 || true
  timeout 10s adb exec-out screencap -p > "$snapshot_dir/screen.png" 2> "$snapshot_dir/screen.log" || true
  timeout 15s adb logcat -d -b all -v threadtime > "$snapshot_dir/logcat.txt" 2>&1 || true
  timeout 10s adb logcat -d -s SlimSamTiming:I > "$snapshot_dir/recent_runtime_events.txt" 2>&1 || true
  tail -n 50 "$snapshot_dir/recent_runtime_events.txt" > "$snapshot_dir/runtime_events_last_50.txt" 2>/dev/null || true
  timeout 15s adb exec-out run-as "$app_package" tar -C cache -cf - \
    recognition-result-handoff recognition-runtime-result-handoff YuJian_SlimSAM_Preload_Evidence \
    > "$snapshot_dir/app_private_evidence.tar" 2> "$snapshot_dir/app_private_evidence_pull.log" || true
  if [[ -s "$snapshot_dir/app_private_evidence.tar" ]]; then
    mkdir -p "$snapshot_dir/private"
    tar -xf "$snapshot_dir/app_private_evidence.tar" -C "$snapshot_dir/private" 2>> "$snapshot_dir/app_private_evidence_pull.log" || true
    rm -f "$snapshot_dir/app_private_evidence.tar"
  fi
}

force_stop_test_processes_and_verify() {
  local label="$1"
  local case_dir="$2"
  local log_file="$case_dir/$label.log"
  local package output status stopped=false
  mkdir -p "$case_dir"
  : > "$log_file"
  for package in "$app_package" "$test_package"; do
    if timeout 30s adb shell am force-stop "$package" >> "$log_file" 2>&1; then
      printf 'force_stop_%s=PASS\n' "$package" >> "$log_file"
    else
      printf 'force_stop_%s=FAIL\n' "$package" >> "$log_file"
    fi
  done
  for attempt in $(seq 1 30); do
    stopped=true
    for package in "$app_package" "$test_package"; do
      if output="$(timeout 10s adb shell pidof "$package" 2>>"$log_file")"; then
        output="${output//$'\r'/}"
        if [[ -n "$output" ]]; then stopped=false; fi
      else
        status=$?
        if [[ "$status" -ne 1 ]]; then stopped=false; fi
      fi
    done
    [[ "$stopped" == true ]] && break
    sleep 1
  done
  printf '%s=%s\n' "$label" "$stopped" | tee "$case_dir/$label-verification.txt"
  [[ "$stopped" == true ]]
}

pull_private_evidence() {
  [[ "$adb_ready" == true ]] || return 0
  local cache_root="$evidence_dir/business-events"
  local status_file="$evidence_dir/private-evidence-pull-status.txt"
  local remote_dir archive log_file pull_status extract_status
  mkdir -p "$cache_root"
  local -a remote_dirs=(rr09-gold-transfer-production recognition-runtime-result-handoff YuJian_SlimSAM_Preload_Evidence)
  if [[ "${API36_SINGLE_PHOTO_E2E_ONLY:-false}" == true ]]; then
    remote_dirs=(recognition-result-handoff recognition-runtime-result-handoff YuJian_SlimSAM_Preload_Evidence)
  fi
  for remote_dir in "${remote_dirs[@]}"; do
    case "$remote_dir" in
      rr09-gold-transfer-production)
        archive="$cache_root/production-test-cache.tar"
        log_file="$cache_root/production-test-cache-pull.log"
        ;;
      recognition-runtime-result-handoff)
        archive="$cache_root/production-runtime-evidence.tar"
        log_file="$cache_root/production-runtime-evidence-pull.log"
        ;;
      recognition-result-handoff)
        archive="$cache_root/production-e2e-test-evidence.tar"
        log_file="$cache_root/production-e2e-test-evidence-pull.log"
        ;;
      YuJian_SlimSAM_Preload_Evidence)
        archive="$cache_root/slimsam-preload-evidence.tar"
        log_file="$cache_root/slimsam-preload-evidence-pull.log"
        ;;
    esac

    if adb exec-out run-as "$app_package" tar -C cache -cf - "$remote_dir" \
        > "$archive" 2> "$log_file"; then
      pull_status=0
    else
      pull_status=$?
    fi
    if [[ "$pull_status" -ne 0 ]]; then
      private_evidence_pull_failed=true
      printf 'dataset=%s action=adb_exec_out status=%s\n' "$remote_dir" "$pull_status" \
        >> "$status_file"
      rm -f "$archive" || true
      continue
    fi
    if [[ ! -s "$archive" ]]; then
      private_evidence_pull_failed=true
      printf 'dataset=%s action=adb_exec_out status=0 result=EMPTY_ARCHIVE\n' "$remote_dir" \
        >> "$status_file"
      continue
    fi
    if tar -xf "$archive" -C "$cache_root" >> "$log_file" 2>&1; then
      extract_status=0
    else
      extract_status=$?
    fi
    rm -f "$archive" || true
    if [[ "$extract_status" -ne 0 ]]; then
      private_evidence_pull_failed=true
      printf 'dataset=%s action=tar_extract status=%s\n' "$remote_dir" "$extract_status" \
        >> "$status_file"
    else
      printf 'dataset=%s action=complete status=0\n' "$remote_dir" >> "$status_file"
    fi
  done
}

finish() {
  local rc=$?
  trap - EXIT
  trap - ERR
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
    grep -Ei 'SLIMSAM_(START|END|PREWARM_[A-Z]+)|SLIMSAM_(ENCODER|DECODER)_(START|END)|SLIMSAM_.*duration_ms|NATIVE_MEMORY_SAMPLE|VISION_ENCODER_NATIVE_END|NATIVE_RUN_RETURNED|NATIVE_TERMINATION_UNCONFIRMED|ATTEMPT_DEADLINE_EXCEEDED|SLIMSAM_RESULT_FAILED|GC |GC:|nativeHeapAllocatedBytes|processVmHwmKb|processRssAnonKb|processVmSwapKb|lowmemorykiller|lmkd' \
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

  if ! python3 scripts/ci/api36_memory_analysis.py "$evidence_dir/memory-analysis.json" "$evidence_dir"; then
    result_classification=INFRA_BLOCKED
    failure_reason="MEMORY_ANALYSIS_FAILED"
    failure_phase="EVIDENCE_FINALIZATION"
    rc=1
  fi

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

  PRIVATE_EVIDENCE_PULL_FAILED="$private_evidence_pull_failed" \
  python3 - "$evidence_dir/runtime-result.json" "$evidence_dir" "$source_sha" "$result_classification" \
    "$failure_reason" "$rc" "$instrument_status" "$instrumentation_timed_out" "$test_started" \
    "$failure_phase" "$instrumentation_attempted" "$adb_ready" "$emulator_boot_pass" "${BUILD_PASS:-false}" \
    "${KVM_READY:-false}" "$validation_reason" "$slimsam_validation_reason" "$apk_dir" "$screenrecord_exit_status" \
    "$video_pull_status" "$pre_screenshot_pull_status" "$post_screenshot_pull_status" \
    "$test_a_force_stop_verified" "$test_b_force_stop_verified" \
    "$test_perf_passed" "$test_perf_status" "$test_perf_timed_out" "$test_perf_force_stop_verified" \
    "$cold_start_passed_count" "$cold_start_force_stop_verified" \
    "$test_a_class#$test_a_method" "$test_class#$test_method" "$test_perf_class#$test_perf_method" \
    "$test_perf_class#coldStartWaitsForRealVisionAndDecoderSessions" \
    "$test_c_class#$test_c_method" "$single_photo_status" "$five_photo_status" "$single_photo_reason" <<'PY'
import hashlib, json, os, pathlib, sys, time
(path, evidence_root, source_sha, classification, reason, exit_code, instrument_status, timed_out,
 test_started, failure_phase, attempted, adb_ready, emulator_boot_pass, build_pass, kvm_ready,
 validation_reason, slimsam_validation_reason, apk_dir, screenrecord_exit_status, video_pull_status,
 pre_screenshot_pull_status, post_screenshot_pull_status, force_stop_verified,
 reveal_force_stop_verified, test_perf_passed, test_perf_status, test_perf_timed_out,
 perf_force_stop_verified, cold_start_passed_count, cold_start_force_stop_verified,
 test_a_name, test_b_name, test_perf_name, test_cold_start_name, test_c_name,
 single_photo_status, five_photo_status, single_photo_reason) = sys.argv[1:]
path = pathlib.Path(path)
evidence_root = pathlib.Path(evidence_root)
def load_case(name, test_name, phase):
    directory = evidence_root / 'test-cases' / name
    try:
        return json.loads((directory / 'result.json').read_text(encoding='utf-8'))
    except (OSError, ValueError):
        attempted = (directory / 'local-command-pid.txt').is_file()
        case_reason = 'CASE_RESULT_MISSING_OR_INVALID' if attempted else 'CASE_NOT_RUN'
        return {
            'test': test_name, 'started': False, 'completed': False, 'passed': False,
            'reason': case_reason, 'failure_phase': phase if attempted else failure_phase,
            'failure_reason': case_reason, 'exception': None, 'root_cause': case_reason if attempted else reason,
            'relevant_log_excerpt': '',
        }
test_a = load_case('classifier', test_a_name, 'CLASSIFIER_TFLITE_RUNTIME')
test_b = load_case('production-reveal', test_b_name, 'PRODUCTION_REVEAL_INSTRUMENTATION')
test_perf = load_case('fishmask-performance-baseline', test_perf_name, 'FISH_MASK_PERFORMANCE_BASELINE')
cold_starts = [load_case(f'cold-start-{index}', test_cold_start_name, 'COLD_START_SESSION_PRELOAD') for index in range(1, 4)]
test_c = load_case('production-auto', test_c_name, 'PRODUCTION_AUTO_INSTRUMENTATION')
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
          test_b.get('passed') is True and test_perf.get('passed') is True and
          all(case.get('passed') is True for case in cold_starts) and
          cold_start_passed_count == '3' and cold_start_force_stop_verified == 'true' and
          test_c.get('passed') is True and force_stop_verified == 'true' and
          reveal_force_stop_verified == 'true' and perf_force_stop_verified == 'true')
result = {
    'source_sha': source_sha,
    'api_level_expected': 36,
    'test': 'com.yujian.ai.RecognitionRevealProductionRuntimeTest#realNightCatchHandoffDrivesTheNativeResultReveal',
    'classification': classification,
    'e2e_acceptance_mode': 'API36_SINGLE_AND_FIVE_PHOTO' if os.environ.get('API36_SINGLE_PHOTO_E2E_ONLY') == 'true' else 'LEGACY_API36_SMOKE',
    'single_photo_e2e_status': single_photo_status,
    'single_photo_e2e_reason': single_photo_reason,
    'five_photo_e2e_status': five_photo_status,
    'API36_SINGLE_PHOTO_E2E_PASS': single_photo_status == 'PASS',
    'API36_FIVE_PHOTO_E2E_PASS': five_photo_status == 'PASS',
    'API36_E2E_ACCEPTANCE_PASS': single_photo_status == 'PASS' and five_photo_status == 'PASS',
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
        'cold_start_session_preload': cold_starts,
        'fishmask_demo_performance_baseline': test_perf,
        'production_home_picker_auto': test_c,
    },
    'CLASSIFIER_TFLITE_RUNTIME_PASS': test_a.get('passed') is True,
    'PRODUCTION_REVEAL_INSTRUMENTATION_PASS': test_b.get('passed') is True,
    'COLD_START_SESSION_PRELOAD_PASS': all(case.get('passed') is True for case in cold_starts) and cold_start_force_stop_verified == 'true',
    'COLD_START_SESSION_PRELOAD_COMMAND_PASS_COUNT': int(cold_start_passed_count),
    'AUTO_PRODUCTION_SMOKE_PASS': test_c.get('passed') is True,
    'FISHMASK_DEMO_PERFORMANCE_BASELINE_PASS': test_perf.get('passed') is True,
    'FISHMASK_DEMO_PERFORMANCE_BASELINE_COMMAND_EXIT_CODE': int(test_perf_status),
    'FISHMASK_DEMO_PERFORMANCE_BASELINE_TIMED_OUT': test_perf_timed_out == 'true',
    'API36_INSTRUMENTATION_PASS': test_a.get('passed') is True and test_b.get('passed') is True and all(case.get('passed') is True for case in cold_starts) and test_perf.get('passed') is True and test_c.get('passed') is True,
    'APP_FORCE_STOP_VERIFIED_BETWEEN_TESTS': force_stop_verified == 'true',
    'APP_FORCE_STOP_VERIFIED_BETWEEN_REVEAL_AND_AUTO': reveal_force_stop_verified == 'true',
    'APP_FORCE_STOP_VERIFIED_AFTER_PERFORMANCE_BASELINE': perf_force_stop_verified == 'true',
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
    'private_evidence_pull_failed': os.environ.get('PRIVATE_EVIDENCE_PULL_FAILED') == 'true',
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
set -eE
trap 'record_unhandled_shell_failure "$?" "$BASH_COMMAND" "$LINENO"' ERR
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
if [[ "${API36_SINGLE_PHOTO_E2E_ONLY:-false}" == true ]]; then
  single_case_dir="$evidence_dir/test-cases/production-auto"
  enter_phase API36_SINGLE_PHOTO_E2E SINGLE_PHOTO_E2E_DID_NOT_COMPLETE
  test_c_status=0
  if run_instrumentation_case single-photo-e2e "$test_c_class" "$test_c_method" "$single_case_dir"; then
    test_c_passed=true
  else
    test_c_status=$?
  fi
  instrument_status="$test_c_status"
  test_b_status="$test_c_status"
  if [[ "$test_c_status" -eq 124 || "$test_c_status" -eq 137 ]]; then
    instrumentation_timed_out=true
  fi
  if grep -Fq "INSTRUMENTATION_STATUS: test=$test_c_method" "$single_case_dir/instrumentation.log" 2>/dev/null; then
    test_started=true
  fi
  cp "$single_case_dir/instrumentation.log" "$evidence_dir/instrumentation.log" 2>/dev/null || true
  cp "$single_case_dir/results.xml" "$evidence_dir/instrumentation_results.xml" 2>/dev/null || true
  adb logcat -d -b all -v threadtime > "$single_case_dir/logcat-final.txt" 2>&1 || true
  adb shell run-as "$app_package" cat \
    "/data/user/0/$app_package/cache/recognition-runtime-result-handoff/attempt_failure.json" \
    > "$single_case_dir/attempt_failure.json" 2>/dev/null || true
  local_checkpoint="$single_case_dir/last_checkpoint.json"
  if [[ ! -s "$local_checkpoint" ]]; then
    timeout 10s adb shell run-as "$app_package" cat \
      "/data/user/0/$app_package/cache/recognition-result-handoff/instrumentation/last_checkpoint.json" \
      > "$local_checkpoint" 2>/dev/null || true
  fi

  if [[ "$test_c_passed" == true ]]; then
    report="$single_case_dir/production_e2e_test_report.json"
    timeout 10s adb shell run-as "$app_package" cat \
      "/data/user/0/$app_package/cache/recognition-result-handoff/production_e2e_test_report.json" \
      > "$report" 2>/dev/null || true
    if python3 - "$report" "$source_sha" <<'PY'
import json, pathlib, sys
payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))
assert payload.get('status') == 'PASS'
assert payload.get('sourceCommitSha') == sys.argv[2]
assert payload.get('sourceFixtureSha256') == 'a7ed3bb0410c191364f6d853b4078aebdc2f3b9baa4da34b7b3f6e8e0e93a56b'
assert payload.get('pickerAction') and payload.get('resumedPickerComponent')
assert payload.get('photoUriScheme') == 'content'
assert payload.get('acceptedHandoffEventCount') == 5
assert payload.get('recognitionSessionId') and payload.get('recognitionAttemptId')
assert payload.get('selectedPhotoSha256') == payload.get('canonicalPixelSha256')
assert payload.get('sourceBytesSha256') == payload.get('sourceFixtureSha256')
assert payload.get('width') and payload.get('height')
assert payload.get('resultScreenshotSha256') and payload.get('processingScreenshotSha256')
PY
    then
      single_photo_status="PASS"
      single_photo_reason="ALL_SINGLE_PHOTO_PRODUCTION_ASSERTIONS_PASSED"
      five_photo_status="NOT_RUN_FIVE_PHOTO_FIXTURE_SET_UNDEFINED"
      result_classification="SINGLE_PHOTO_PASS_FIVE_PHOTO_FIXTURE_SET_BLOCKED"
      failure_reason="NO_FROZEN_FIVE_PRODUCTION_PHOTO_MANIFEST:tracked Fish-Mask manifest declares four regression inputs with production_outputs=false"
      failure_phase="FIVE_PHOTO_PREFLIGHT"
      printf 'API36_SINGLE_PHOTO_E2E_PASS source_sha=%s\n' "$source_sha"
      printf 'API36_FIVE_PHOTO_E2E=NOT_RUN reason=%s\n' "$failure_reason"
      exit 51
    else
      single_photo_status="FAIL"
      single_photo_reason="EVIDENCE_INCOMPLETE:single-photo instrumentation passed but report identity or handoff evidence did not validate"
      five_photo_status="NOT_RUN_SINGLE_PHOTO_EVIDENCE_GATE_BLOCKED"
      result_classification="EVIDENCE_INCOMPLETE"
      failure_reason="$single_photo_reason"
      failure_phase="SINGLE_PHOTO_EVIDENCE_VALIDATION"
      exit 50
    fi
  fi

  latest_event="$(sed -n 's/.*"event"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$local_checkpoint" 2>/dev/null | head -1)"
  test_reason="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("reason", "RESULT_INVALID"))' \
    "$single_case_dir/result.json" 2>/dev/null || printf 'RESULT_MISSING')"
  runtime_log="$single_case_dir/runtime-events-live.log"
  if grep -Eq 'NATIVE_EXECUTION_STILL_RUNNING|NATIVE_TERMINATION_UNCONFIRMED' "$runtime_log" 2>/dev/null; then
    single_photo_classification="NATIVE_RUN_NOT_TERMINATED"
  elif grep -Fq 'LMK_CONFIRMED' "$single_case_dir/result.json" 2>/dev/null; then
    single_photo_classification="LMK_KILLED"
  elif grep -Eqi 'fatal signal|tombstone|debuggerd' "$single_case_dir/logcat-final.txt" 2>/dev/null; then
    single_photo_classification="NATIVE_CRASH"
  elif [[ "$test_c_status" -eq 124 || "$test_c_status" -eq 137 ]]; then
    case "$latest_event" in
      HOME_ACTIVITY_LAUNCH_REQUESTED|PHOTO_PICKER_OPEN_REQUESTED|PHOTO_PICKER_ACTIVITY_RESUMED|PHOTO_SELECTION_TAPPED|PHOTO_INPUT_READY_PROCESSING_VISIBLE|RESULT_PAGE_RESUMED_AND_VISIBLE)
        single_photo_classification="UI_AUTOMATION_BLOCKED" ;;
      WAITING_FOR_PRODUCTION_RESULT)
        if grep -Eqi 'HANDOFF.*(TIMEOUT|FAIL)|RESULT.*TIMEOUT' "$runtime_log" "$single_case_dir/attempt_failure.json" 2>/dev/null; then
          single_photo_classification="RESULT_HANDOFF_TIMEOUT"
        else
          single_photo_classification="COROUTINE_WAIT_STUCK"
        fi ;;
      *) single_photo_classification="INSTRUMENTATION_TIMEOUT" ;;
    esac
  elif [[ "$test_reason" == EXPECTED_TEST_NOT_STARTED ]]; then
    single_photo_classification="INFRA_BLOCKED"
  elif [[ "$latest_event" == WAITING_FOR_PRODUCTION_RESULT ]]; then
    if grep -Eqi 'HANDOFF.*(TIMEOUT|FAIL)|RESULT.*TIMEOUT' "$runtime_log" "$single_case_dir/attempt_failure.json" 2>/dev/null; then
      single_photo_classification="RESULT_HANDOFF_TIMEOUT"
    else
      single_photo_classification="COROUTINE_WAIT_STUCK"
    fi
  elif [[ "$latest_event" == PHOTO_SELECTION_TAPPED ]]; then
    single_photo_classification="PHOTO_PICKER_FAILURE"
  elif [[ "$latest_event" == PHOTO_PICKER_* ]]; then
    single_photo_classification="UI_AUTOMATION_BLOCKED"
  else
    failure_code="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("failureCode", ""))' \
      "$single_case_dir/attempt_failure.json" 2>/dev/null || true)"
    case "$failure_code" in
      *MODEL*|*SLIMSAM*|*ENCODER*|*DECODER*) single_photo_classification="MODEL_INFERENCE_FAILURE" ;;
      *MASK*) single_photo_classification="MASK_NOT_TRUSTED" ;;
      *PATH*|*CONTOUR*|*PSTAR*) single_photo_classification="FISH_PATH_FAILURE" ;;
      *HANDOFF*) single_photo_classification="HANDOFF_FAILURE" ;;
      *RESULT*) single_photo_classification="RESULT_RENDER_FAILURE" ;;
      *) single_photo_classification="BUSINESS_FAILURE" ;;
    esac
  fi
  single_photo_status="FAIL"
  single_photo_reason="$single_photo_classification:$test_reason:lastCheckpoint=${latest_event:-NOT_RECORDED}"
  five_photo_status="NOT_RUN_SINGLE_PHOTO_GATE_BLOCKED"
  result_classification="$single_photo_classification"
  failure_reason="$single_photo_reason"
  failure_phase="API36_SINGLE_PHOTO_E2E"
  printf 'API36_SINGLE_PHOTO_E2E_FAIL classification=%s reason=%s\n' "$result_classification" "$failure_reason"
  exit 50
fi

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
test_b_status="$instrument_status"
test_b_timed_out="$instrumentation_timed_out"
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

enter_phase COLD_START_SESSION_PRELOAD COLD_START_SESSION_PRELOAD_SAMPLES_INCOMPLETE
for cold_start_id in 1 2 3; do
  cold_start_case="cold-start-$cold_start_id"
  cold_start_dir="$evidence_dir/test-cases/$cold_start_case"
  if force_stop_test_processes_and_verify "APP_AND_TEST_FORCE_STOP_BEFORE_COLD_START_$cold_start_id" "$cold_start_dir"; then
    if run_instrumentation_case "$cold_start_case" "$test_perf_class" "coldStartWaitsForRealVisionAndDecoderSessions" \
        "$cold_start_dir" -e cold_start_id "$cold_start_id"; then
      cold_start_passed_count=$((cold_start_passed_count + 1))
    fi
  else
    cold_start_force_stop_verified=false
  fi
  if ! force_stop_test_processes_and_verify "APP_AND_TEST_FORCE_STOP_AFTER_COLD_START_$cold_start_id" "$cold_start_dir"; then
    cold_start_force_stop_verified=false
  fi
done

enter_phase FISH_MASK_PERFORMANCE_BASELINE FISH_MASK_PERFORMANCE_BASELINE_DID_NOT_COMPLETE
if run_instrumentation_case fishmask-performance-baseline "$test_perf_class" "$test_perf_method" \
    "$evidence_dir/test-cases/fishmask-performance-baseline"; then
  test_perf_passed=true
  test_perf_status=0
else
  test_perf_status=$?
fi
if [[ "$test_perf_status" -eq 124 || "$test_perf_status" -eq 137 ]]; then
  test_perf_timed_out=true
fi

enter_phase BETWEEN_PERFORMANCE_BASELINE_AND_AUTO APP_FORCE_STOP_AFTER_PERFORMANCE_BASELINE_NOT_VERIFIED
if timeout 30s adb shell am force-stop "$app_package" > "$evidence_dir/force-stop-after-performance-baseline.log" 2>&1; then
  for attempt in $(seq 1 30); do
    if app_pid_after_performance="$(timeout 10s adb shell pidof "$app_package" 2>/dev/null)"; then
      app_pid_after_performance="${app_pid_after_performance//$'\r'/}"
      if [[ -n "$app_pid_after_performance" ]]; then sleep 1; else test_perf_force_stop_verified=true; break; fi
    else
      pidof_status=$?
      if [[ "$pidof_status" -eq 1 ]]; then test_perf_force_stop_verified=true; fi
      break
    fi
  done
fi
printf 'APP_FORCE_STOP_VERIFIED_AFTER_PERFORMANCE_BASELINE=%s\n' "$test_perf_force_stop_verified" \
  | tee "$evidence_dir/force-stop-after-performance-baseline-verification.txt"
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
  --command-exit-code "$test_b_status"
)
if [[ "$test_b_timed_out" == true ]]; then
  validator_args+=(--timed-out)
fi
set +e
if [[ "$test_b_passed" != true && ! -f "$business_report" ]]; then
  validation_output='{"passed": false, "reason": "UPSTREAM_RUNTIME_NOT_REACHED"}'
  validation_status=1
else
  validation_output="$(python3 scripts/ci/validate_recognition_result_api36_smoke.py "${validator_args[@]}" 2>&1)"
  validation_status=$?
fi
set -eE
printf '%s\n' "$validation_output" | tee "$evidence_dir/business-integrity-report.json"
validation_reason="$(python3 -c 'import json,sys; print(json.loads(sys.stdin.read()).get("reason", "VALIDATOR_OUTPUT_INVALID"))' <<< "$validation_output" 2>/dev/null || printf '%s' 'VALIDATOR_OUTPUT_INVALID')"

slimsam_evidence_dir="$evidence_dir/business-events/YuJian_SlimSAM_Preload_Evidence"
enter_phase SLIMSAM_VALIDATION SLIMSAM_EVIDENCE_VALIDATION_DID_NOT_COMPLETE
set +e
slimsam_validation_output="$(python3 scripts/ci/validate_slimsam_preload_evidence.py --evidence-dir "$slimsam_evidence_dir" 2>&1)"
slimsam_validation_status=$?
set -eE
if [[ "$test_b_passed" != true ]] && grep -Fq '"REQUIRED_SLIMSAM_EVIDENCE_MISSING"' <<< "$slimsam_validation_output"; then
  slimsam_validation_output='{"passed": false, "reason": "UPSTREAM_RUNTIME_NOT_REACHED"}'
fi
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
  test_b_reason="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("reason", "RESULT_INVALID"))' \
    "$evidence_dir/test-cases/production-reveal/result.json" 2>/dev/null || printf '%s' 'PRODUCTION_REVEAL_TEST_RESULT_MISSING')"
  failure_reason="PRODUCTION_REVEAL_INSTRUMENTATION_FAILED:$test_b_reason"
  failure_phase="PRODUCTION_REVEAL_INSTRUMENTATION"
elif [[ "$test_b_force_stop_verified" != true ]]; then
  result_classification="INFRA_BLOCKED"
  failure_reason="APP_FORCE_STOP_BETWEEN_REVEAL_AND_AUTO_NOT_VERIFIED"
  failure_phase="BETWEEN_REVEAL_AND_AUTO"
elif [[ "$cold_start_passed_count" -ne 3 ]]; then
  result_classification="PERFORMANCE_BASELINE_FAILED"
  failure_reason="COLD_START_SESSION_PRELOAD_SAMPLES_INCOMPLETE:$cold_start_passed_count/3"
  failure_phase="COLD_START_SESSION_PRELOAD"
elif [[ "$cold_start_force_stop_verified" != true ]]; then
  result_classification="INFRA_BLOCKED"
  failure_reason="COLD_START_PROCESS_TERMINATION_NOT_VERIFIED"
  failure_phase="COLD_START_SESSION_PRELOAD"
elif [[ "$test_perf_passed" != true ]]; then
  result_classification="PERFORMANCE_BASELINE_FAILED"
  test_perf_reason="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("reason", "RESULT_INVALID"))' \
    "$evidence_dir/test-cases/fishmask-performance-baseline/result.json" 2>/dev/null || printf '%s' 'FISHMASK_PERFORMANCE_BASELINE_RESULT_MISSING')"
  failure_reason="FISH_MASK_PERFORMANCE_BASELINE_FAILED:$test_perf_reason"
  failure_phase="FISH_MASK_PERFORMANCE_BASELINE"
elif [[ "$test_perf_force_stop_verified" != true ]]; then
  result_classification="INFRA_BLOCKED"
  failure_reason="APP_FORCE_STOP_AFTER_PERFORMANCE_BASELINE_NOT_VERIFIED"
  failure_phase="BETWEEN_PERFORMANCE_BASELINE_AND_AUTO"
elif [[ "$test_c_passed" != true ]]; then
  result_classification="BUSINESS_LOGIC_FAILURE"
  test_c_reason="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("reason", "RESULT_INVALID"))' \
    "$evidence_dir/test-cases/production-auto/result.json" 2>/dev/null || printf '%s' 'PRODUCTION_AUTO_TEST_RESULT_MISSING')"
  failure_reason="PRODUCTION_HOME_PICKER_AUTO_INSTRUMENTATION_FAILED:$test_c_reason"
  failure_phase="PRODUCTION_AUTO_INSTRUMENTATION"
elif [[ "$private_evidence_pull_failed" == true ]]; then
  result_classification="INFRA_BLOCKED"
  failure_reason="PRIVATE_EVIDENCE_PULL_FAILED"
  failure_phase="BUSINESS_VALIDATION"
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
