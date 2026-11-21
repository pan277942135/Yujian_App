#!/usr/bin/env bash
set -uo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 LABEL COMMAND [ARGS...]" >&2
  exit 64
fi
label="$1"
shift
root="${GITHUB_WORKSPACE:-$(pwd)}"
diag="$root/build/diagnostics"
mkdir -p "$diag"
log="$diag/gradle-${label}.log"
time_log="$diag/time-${label}.txt"
samples="$diag/memory-${label}.log"
histogram="$diag/class-histogram-${label}.txt"
summary="$diag/memory-summary-${label}.txt"
module_tasks="$diag/module-tasks-${label}.txt"

# Gradle daemons run with their cwd under GRADLE_USER_HOME/daemon/<version>,
# so relative HeapDumpPath and GC log paths resolve there rather than at the
# repository root. Create the matching directory before the daemon starts, then
# copy those files into the artifact directory after the command completes.
gradle_user_home="${GRADLE_USER_HOME:-${HOME:-$root}/.gradle}"
gradle_bin="$1"
daemon_diagnostics=""
if command -v "$gradle_bin" >/dev/null 2>&1; then
  gradle_version="$("$gradle_bin" --version 2>/dev/null | awk '/^Gradle / { print $2; exit }')"
  if [[ -n "$gradle_version" ]]; then
    daemon_diagnostics="$gradle_user_home/daemon/$gradle_version/build/diagnostics"
    mkdir -p "$daemon_diagnostics"
  fi
fi

memory_cgroup_dir=""
if [[ -r /proc/self/cgroup ]]; then
  v2_path="$(awk -F: '$1 == "0" && $2 == "" { print $3; exit }' /proc/self/cgroup)"
  if [[ -n "$v2_path" && -d "/sys/fs/cgroup${v2_path}" ]]; then
    memory_cgroup_dir="/sys/fs/cgroup${v2_path}"
  else
    v1_path="$(awk -F: '$2 ~ /(^|,)memory(,|$)/ { print $3; exit }' /proc/self/cgroup)"
    if [[ -n "$v1_path" ]]; then
      for candidate in "/sys/fs/cgroup/memory${v1_path}" "/sys/fs/cgroup${v1_path}"; do
        if [[ -d "$candidate" ]]; then memory_cgroup_dir="$candidate"; break; fi
      done
    fi
  fi
fi
# GitHub-hosted runners may expose the cgroup namespace root without the
# process-relative path from /proc/self/cgroup. Probe the mounted root and
# common v1 mount before declaring cgroup telemetry unavailable.
if [[ -z "$memory_cgroup_dir" ]]; then
  for candidate in /sys/fs/cgroup /sys/fs/cgroup/memory; do
    if [[ -r "$candidate/memory.current" || -r "$candidate/memory.usage_in_bytes" ]]; then
      memory_cgroup_dir="$candidate"
      break
    fi
  done
fi
max_daemon_rss_kb=0
max_process_rss_sum_kb=0
max_cgroup_current_bytes=0

cgroup_file() {
  local v2_name="$1" v1_name="$2"
  if [[ -n "$memory_cgroup_dir" && -r "$memory_cgroup_dir/$v2_name" ]]; then
    printf '%s' "$memory_cgroup_dir/$v2_name"
  elif [[ -n "$memory_cgroup_dir" && -r "$memory_cgroup_dir/$v1_name" ]]; then
    printf '%s' "$memory_cgroup_dir/$v1_name"
  fi
}

file_value() {
  local file="$1"
  if [[ -n "$file" && -r "$file" ]]; then
    tr '\n' ' ' < "$file"
  else
    printf 'unavailable'
  fi
}

