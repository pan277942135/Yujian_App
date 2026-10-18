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
    if [[ "$command_line" == *cache/recognition-first-use-camera-guide/first_use_camera_guide_runtime_report.json* ]]; then
      # The evidence contract binds this report to the exact host APK supplied
      # by test_contract.sh. Other fake evidence remains clearly synthetic.
      printf '{"apk_sha256":"%s"}\n' "${FAKE_APP_APK_SHA256:-}"
      exit 0
    fi
    if [[ "$command_line" == *cache/recognition-first-use-camera-guide/P01_compose_component.png* ]]; then
      cat "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../design/pages/recognition/first_use_camera_guide/frozen" && pwd)/P01_First_Use_Camera_Guide_Frozen.png"
      exit 0
    fi
    if [[ "$command_line" == *cache/recognition-first-use-camera-guide/P02_compose_component.png* ]]; then
      cat "$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../design/pages/recognition/first_use_camera_guide/frozen" && pwd)/P02_First_Use_Camera_Guide_Frozen.png"
      exit 0
    fi
    # This fake checks Harness classification and evidence-validator routing;
    # it does not claim to render application screenshots or validate video.
    fixture_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)/design/pages/recognition/design"
    if [[ "$command_line" == *recognition_motion_trace_m1_v3_1.json* ]]; then
      printf '{"contract_version":"recognition_processing_state_machine_v3_1","motion_authority":"design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_2.md","motion_authority_sha256":"00df6a7c28a76182a44f8f2008918dc533f5640114496b132a759d72e61ddc7c","samples":['
      printf '{"uptime_ms":1000,"phase":"CAPTURED","segment_offset":0.1,"segment_speed":1.0,"state_strength":0.5,"detail_motion_time_ms":1000,"contour_core_alpha_target":null,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1100,"phase":"CAPTURED","segment_offset":0.2,"segment_speed":1.0,"state_strength":0.7,"detail_motion_time_ms":1100,"contour_core_alpha_target":null,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1200,"phase":"DETECTING","segment_offset":0.3,"segment_speed":1.0,"state_strength":0.7,"detail_motion_time_ms":1200,"contour_core_alpha_target":null,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1300,"phase":"DETECTING","segment_offset":0.4,"segment_speed":1.0,"state_strength":0.8,"detail_motion_time_ms":1300,"contour_core_alpha_target":null,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1400,"phase":"OUTLINE","segment_offset":0.5,"segment_speed":0.62,"state_strength":0.7,"detail_motion_time_ms":1400,"contour_core_alpha_target":null,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1500,"phase":"OUTLINE","segment_offset":0.6,"segment_speed":0.62,"state_strength":0.6,"detail_motion_time_ms":1500,"contour_core_alpha_target":null,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1600,"phase":"CLASSIFYING","segment_offset":0.7,"segment_speed":0.42,"state_strength":0.4,"detail_motion_time_ms":1600,"contour_core_alpha_target":0.4,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1700,"phase":"CLASSIFYING","segment_offset":0.8,"segment_speed":0.42,"state_strength":0.4,"detail_motion_time_ms":1700,"contour_core_alpha_target":0.4,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1800,"phase":"CLASSIFYING","segment_offset":0.9,"segment_speed":0.42,"state_strength":0.4,"detail_motion_time_ms":1800,"contour_core_alpha_target":0.4,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":1900,"phase":"CLASSIFYING","segment_offset":0.1,"segment_speed":0.42,"state_strength":0.4,"detail_motion_time_ms":1900,"contour_core_alpha_target":0.4,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"},'
      printf '{"uptime_ms":2000,"phase":"CLASSIFYING","segment_offset":0.2,"segment_speed":0.42,"state_strength":0.4,"detail_motion_time_ms":2000,"contour_core_alpha_target":0.4,"resolve_strength":1.0,"resolve_fade_started_at_ms":null,"reduce_motion":false,"quality":"FULL"}]}'
      exit 0
    fi
    if [[ "$command_line" == *recognition_production_flow_trace_m1_v3_1.json* ]]; then
      detecting_at=1220
      [[ "${FAKE_M1_SHORT_BEAT:-0}" != "1" ]] || detecting_at=1219
      printf '{"contract_version":"recognition_processing_state_machine_v3_1","timing_authority":"design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json","timing_authority_sha256":"fa4f9f7ea371dd46f8a92be8fc0398bee465c05356a8d1d5623a46120d07de02","visual_state_sequence":["CAPTURED","DETECTING","OUTLINE","CLASSIFYING","RESULT_READY"],"minimum_durations_ms":{"CAPTURED":220,"DETECTING":320,"OUTLINE":350,"CLASSIFYING":250,"RESULT_READY":0},"motion_authority":"design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_2.md","motion_authority_sha256":"00df6a7c28a76182a44f8f2008918dc533f5640114496b132a759d72e61ddc7c","pipeline_phases":["CAPTURED","DETECTING","OUTLINE","CLASSIFYING","RESULT"],"presentation_events":[{"state":"CAPTURED","at_ms":1000},{"state":"DETECTING","at_ms":%s},{"state":"OUTLINE","at_ms":1540},{"state":"CLASSIFYING","at_ms":1890},{"state":"RESULT_READY","at_ms":2140}],"pipeline_events":[{"phase":"CAPTURED","at_ms":990},{"phase":"DETECTING","at_ms":1190},{"phase":"OUTLINE","at_ms":1510},{"phase":"CLASSIFYING","at_ms":1800},{"phase":"RESULT","at_ms":2000}],"captured_ms":%s,"detecting_ms":320,"outline_ms":350,"classifying_ms":250,"total_ms":1140,"classifying_breath_period_ms":1900,"result_ready_at_ms":2140,"resolve_fade_ms":200,"resolve_fade_started_at_ms":null,"resolve_fade_first_visible_at_ms":null,"resolve_fade_observed_ms":null,"resolve_fade_observed_meets_motion_min":false,"result_ready":true,"source":{"width":1080,"height":1920},"quality_gate":{"classifier_eligible":true},"focus":{"level":"A"},"bbox":{"x1":0.1,"y1":0.1,"x2":0.8,"y2":0.9}}\n' "$detecting_at" "$((detecting_at - 1000))"
      exit 0
    fi
    if [[ "$command_line" == *recognition_processing_timing.txt* ]]; then
      captured_ms=220
      [[ "${FAKE_M1_SHORT_BEAT:-0}" != "1" ]] || captured_ms=219
      printf 'contract_version=recognition_processing_state_machine_v3_1\ntiming_authority=design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json\ntiming_authority_sha256=fa4f9f7ea371dd46f8a92be8fc0398bee465c05356a8d1d5623a46120d07de02\ncaptured_min_ms=220\ndetecting_min_ms=320\noutline_min_ms=350\nclassifying_min_ms=250\ncaptured_ms=%s\ndetecting_ms=320\noutline_ms=350\nclassifying_ms=250\ntotal_ms=1140\nresult_ready_at_ms=2140\nresolve_motion_authority=design/pages/recognition/processing/motion/Recognition_Processing_Motion_Spec_V1_2.md\nresolve_motion_authority_sha256=00df6a7c28a76182a44f8f2008918dc533f5640114496b132a759d72e61ddc7c\nresolve_fade_ms=200\nclassifying_breath_period_ms=1900\nresolve_fade_started_at_ms=null\nresolve_fade_first_visible_at_ms=null\nresolve_fade_observed_ms=null\nresolve_fade_observed_meets_motion_min=false\n' "$captured_ms"
      exit 0
    fi
    if [[ "$command_line" == *recognition_production_flow_trace.txt* ]]; then
      printf 'REAL_BBOX=0.1,0.1,0.8,0.9 confidence=0.95\nSUBJECT_STATUS=PASS\nSUBJECT_QUALITY=HIGH\nSUBJECT_MASK_AREA=0.5\nSUBJECT_WIDTH=300\nSUBJECT_HEIGHT=250\nCONTOUR_SEGMENTS=8\nFOCUS_RENDER_MODE=LEVEL_A\nLOW_PERFORMANCE=false\nREDUCE_MOTION=false\nRESULT species=black_bass confidence=0.9\n'
      exit 0
    fi
    if [[ "$command_line" == *recognition_visual_qa_v1_3.json* ]]; then
      printf '{"findings":{"F01":{"status":"UNREVIEWED"},"F02":{"status":"UNREVIEWED"},"F03":{"status":"UNREVIEWED"},"F04":{"status":"UNREVIEWED"},"F05":{"status":"UNREVIEWED"},"F06":{"status":"UNREVIEWED"}}}\n'
      exit 0
    fi
    if [[ "$command_line" == *recognition_accessibility_trace_v3_1.json* ]]; then
      printf '{"degradation":{"D0":"FULL+A","D1":"BALANCED+A","D2":"LITE+A","D3":"LITE+B","D4":"LITE+C"}}\n'
      exit 0
    fi
    if [[ "$command_line" == *fish_focus_bbox_mapping.json* ]]; then
      printf '{"detector_bbox_normalized":{"x1":0.1,"y1":0.1,"x2":0.8,"y2":0.9}}\n'
      exit 0
    fi
    if [[ "$command_line" == *cache/recognition-optical-fidelity/recognition_optical_fidelity_v3_1.json* ]]; then
      python3 - <<'PY'
