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
    command_line="$*"
    # This fake checks Harness classification and evidence-validator routing;
    # it does not claim to render application screenshots or validate video.
    fixture_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)/design/pages/recognition/design"
    if [[ "$command_line" == *recognition_motion_trace_v1_2.json* ]]; then
      printf '{"samples":['
      for index in $(seq 0 9); do
        if (( index > 0 )); then printf ','; fi
        phase=CAPTURED
        (( index >= 3 )) && phase=OUTLINE
        (( index >= 6 )) && phase=CLASSIFYING
        resolve=1.0
        (( index >= 8 )) && resolve=0.5
        speed=0.5
        (( index >= 8 )) && speed=0.0
        printf '{"uptime_ms":%s,"phase":"%s","segment_offset":0.%s,"segment_speed":%s,"state_strength":0.5,"resolve_strength":%s,"detail_motion_time_ms":1008}' "$((1000 + index))" "$phase" "$index" "$speed" "$resolve"
      done
      printf ']}\n'
      exit 0
    fi
    if [[ "$command_line" == *recognition_production_flow_trace_v1_2.json* ]]; then
      printf '{"pipeline_phases":["CAPTURED","DETECTING","OUTLINE","CLASSIFYING","RESULT"],"presentation_events":[{"state":"IMAGE_RECOGNIZING","at_ms":0},{"state":"FISH_LOCATED","at_ms":900},{"state":"SPECIES_RECOGNIZING","at_ms":1500},{"state":"RESULT","at_ms":2950}],"result_ready":true,"bbox":{"x1":0.1,"y1":0.1,"x2":0.8,"y2":0.9}}\n'
      exit 0
    fi
    if [[ "$command_line" == *recognition_accessibility_trace_v1_2.json* ]]; then
      printf '{"degradation":{"D0":"FULL+A","D1":"BALANCED+A","D2":"LITE+A","D3":"LITE+B","D4":"LITE+C"}}\n'
      exit 0
    fi
    if [[ "$command_line" == *fish_focus_bbox_mapping.json* ]]; then
      printf '{"detector_bbox_normalized":{"x1":0.1,"y1":0.1,"x2":0.8,"y2":0.9}}\n'
      exit 0
    fi
    if [[ "$command_line" =~ cache/recognition-evidence/([0-9][0-9]_[a-z_]+\.png) ]]; then
      name="${BASH_REMATCH[1]}"
      case "$name" in
        01_image_recognizing_early.png) cat "$fixture_root/01_Capture_Transition_Frozen.png" ;;
        02_image_recognizing_late.png) cat "$fixture_root/02_AI_Understanding_Frozen.png" ;;
        03_fish_located.png) cat "$fixture_root/03_Fish_Highlight_Frozen.png" ;;
        04_species_recognizing.png) cat "$fixture_root/04_Fish_Identifying_Frozen.png" ;;
        05_result_high.png) cat "$fixture_root/05_Result_High_Frozen.png" ;;
        06_result_medium.png) cat "$fixture_root/06_Result_Medium_Frozen.png" ;;
        07_result_low.png) cat "$fixture_root/07_Result_Low_Frozen.png" ;;
        08_issue_no_fish.png) cat "$fixture_root/08_Error_No_Fish_Frozen.png" ;;
        09_issue_image_quality.png) cat "$fixture_root/09_Error_Image_Quality_Frozen.png" ;;
        10_issue_technical_failure.png) cat "$fixture_root/09_Error_Image_Quality_Frozen.png" ;;
      esac
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
    if [[ "$command_line" == *'wc -c'*'recognition_processing_runtime_host.mp4'* ]]; then
      if [[ "$MODE" == "missing-evidence" ]]; then
        printf '0\n'
      else
        printf '4096\n'
      fi
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
      if [[ "$MODE" == "process-crash" ]]; then
        printf 'INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n'
        exit 0
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