capture_snapshot() {
  local phase="$1"
  {
    printf '\n=== %s %s ===\n' "$label" "$phase"
    date -u +'%Y-%m-%dT%H:%M:%SZ'
    free -h 2>&1 || true
    cat /proc/meminfo 2>&1 || true
    printf '\n--- /proc/self/cgroup ---\n'
    cat /proc/self/cgroup 2>&1 || true
    printf '\n--- cgroup mounts ---\n'
    awk '$0 ~ / - cgroup2? / { print }' /proc/self/mountinfo 2>&1 || true
    printf '\n--- resolved memory cgroup ---\n%s\n' "${memory_cgroup_dir:-unavailable}"
    for file in "$(cgroup_file memory.max memory.limit_in_bytes)" "$(cgroup_file memory.current memory.usage_in_bytes)" "$(cgroup_file memory.peak memory.max_usage_in_bytes)" "$(cgroup_file memory.events memory.failcnt)"; do
      [[ -n "$file" ]] || continue
      if [[ -r "$file" ]]; then
        printf '\n--- %s ---\n' "$file"
        cat "$file"
      fi
    done
    printf '\n--- Java / Gradle processes ---\n'
    ps -eo pid,ppid,rss,vsz,args --sort=-rss | awk 'NR == 1 || /GradleDaemon|GradleWrapperMain|org\.gradle\.launcher/' | head -30
  } >> "$samples"
}

capture_snapshot before
printf 'label=%s\ncommand=' "$label" | tee "$diag/command-${label}.txt"
printf '%q ' "$@" | tee -a "$diag/command-${label}.txt"
printf '\n' | tee -a "$diag/command-${label}.txt"

if [[ -x /usr/bin/time ]]; then
  /usr/bin/time -v -o "$time_log" "$@" >"$log" 2>&1 &
else
  printf '/usr/bin/time unavailable; wall-clock/resource summary omitted.\n' > "$time_log"
  "$@" >"$log" 2>&1 &