import json
intervals = [16.67] * 120
def profile(name):
    return {
        "profile": name,
        "phase": "CLASSIFYING",
        "sample_count": len(intervals),
        "motion_sample_count": 120,
        "refresh_rate_hz": 60.0,
        "interval_ms": {
            "mean": 16.67,
            "median": 16.67,
            "p95": 16.67,
            "max": 16.67,
            "frames_over_32ms": 0,
        },
        "samples_ms": intervals,
        "budget_policy": "MEASURED_ONLY_NO_FROZEN_THRESHOLD",
    }
print(json.dumps({
    "contract_version": "recognition_processing_state_machine_v3_1",
    "measurement_method": "Choreographer.FrameCallback frame intervals while production RecognitionProcessingScene is live (synthetic fake-adb contract fixture)",
    "phase": "CLASSIFYING",
    "fixture": "fake",
    "refresh_rate_hz": 60.0,
    "budget_policy": "MEASURED_ONLY_NO_FROZEN_THRESHOLD",
    "profiles": [profile("FULL"), profile("LITE")],
    "presented_phases": ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING"],
    "result_ready": False,
}))
PY
      exit 0
    fi
    if [[ "$command_line" =~ cache/recognition-optical-fidelity/([A-Za-z0-9_.-]+\.png) ]]; then
      cat "$fixture_root/05_Result_High_Frozen.png"
      exit 0
    fi
    if [[ "$command_line" =~ cache/recognition-evidence/([A-Za-z0-9_.-]+\.png) ]]; then
      name="${BASH_REMATCH[1]}"
      case "$name" in
        01_image_recognizing_early.png) cat "$fixture_root/01_Capture_Transition_Frozen.png" ;;
        02_image_recognizing_late.png) cat "$fixture_root/02_AI_Understanding_Frozen.png" ;;
        03_fish_located.png) cat "$fixture_root/03_Fish_Highlight_Frozen.png" ;;
        04_species_recognizing.png) cat "$fixture_root/04_Fish_Identifying_Frozen.png" ;;
        05_result_high.png) cat "$fixture_root/05_Result_High_Frozen.png" ;;
        06_result_medium.png) cat "$fixture_root/06_Result_Medium_Frozen.png" ;;
        07_result_low.png) cat "$fixture_root/07_Result_Low_Frozen.png" ;;
        08_error_no_fish.png) cat "$fixture_root/08_Error_No_Fish_Frozen.png" ;;
        09_error_image_quality.png) cat "$fixture_root/09_Error_Image_Quality_Frozen.png" ;;
        10_issue_technical_failure.png) cat "$fixture_root/09_Error_Image_Quality_Frozen.png" ;;
        *) cat "$fixture_root/05_Result_High_Frozen.png" ;;
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
    if [[ "$command_line" == *'getprop ro.product.model'* ]]; then printf 'Pixel 2\n'; exit 0; fi
    if [[ "$command_line" == *'getprop qemu.avd_name'* ]]; then printf 'yujian-api28\n'; exit 0; fi
    if [[ "$command_line" == *'settings get system font_scale'* ]]; then printf '1.0\n'; exit 0; fi
    if [[ "$command_line" == *'wm size'* ]]; then printf 'Physical size: 1080x1920\nOverride size: 1080x1920\n'; exit 0; fi
    if [[ "$command_line" == *'wm density'* ]]; then printf 'Physical density: 480\nOverride density: 480\n'; exit 0; fi
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
      if [[ "$MODE" == "camera-closed" ]]; then
        printf 'INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS: stack=androidx.camera.core.ImageCaptureException: Camera is closed.\nFAILURES!!!\nINSTRUMENTATION_CODE: -1\n'
        exit 1
      fi
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
