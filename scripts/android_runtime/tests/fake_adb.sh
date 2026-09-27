#!/usr/bin/env bash

# Minimal fake adb used by test_contract.sh.  It models only the transport,
# package, instrumentation and evidence calls made by the unified Harness.
set -u

MODE="${FAKE_ADB_MODE:-ready}"
RESTART_LOG="${FAKE_ADB_RESTART_LOG:-}"

record_restart() {
  if [[ -n "$RESTART_LOG" ]]; then
    printf '%s\n' "$1" >> "$RESTART_LOG"
  fi
}

if [[ $# -eq 0 ]]; then
  exit 0
fi

case "$1" in
  devices)
    if [[ "$MODE" == "offline" ]]; then
      printf 'List of devices attached\nemulator-5554 offline\n'
    else
      printf 'List of devices attached\nemulator-5554 device product:fake model:fake transport_id:1\n'
    fi
    exit 0
    ;;
  get-state)
    if [[ "$MODE" == "offline" ]]; then printf 'offline\n'; else printf 'device\n'; fi
    exit 0
    ;;
  kill-server)
    record_restart kill-server
    exit 0
    ;;
  start-server)
    record_restart start-server
    exit 0
    ;;
  wait-for-device)
    [[ "$MODE" != "offline" ]]
    exit $?
    ;;
  install)
    if [[ "$MODE" == "invalid-apk" ]]; then
      printf 'Failure [INSTALL_FAILED_INVALID_APK]\n'
      exit 1
    fi
    printf 'Success\n'
    exit 0
    ;;
  uninstall)
    printf 'Failure [DELETE_FAILED_INTERNAL_ERROR]\n'
    exit 1
    ;;
  exec-out)
    if [[ "$MODE" == "missing-evidence" ]]; then
      exit 0
    fi
    printf 'fake-evidence\n'
    exit 0
    ;;
  pull)
    if [[ "$MODE" == "missing-evidence" ]]; then
      exit 1
    fi
    destination="${!#}"
    mkdir -p "$(dirname "$destination")"
    printf 'fake-video\n' > "$destination"
    exit 0
    ;;
  logcat)
    exit 0
    ;;
  shell)
    shift
    command_line="$*"
    if [[ "$MODE" == "offline" ]]; then
      printf 'error: device offline\n' >&2
      exit 1
    fi
    if [[ "$command_line" == *'getprop sys.boot_completed'* ]]; then
      if [[ "$MODE" == "boot-timeout" ]]; then printf '0\n'; else printf '1\n'; fi
      exit 0
    fi
    if [[ "$command_line" == *'pm list packages'* || "$command_line" == *'cmd package list packages'* ]]; then
      printf 'package:com.example.fake\n'
      exit 0
    fi
    if [[ "$command_line" == *'df -k /data'* ]]; then
      if [[ "$MODE" == "low-disk" ]]; then
        printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\n/dev/fake 1000000 999900 100 100%% /data\n'
      else
        printf 'Filesystem 1K-blocks Used Available Use%% Mounted on\n/dev/fake 1000000 100000 900000 10%% /data\n'
      fi
      exit 0
    fi
    if [[ "$command_line" == *'df -h /data'* ]]; then
      printf 'Filesystem Size Used Avail Use%% Mounted on\n/dev/fake 1G 100M 900M 10%% /data\n'
      exit 0
    fi
    if [[ "$command_line" == *'pm path com.yujian.ai.uiv2.test'* || "$command_line" == *'pm path com.yujian.ai.uiv2'* ]]; then
      printf 'package:/data/app/fake/base.apk\n'
      exit 0
    fi
    if [[ "$command_line" == *'am instrument'* ]]; then
      if [[ "$MODE" == "test-fail" ]]; then
        printf 'INSTRUMENTATION_STATUS: numtests=1\nFAILURES!!!\nINSTRUMENTATION_CODE: -1\n'
        exit 1
      fi
      printf 'INSTRUMENTATION_RESULT: stream=\nOK (1 test)\nINSTRUMENTATION_CODE: 0\n'
      exit 0
    fi
    if [[ "$command_line" == *'getprop'* ]]; then
      printf '[ro.build.version.sdk]: [28]\n'
      exit 0
    fi
    if [[ "$command_line" == *'pidof'* || "$command_line" == *'monkey'* || "$command_line" == *'screenrecord'* || "$command_line" == *'screencap'* || "$command_line" == *'pm clear'* || "$command_line" == *'settings put'* || "$command_line" == *'wm size'* ]]; then
      printf 'fake-shell-command\n'
      exit 0
    fi
    exit 0
    ;;
  *)
    exit 0
    ;;
esac