fi
command_pid=$!
histogram_saved=0
last_heap_snapshot=0
while kill -0 "$command_pid" 2>/dev/null; do
  process_state="$(ps -o stat= -p "$command_pid" 2>/dev/null | tr -d ' ' || true)"
  [[ "$process_state" == Z* ]] && break
  now="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  {
    printf '%s ' "$now"
    current_file="$(cgroup_file memory.current memory.usage_in_bytes)"
    if [[ -n "$current_file" ]]; then
      current_bytes="$(cat "$current_file" 2>/dev/null || printf 0)"
      printf 'cgroup_current_bytes=%s ' "$current_bytes"
      if [[ "$current_bytes" =~ ^[0-9]+$ ]] && (( current_bytes > max_cgroup_current_bytes )); then max_cgroup_current_bytes="$current_bytes"; fi
    else
      printf 'cgroup_current_bytes=unavailable '
    fi
    ps -eo pid,rss,args --sort=-rss | awk 'NR == 1 || /GradleDaemon|GradleWrapperMain|org\.gradle\.launcher/' | head -12
    process_rss_sum_kb="$(ps -eo rss= | awk '{ total += $1 } END { printf "%d", total }')"
    printf '%s process_rss_sum_kb=%s\n' "$now" "$process_rss_sum_kb"
    if [[ "$process_rss_sum_kb" =~ ^[0-9]+$ ]] && (( process_rss_sum_kb > max_process_rss_sum_kb )); then max_process_rss_sum_kb="$process_rss_sum_kb"; fi
  } >> "$samples"

  daemon_pid="$(pgrep -f 'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon' | head -1 || true)"
  if [[ -n "$daemon_pid" && -r "/proc/$daemon_pid/status" ]]; then
    rss_kb="$(awk '/^VmRSS:/ {print $2}' "/proc/$daemon_pid/status")"
    if [[ "$rss_kb" =~ ^[0-9]+$ ]] && (( rss_kb > max_daemon_rss_kb )); then max_daemon_rss_kb="$rss_kb"; fi
    if command -v jcmd >/dev/null 2>&1; then
      now_epoch="$(date +%s)"
      if (( now_epoch - last_heap_snapshot >= 30 )); then
        { printf '\n=== heap %s pid=%s ===\n' "$now" "$daemon_pid"; jcmd "$daemon_pid" GC.heap_info 2>&1 || true; } >> "$samples"
        last_heap_snapshot="$now_epoch"
      fi
    fi
  fi

  if (( histogram_saved == 0 )); then
    if [[ -n "$daemon_pid" && -r "/proc/$daemon_pid/status" ]]; then
      rss_kb="$(awk '/^VmRSS:/ {print $2}' "/proc/$daemon_pid/status")"
      xmx="$(tr '\0' ' ' < "/proc/$daemon_pid/cmdline" | sed -nE 's/.*-Xmx([0-9]+)([kKmMgG])[^ ]*.*/\1\2/p' | head -1)"
      threshold_kb=0
      cgroup_limit_file="$(cgroup_file memory.max memory.limit_in_bytes)"
      if [[ -n "$cgroup_limit_file" ]]; then
        cgroup_max="$(cat "$cgroup_limit_file")"
        if [[ "$cgroup_max" =~ ^[0-9]+$ ]]; then threshold_kb=$((cgroup_max * 70 / 100 / 1024)); fi
      fi
      if [[ "$threshold_kb" == 0 && -n "$xmx" ]]; then
        number="${xmx%[kKmMgG]}"; suffix="${xmx:${#number}}"
        case "$suffix" in
          k|K) threshold_kb=$((number * 80 / 100));;
          m|M) threshold_kb=$((number * 1024 * 80 / 100));;
          g|G) threshold_kb=$((number * 1024 * 1024 * 80 / 100));;
        esac
      fi
      if [[ "$rss_kb" =~ ^[0-9]+$ ]] && (( threshold_kb > 0 && rss_kb >= threshold_kb )); then
        if command -v jcmd >/dev/null 2>&1; then
          {
            printf 'Captured at %s; Gradle daemon PID=%s RSS_KB=%s threshold_KB=%s\n' "$now" "$daemon_pid" "$rss_kb" "$threshold_kb"
            jcmd "$daemon_pid" GC.heap_info 2>&1 || true
            jcmd "$daemon_pid" GC.class_histogram -all 2>&1 | head -160 || true
          } > "$histogram"
        else
          printf 'jcmd unavailable at high RSS; PID=%s RSS_KB=%s threshold_KB=%s\n' "$daemon_pid" "$rss_kb" "$threshold_kb" > "$histogram"
        fi
        histogram_saved=1
      fi
    fi
  fi
  sleep 3
 done
wait "$command_pid"
status=$?
capture_snapshot after

grep -E '^> Task :(recognition-domain|recognition-runtime|application-core|waterlight-renderer|recognition-ui|recognition-result-components|recognition-result-ui|app):(compile[^ ]*|test[^ ]*|assemble[^ ]*)' "$log" \
  | sort -u > "$module_tasks" || true

# Retain fresh daemon logs so Kotlin/Gradle teardown and OOM messages remain inspectable.
if [[ -d "${HOME:-}/.gradle/daemon" ]]; then
  mkdir -p "$diag/daemon-logs"
  find "${HOME}/.gradle/daemon" -type f -name 'daemon-*.out.log' -mmin -180 -exec cp --parents {} "$diag/daemon-logs" \; 2>/dev/null || true
fi
if [[ -n "$daemon_diagnostics" && -d "$daemon_diagnostics" ]]; then
  find "$daemon_diagnostics" -maxdepth 1 -type f \( -name '*.hprof' -o -name 'gc-*.log' \) -mmin -180 -exec cp -p {} "$diag/" \; 2>/dev/null || true
fi

# A raw heap dump can exceed artifact limits. Keep a compressed dump only when useful;
# the live histogram above is retained for high-pressure cases even when a dump is large.
while IFS= read -r -d '' dump; do
  compressed="${dump}.gz"
  if gzip -1 -c "$dump" > "$compressed" 2>/dev/null; then
    size="$(stat -c '%s' "$compressed" 2>/dev/null || echo 0)"
    if (( size <= 536870912 )); then
      rm -f "$dump"
      printf 'Compressed heap dump retained: %s (%s bytes)\n' "$compressed" "$size" >> "$samples"
    else
      rm -f "$compressed" "$dump"
      printf 'Heap dump exceeded 512 MiB compressed; raw dump removed after in-run class histogram capture.\n' >> "$samples"
    fi
  else
    rm -f "$compressed"
    printf 'Heap dump compression failed; raw diagnostic left in workspace.\n' >> "$samples"
  fi
