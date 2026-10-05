#!/usr/bin/env bash

android_runtime_preflight() {
  mkdir -p "$YUJIAN_INFRA_DIR"
  local log="$YUJIAN_INFRA_DIR/preflight.log"
  : > "$log"

  runtime_log "ADB devices before readiness check"
  "${YUJIAN_ADB_BIN}" devices -l >> "$log" 2>&1
  local initial_state
  initial_state="$(runtime_adb_state)"
  printf 'ADB_INITIAL_STATE=%s\n' "$initial_state" >> "$log"
  YUJIAN_DEVICE_STATE="$initial_state"

  if [[ "$initial_state" != "device" ]]; then
    runtime_log "Initial ADB state is '$initial_state'; recovery budget permits one restart"
    runtime_restart_adb_once >/dev/null 2>&1 || true
  fi

  if ! runtime_wait_for_device; then
    YUJIAN_DEVICE_STATE="$(runtime_adb_state)"
    runtime_set_failure "PREFLIGHT" "ADB_DEVICE_OFFLINE"
    if [[ "$YUJIAN_DEVICE_STATE" == "" || "$YUJIAN_DEVICE_STATE" == "unknown" ]]; then
      YUJIAN_FAILURE_REASON="ADB_NO_DEVICE"
    fi
    printf 'ADB_WAIT_RESULT=FAIL state=%s\n' "$YUJIAN_DEVICE_STATE" >> "$log"
    return "$EXIT_BLOCKED_INFRA"
  fi

  # Explicit diagnostics are intentionally kept in the preflight log as well
  # as being available through diagnostics.sh on failure.
  "${YUJIAN_ADB_BIN}" shell getprop sys.boot_completed >> "$log" 2>&1 || true
  "${YUJIAN_ADB_BIN}" wait-for-device >> "$log" 2>&1 || true
  "${YUJIAN_ADB_BIN}" devices -l >> "$log" 2>&1 || true
  { "${YUJIAN_ADB_BIN}" shell pm list packages 2>&1 | head -n 20; } >> "$log" 2>&1 || true
  "${YUJIAN_ADB_BIN}" shell df -h /data >> "$log" 2>&1 || true

  local attempt state boot pm_output pm_rc df_output df_rc free_kb
  for attempt in $(seq 1 "$YUJIAN_PREFLIGHT_ATTEMPTS"); do
    state="$(runtime_adb_state)"
    boot="$("${YUJIAN_ADB_BIN}" shell getprop sys.boot_completed 2>&1 | runtime_normalize)"
    pm_output="$("${YUJIAN_ADB_BIN}" shell pm list packages 2>&1)"
    pm_rc=$?
    df_output="$("${YUJIAN_ADB_BIN}" shell df -k /data 2>&1)"
    df_rc=$?
    free_kb="$(awk 'NF >= 4 { value=$4 } END { gsub(/\r/, "", value); print value }' <<< "$df_output")"
    YUJIAN_DEVICE_STATE="$state"
    YUJIAN_BOOT_COMPLETED="$boot"
    YUJIAN_DATA_FREE_KB="$free_kb"

    printf 'READINESS attempt=%s state=%s boot_completed=%s pm_rc=%s df_rc=%s data_free_kb=%s\n' \
      "$attempt" "$state" "$boot" "$pm_rc" "$df_rc" "$free_kb" >> "$log"
    printf '%s\n' "$pm_output" >> "$log"
    printf '%s\n' "$df_output" >> "$log"

    if [[ "$state" == "device" && "$boot" == "1" && "$pm_rc" -eq 0 && "$df_rc" -eq 0 ]]; then
      if [[ "$free_kb" =~ ^[0-9]+$ ]] && (( free_kb < YUJIAN_MIN_DATA_FREE_KB )); then
        runtime_set_failure "PREFLIGHT" "DATA_INSUFFICIENT"
        printf 'DATA_FREE_KB=%s REQUIRED_KB=%s\n' "$free_kb" "$YUJIAN_MIN_DATA_FREE_KB" >> "$log"
        return "$EXIT_BLOCKED_INFRA"
      fi
      if [[ ! "$free_kb" =~ ^[0-9]+$ ]]; then
        runtime_set_failure "PREFLIGHT" "DATA_SPACE_UNREADABLE"
        return "$EXIT_BLOCKED_INFRA"
      fi
      runtime_log "ADB preflight PASS state=$state boot_completed=$boot data_free_kb=$free_kb"
      return "$EXIT_PASS"
    fi

    if [[ "$state" != "device" ]]; then
      runtime_set_failure "PREFLIGHT" "ADB_DEVICE_OFFLINE"
    elif [[ "$boot" != "1" ]]; then
      runtime_set_failure "PREFLIGHT" "BOOT_TIMEOUT"
    elif [[ "$pm_rc" -ne 0 ]]; then
      runtime_set_failure "PREFLIGHT" "PACKAGE_MANAGER_UNAVAILABLE"
    else
      runtime_set_failure "PREFLIGHT" "DATA_SPACE_UNAVAILABLE"
    fi
    sleep "$YUJIAN_PREFLIGHT_SLEEP_SECONDS"
  done

  return "$EXIT_BLOCKED_INFRA"
}
