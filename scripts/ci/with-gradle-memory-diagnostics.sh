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

capture_snapshot() {
  local phase="$1"
  {
    printf '\n=== %s %s ===\n' "$label" "$phase"
    date -u +'%Y-%m-%dT%H:%M:%SZ'
    free -h 2>&1 || true
    cat /proc/meminfo 2>&1 || true
    for file in /sys/fs/cgroup/memory.max /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.peak /sys/fs/cgroup/memory.events /sys/fs/cgroup/memory/memory.limit_in_bytes /sys/fs/cgroup/memory/memory.usage_in_bytes /sys/fs/cgroup/memory/memory.max_usage_in_bytes; do
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

/usr/bin/time -v -o "$time_log" "$@" >"$log" 2>&1 &
command_pid=$!
histogram_saved=0
last_heap_snapshot=0
while kill -0 "$command_pid" 2>/dev/null; do
  process_state="$(ps -o stat= -p "$command_pid" 2>/dev/null | tr -d ' ' || true)"
  [[ "$process_state" == Z* ]] && break
  now="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  {
    printf '%s ' "$now"
    if [[ -r /sys/fs/cgroup/memory.current ]]; then cat /sys/fs/cgroup/memory.current; else printf 'cgroup_current=unavailable\n'; fi
    if [[ -r /sys/fs/cgroup/memory.peak ]]; then printf 'peak='; cat /sys/fs/cgroup/memory.peak; fi
    ps -eo pid,rss,args --sort=-rss | awk 'NR == 1 || /GradleDaemon|GradleWrapperMain|org\.gradle\.launcher/' | head -12
  } >> "$samples"

  if (( histogram_saved == 0 )); then
    daemon_pid="$(pgrep -f 'org\.gradle\.launcher\.daemon\.bootstrap\.GradleDaemon' | head -1 || true)"
    if [[ -n "$daemon_pid" && -r "/proc/$daemon_pid/status" ]]; then
      rss_kb="$(awk '/^VmRSS:/ {print $2}' "/proc/$daemon_pid/status")"
      if command -v jcmd >/dev/null 2>&1; then
        now_epoch="$(date +%s)"
        if (( now_epoch - last_heap_snapshot >= 30 )); then
          { printf '\n=== heap %s pid=%s ===\n' "$now" "$daemon_pid"; jcmd "$daemon_pid" GC.heap_info 2>&1 || true; } >> "$samples"
          last_heap_snapshot="$now_epoch"
        fi
      fi
      xmx="$(tr '\0' ' ' < "/proc/$daemon_pid/cmdline" | sed -nE 's/.*-Xmx([0-9]+)([kKmMgG])[^ ]*.*/\1\2/p' | head -1)"
      threshold_kb=0
      if [[ -r /sys/fs/cgroup/memory.max ]]; then
        cgroup_max="$(cat /sys/fs/cgroup/memory.max)"
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

# Retain fresh daemon logs so Kotlin/Gradle teardown and OOM messages remain inspectable.
if [[ -d "${HOME:-}/.gradle/daemon" ]]; then
  mkdir -p "$diag/daemon-logs"
  find "${HOME}/.gradle/daemon" -type f -name 'daemon-*.out.log' -mmin -180 -exec cp --parents {} "$diag/daemon-logs" \; 2>/dev/null || true
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

if [[ -s "$log" ]]; then
  echo "Gradle diagnostic log: $log (exit=$status)"
  if (( status == 0 )); then tail -80 "$log"; else tail -240 "$log"; fi
fi
exit "$status"