done < <(find "$diag" -maxdepth 1 -type f -name '*.hprof' -print0)

# /usr/bin/time wraps Gradle's client process; Gradle may fork a separate
# single-use daemon. Report sampled daemon RSS separately so the client's
# max-RSS value is never mistaken for the compiler JVM's peak.
max_gc_heap_used_kb="$(awk '
  /Pause (Young|Full)/ && /->/ {
    line=$0
    sub(/^.*Pause [^)]*\) /, "", line)
    if (match(line, /[0-9]+[KMG]->/)) {
      value=substr(line, RSTART, RLENGTH-2)
      unit=substr(value, length(value), 1)
      number=substr(value, 1, length(value)-1)
      if (unit == "G") number *= 1024 * 1024
      else if (unit == "M") number *= 1024
      else number /= 1024
      if (number > maximum) maximum=number
    }
  }
  END { printf "%d", maximum }
' "$diag"/gc-*.log 2>/dev/null || printf 0)"
if pgrep -f 'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon' >/dev/null 2>&1; then
  daemon_end_state="running"
else
  daemon_end_state="not-running"
fi
if grep -q 'java.lang.OutOfMemoryError: Java heap space' "$log"; then
  failure_class="java-heap-space"
elif grep -qi 'GC overhead limit exceeded' "$log"; then
  failure_class="gc-overhead-limit"
elif grep -qi 'Metaspace' "$log" && grep -qi 'OutOfMemoryError' "$log"; then
  failure_class="metaspace-oom"
elif (( status == 0 )); then
  failure_class="none"
else
  failure_class="other"
fi
{
  printf 'label=%s\n' "$label"
  printf 'exit_status=%s\n' "$status"
  printf 'failure_class=%s\n' "$failure_class"
  printf 'gradle_daemon_end_state=%s\n' "$daemon_end_state"
  printf 'memory_cgroup_dir=%s\n' "${memory_cgroup_dir:-unavailable}"
  printf 'sampled_max_gradle_daemon_rss_kb=%s\n' "$max_daemon_rss_kb"
  printf 'sampled_max_gradle_daemon_rss_gib=%.2f\n' "$(awk -v value="$max_daemon_rss_kb" 'BEGIN { print value / 1048576 }')"
  printf 'sampled_max_process_rss_sum_kb=%s\n' "$max_process_rss_sum_kb"
  printf 'sampled_max_cgroup_current_bytes=%s\n' "$max_cgroup_current_bytes"
  printf 'cgroup_peak_bytes=%s\n' "$(file_value "$(cgroup_file memory.peak memory.max_usage_in_bytes)")"
  printf 'cgroup_memory_events=%s\n' "$(file_value "$(cgroup_file memory.events memory.failcnt)")"
  printf 'max_gc_heap_used_kb=%s\n' "$max_gc_heap_used_kb"
  printf 'max_gc_heap_used_gib=%.2f\n' "$(awk -v value="$max_gc_heap_used_kb" 'BEGIN { print value / 1048576 }')"
  printf 'gradle_client_time_report=%s\n' "$time_log"
  printf 'module_task_results=%s\n' "$module_tasks"
  printf 'gc_logs=%s\n' "$(find "$diag" -maxdepth 1 -type f -name 'gc-*.log' -printf '%f ' 2>/dev/null || true)"
} > "$summary"

if [[ -s "$log" ]]; then
  echo "Gradle diagnostic log: $log (exit=$status)"
  if (( status == 0 )); then tail -80 "$log"; else tail -240 "$log"; fi
fi
exit "$status"
