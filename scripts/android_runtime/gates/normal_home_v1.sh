#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.ui.home.NormalHomeRealPhotoProbeTest,com.yujian.ai.ui.components.HomeEvidenceBitmapTest,com.yujian.ai.NormalHomeRuntimeContractTest,com.yujian.ai.ui.home.HomeStatsSemanticsTest,com.yujian.ai.ui.home.NormalHomeHeroBehaviorTest,com.yujian.ai.ui.home.NormalHomeDataParityTest,com.yujian.ai.ui.home.NormalHomeMotionEvidenceTest'
}

gate_additional_test_classes() {
  printf '%s\n' 'com.yujian.ai.ui.components.FishPhotoRendererRuntimeTest'
}

gate_before_instrumentation() {
  "$YUJIAN_ADB_BIN" logcat -c >/dev/null 2>&1 || true
}

gate_after_instrumentation() {
  local output_dir="$YUJIAN_EVIDENCE_DIR/normal-home-v1"
  mkdir -p "$output_dir"
  "$YUJIAN_ADB_BIN" logcat -d -v brief -s \
    NORMAL_HOME_MOTION_TRACE:I NORMAL_HOME_AVATAR_STATE_TRACE:I FishPhotoCropAcceptance:I \
    > "$output_dir/normal-home-motion-instrumentation.log" 2>&1 || true
}

normal_home_run_seed() {
  local method="$1"
  local log="$2"
  timeout 60s "$YUJIAN_ADB_BIN" shell am instrument -w -r \
    -e class "com.yujian.ai.HomeVisualEvidenceSeedTest#$method" \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$log" 2>&1
  local rc=$?
  if (( rc != 0 )) || grep -Eiq 'FAILURES!!!|INSTRUMENTATION_FAILED|Assertion(Error|FailedError)|Process (crashed|has died)|Process .* (crashed|has died)|shortMsg=Process crashed' "$log"; then
    cat "$log" >&2
    return 1
  fi
  grep -Eq 'INSTRUMENTATION_CODE:[[:space:]]*0|OK \([0-9]+ test' "$log"
}

normal_home_launch_app() {
  "$YUJIAN_ADB_BIN" shell am force-stop "$YUJIAN_APP_PACKAGE"
  "$YUJIAN_ADB_BIN" shell monkey -p "$YUJIAN_APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

  local attempt resumed pid
  for attempt in $(seq 1 20); do
    resumed="$("$YUJIAN_ADB_BIN" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|ResumedActivity' | head -n 1 || true)"
    pid="$("$YUJIAN_ADB_BIN" shell pidof "$YUJIAN_APP_PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    printf 'NORMAL_HOME_ACTIVITY_READINESS attempt=%s resumed=%s pid=%s\n' "$attempt" "$resumed" "$pid"
    if [[ "$resumed" == *"$YUJIAN_APP_PACKAGE/com.yujian.ai.MainActivity"* && "$pid" =~ [0-9] ]]; then
      # UIAutomator's Compose bridge is unreliable and can hang on API 28.
      # Wait on real rendered pixels instead. Normal Home intentionally loads
      # its scene/card/photo assets asynchronously, so an Activity-resumed
      # signal alone is not sufficient evidence that the production frame is
      # ready for a frozen-parity capture.
      local candidate="$YUJIAN_EVIDENCE_DIR/normal-home-v1/render_readiness.png"
      local render_attempt
      for render_attempt in $(seq 1 20); do
        "$YUJIAN_ADB_BIN" exec-out screencap -p > "$candidate"
        local fatal_runtime
        fatal_runtime="$("$YUJIAN_ADB_BIN" logcat -d -v brief -s AndroidRuntime:E 2>/dev/null | grep -E 'FATAL EXCEPTION|Process: com\.yujian\.ai' | tail -n 4 || true)"
        if [[ -n "$fatal_runtime" ]]; then
          printf 'NORMAL_HOME_APP_CRASH_DETECTED\n%s\n' "$fatal_runtime" >&2
          return 1
        fi
        if python3 - "$candidate" <<'PY'
from PIL import Image, ImageStat
import sys

image = Image.open(sys.argv[1]).convert("RGB")
if image.width < 200 or image.height < 400:
    raise SystemExit("NORMAL_HOME_FRAME_INVALID")

def crop(x, y, w, h):
    return image.crop((
        int(image.width * x),
        int(image.height * y),
        int(image.width * (x + w)),
        int(image.height * (y + h)),
    ))

# The screen must be the Normal Home data page, not merely any resumed
# activity. The blue/gray lake and photographic Hero reject the API28 launcher
# wallpaper that previously passed the loose spread-only check.
background = crop(0.04, 0.16, 0.92, 0.18)
card_photo = crop(0.18, 0.33, 0.64, 0.28)
bg_stat = ImageStat.Stat(background)
card_stat = ImageStat.Stat(card_photo)
bg_spread = sum(bg_stat.stddev) / 3.0
card_spread = sum(card_stat.stddev) / 3.0
bg_mean = sum(bg_stat.mean) / 3.0
card_mean = sum(card_stat.mean) / 3.0
lake_blue_over_red = bg_stat.mean[2] - bg_stat.mean[0]
print(
    "NORMAL_HOME_RENDER_READINESS "
    f"bg_mean={bg_mean:.2f} bg_spread={bg_spread:.2f} "
    f"lake_blue_over_red={lake_blue_over_red:.2f} "
    f"card_mean={card_mean:.2f} card_spread={card_spread:.2f}"
)
if lake_blue_over_red < 6.0 or card_spread < 35.0 or card_mean > 225.0:
    raise SystemExit("NORMAL_HOME_RENDER_NOT_SETTLED")
PY
        then
          printf 'NORMAL_HOME_RENDER_READY attempt=%s\n' "$render_attempt"
          return 0
        fi
        printf 'NORMAL_HOME_RENDER_SETTLE attempt=%s\n' "$render_attempt"
        sleep 1
      done
      echo 'NORMAL_HOME_RENDER_NOT_READY' >&2
      return 1
    fi
    sleep 1
  done
  echo 'NORMAL_HOME_ACTIVITY_NOT_READY' >&2
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$YUJIAN_EVIDENCE_DIR/normal-home-v1/content_not_ready.png" || true
  return 1
}

normal_home_capture_exact() {
  local path="$1" width="$2" height="$3"
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$path"
  python3 - "$path" "$width" "$height" <<'PY'
from PIL import Image
import sys
path, expected_w, expected_h = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
with Image.open(path) as image:
    actual = image.size
print(f"SCREENSHOT_DIMENSIONS file={path} actual={actual[0]}x{actual[1]} expected={expected_w}x{expected_h}")
if actual != (expected_w, expected_h):
    raise SystemExit(10)
PY
}

normal_home_prepare_long_display() {
  local setup_log="$YUJIAN_EVIDENCE_DIR/infra/long_screen_device_setup.log"
  local probe="$YUJIAN_EVIDENCE_DIR/infra/long_screen_probe.png"
  local actual serial avd_name emulator_bin emulator_log attempt boot api
  mkdir -p "$(dirname "$setup_log")"
  : > "$setup_log"
  "$YUJIAN_ADB_BIN" shell wm size 1080x2340 >> "$setup_log" 2>&1 || true
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$probe" 2>> "$setup_log" || true
  actual="$(python3 - "$probe" <<'PY'
from PIL import Image
import sys
try:
    with Image.open(sys.argv[1]) as image:
        print(f"{image.width}x{image.height}")
except Exception:
    print("unavailable")
PY
)"
  printf 'wm_size_capture_probe=%s\n' "$actual" | tee -a "$setup_log"
  if [[ "$actual" == "1080x2340" ]]; then
    return 0
  fi

  serial="$("$YUJIAN_ADB_BIN" get-serialno 2>/dev/null | tr -d '\r' || true)"
  avd_name="$("$YUJIAN_ADB_BIN" shell getprop qemu.avd_name 2>/dev/null | tr -d '\r' || true)"
  local startup_log="$YUJIAN_EVIDENCE_DIR/infra/emulator.log"
  local sdk_root
  if [[ -z "$avd_name" && "$serial" == emulator-* ]]; then
    local avd_console
    avd_console="$("$YUJIAN_ADB_BIN" -s "$serial" emu avd name 2>/dev/null | tr -d '\r' || true)"
    avd_name="$(printf '%s\n' "$avd_console" | sed -E '/^[[:space:]]*OK[[:space:]]*$/d; /^[[:space:]]*$/d; s/^[[:space:]]*Name:[[:space:]]*//' | tail -n 1)"
    printf 'resolved_avd_name=%s source=adb_emulator_console\n' "$avd_name" | tee -a "$setup_log"
  fi
  if [[ -z "$avd_name" && -s "$startup_log" ]]; then
    avd_name="$(sed -nE 's/.*qemu\.avd_name=([^[:space:]]+).*/\1/p' "$startup_log" | tail -n 1)"
    if [[ -z "$avd_name" ]]; then
      avd_name="$(sed -nE 's/.*requirements to run avd: `([^`]+)`.*/\1/p' "$startup_log" | tail -n 1)"
    fi
    printf 'resolved_avd_name=%s source=%s\n' "$avd_name" "$startup_log" | tee -a "$setup_log"
  fi
  sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  emulator_bin=""
  if [[ -n "$sdk_root" && -x "$sdk_root/emulator/emulator" ]]; then
    emulator_bin="$sdk_root/emulator/emulator"
  else
    local emulator_candidate
    emulator_candidate="$(command -v emulator || true)"
    if [[ -n "$emulator_candidate" && -x "$emulator_candidate" ]]; then
      emulator_bin="$emulator_candidate"
    fi
  fi
  if [[ "$serial" != emulator-* || -z "$avd_name" || -z "$emulator_bin" ]]; then
    printf 'LONG_SCREEN_NATIVE_PROFILE_UNAVAILABLE serial=%s avd=%s emulator=%s\n' \
      "$serial" "$avd_name" "$emulator_bin" | tee -a "$setup_log"
    return "$EXIT_BLOCKED_INFRA"
  fi

  emulator_log="$YUJIAN_EVIDENCE_DIR/infra/emulator_long_screen.log"
  printf 'restart_serial=%s avd=%s skin=1080x2340\n' "$serial" "$avd_name" | tee -a "$setup_log"
  "$YUJIAN_ADB_BIN" -s "$serial" emu kill >> "$setup_log" 2>&1 || true
  for attempt in $(seq 1 30); do
    if ! "$YUJIAN_ADB_BIN" devices | awk 'NR > 1 && $2 == "device" {found=1} END {exit !found}'; then
      break
    fi
    sleep 1
  done
  nohup "$emulator_bin" -avd "$avd_name" -no-snapshot -no-window \
    -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none \
    -partition-size 4096 -skin 1080x2340 > "$emulator_log" 2>&1 &
  printf 'restarted_emulator_pid=%s\n' "$!" | tee -a "$setup_log"
  timeout 120s "$YUJIAN_ADB_BIN" wait-for-device >> "$setup_log" 2>&1 || return "$EXIT_BLOCKED_INFRA"
  boot=0
  for attempt in $(seq 1 60); do
    api="$("$YUJIAN_ADB_BIN" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || true)"
    if [[ "$("$YUJIAN_ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" == "1" && "$api" == "28" ]]; then
      boot=1
      break
    fi
    sleep 2
  done
  if (( boot != 1 )); then
    printf 'LONG_SCREEN_EMULATOR_BOOT_FAILED api=%s\n' "$api" | tee -a "$setup_log"
    return "$EXIT_BLOCKED_INFRA"
  fi

  # Preserve the runner's previous logical profile after the wider native skin
  # has supplied a real 1080x2340 capture surface for this job.
  original_override="1080x1920"
  "$YUJIAN_ADB_BIN" shell wm size reset >> "$setup_log" 2>&1 || true
  "$YUJIAN_ADB_BIN" shell wm density 480 >> "$setup_log" 2>&1 || true
  "$YUJIAN_ADB_BIN" shell settings put system font_scale 1.0 >> "$setup_log" 2>&1 || true
  "$YUJIAN_ADB_BIN" exec-out screencap -p > "$probe" 2>> "$setup_log" || true
  actual="$(python3 - "$probe" <<'PY'
from PIL import Image
import sys
try:
    with Image.open(sys.argv[1]) as image:
        print(f"{image.width}x{image.height}")
except Exception:
    print("unavailable")
PY
)"
  printf 'native_skin_capture_probe=%s\n' "$actual" | tee -a "$setup_log"
  if [[ "$actual" != "1080x2340" ]]; then
    tail -n 80 "$emulator_log" >> "$setup_log" 2>&1 || true
    return "$EXIT_BLOCKED_INFRA"
  fi
  return 0
}

normal_home_write_profile_record() {
  local screenshot="$1" profile="$2" size="$3" scale="$4" status="$5" reason="$6"
  local output="$YUJIAN_EVIDENCE_DIR/normal-home-v1/normal_home_profile_${profile}.json"
  python3 - "$screenshot" "$output" "$BUILD_SHA" "$profile" "$size" "$scale" \
    "$YUJIAN_ADB_BIN" "$YUJIAN_APP_APK" "$status" "$reason" <<'PY'
import hashlib, json, pathlib, subprocess, sys
from PIL import Image
image_path, output, build_sha, profile, size, font_scale, adb, apk, status, reason = sys.argv[1:]
def shell(*args):
    return subprocess.check_output([adb, "shell", *args], text=True).strip().replace("\r", "")
with Image.open(image_path) as image:
    dimensions = list(image.size)
payload = {
    "profile_id": profile,
    "status": status,
    "reason": reason or None,
    "page_state": "NORMAL_HOME_DATA",
    "account_state": "GUEST",
    "api_level": shell("getprop", "ro.build.version.sdk"),
    "device": shell("getprop", "ro.product.model"),
    "device_serial": subprocess.check_output([adb, "get-serialno"], text=True).strip(),
    "resolution_override": size,
    "reported_wm_size": shell("wm", "size"),
    "density_dpi": 480,
    "font_scale": float(font_scale),
    "motion_settings": {
        "animator_duration_scale": shell("settings", "get", "global", "animator_duration_scale"),
        "transition_animation_scale": shell("settings", "get", "global", "transition_animation_scale"),
        "window_animation_scale": shell("settings", "get", "global", "window_animation_scale"),
    },
    "apk_sha256": hashlib.sha256(pathlib.Path(apk).read_bytes()).hexdigest(),
    "source_commit": build_sha,
    "screenshot": {
        "path": pathlib.Path(image_path).name,
        "sha256": hashlib.sha256(pathlib.Path(image_path).read_bytes()).hexdigest(),
        "dimensions": dimensions,
        "native_capture": True,
    },
    "window_metrics_log": pathlib.Path(output).with_name(f"normal_home_profile_{profile}_metrics.log").name,
    "window_dump": pathlib.Path(output).with_name(f"normal_home_profile_{profile}_window.txt").name,
}
pathlib.Path(output).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(payload, ensure_ascii=False))
PY
}

normal_home_write_blocked_profile_record() {
  local profile="$1" size="$2" scale="$3" reason="$4"
  local output="$YUJIAN_EVIDENCE_DIR/normal-home-v1/normal_home_profile_${profile}.json"
  python3 - "$output" "$BUILD_SHA" "$profile" "$size" "$scale" "$reason" <<'PY'
import json, pathlib, sys
output, build_sha, profile, size, font_scale, reason = sys.argv[1:]
payload = {
    "profile_id": profile,
    "status": "BLOCKED_INFRA",
    "reason": reason,
    "page_state": "NORMAL_HOME_DATA",
    "account_state": "GUEST",
    "requested_resolution": size,
    "font_scale": float(font_scale),
    "source_commit": build_sha,
    "screenshot": None,
}
pathlib.Path(output).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(payload, ensure_ascii=False))
PY
}

normal_home_blocked_dimension() {
  local size="$1"
  "$YUJIAN_ADB_BIN" shell wm size reset || true
  runtime_set_failure "PREFLIGHT" "DEVICE_CANNOT_CAPTURE_${size}"
  return "$EXIT_BLOCKED_INFRA"
}

normal_home_capture_runtime_profile() {
  local profile="$1" size="$2" scale="$3"
  local out="$YUJIAN_EVIDENCE_DIR/normal-home-v1"
  local screenshot="$out/normal_home_runtime_${profile}.png"
  local test_log="$out/normal_home_profile_${profile}_instrumentation.log"
  local insets_log="$out/normal_home_profile_${profile}_insets_instrumentation.log"
  local metrics_log="$out/normal_home_profile_${profile}_metrics.log"
  local window_dump="$out/normal_home_profile_${profile}_window.txt"
  local launch_log="$out/normal_home_profile_${profile}_launch.log"
  local rc capture_rc launch_rc

  if [[ "$size" == "1080x2340" ]]; then
    local prepare_rc=0
    normal_home_prepare_long_display || prepare_rc=$?
    if (( prepare_rc == EXIT_BLOCKED_INFRA )); then
      normal_home_write_blocked_profile_record "$profile" "$size" "$scale" \
        "A native 1080x2340 API28 display could not be provisioned; see infra/long_screen_device_setup.log."
      return "$EXIT_BLOCKED_INFRA"
    elif (( prepare_rc != 0 )); then
      return "$prepare_rc"
    fi
  fi

  "$YUJIAN_ADB_BIN" shell wm size "$size" > "$out/normal_home_profile_${profile}_wm_size.log" 2>&1
  "$YUJIAN_ADB_BIN" shell wm density 480 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put system font_scale "$scale" >/dev/null
  # Frozen pixel masks compare the neutral design frame. Motion is captured
  # separately below with the platform animation scales restored to 1.
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell am force-stop "$YUJIAN_APP_PACKAGE"
  "$YUJIAN_ADB_BIN" logcat -c

  local profile_class='com.yujian.ai.ui.home.NormalHomeDataParityTest#differentPortraitRatiosShareCoverViewportAndPreserveHomeDataAndRoutes'
  timeout 180s "$YUJIAN_ADB_BIN" shell am instrument -w -r -e class "$profile_class" \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$test_log" 2>&1
  local rc=$?
  if (( rc != 0 )) || ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$test_log"; then
    cat "$test_log" >&2
    return 1
  fi

  local insets_class='com.yujian.ai.ui.home.NormalHomeDataParityTest#edgeToEdgeNormalHomeUsesOneWindowOriginAndReportsInsets'
  timeout 180s "$YUJIAN_ADB_BIN" shell am instrument -w -r -e class "$insets_class" \
    "$YUJIAN_INSTRUMENTATION_TARGET" > "$insets_log" 2>&1
  rc=$?
  if (( rc != 0 )) || ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$insets_log"; then
    cat "$insets_log" >&2
    return 1
  fi

  # Save real hierarchy/text metrics from the instrumentation activity before
  # the runtime page is relaunched for the screenshot.
  "$YUJIAN_ADB_BIN" logcat -d -v brief -s \
    NORMAL_HOME_HIERARCHY_EVIDENCE:I NORMAL_HOME_WINDOW_METRICS:I NORMAL_HOME_TEXT_LAYOUT:I \
    > "$metrics_log" 2>&1 || return 1
  "$YUJIAN_ADB_BIN" shell dumpsys window > "$window_dump" 2>&1 || return 1

  normal_home_launch_app > "$launch_log" 2>&1
  launch_rc=$?
  if (( launch_rc != 0 )); then
    if [[ "$size" == "1080x2340" ]]; then
      if "$YUJIAN_ADB_BIN" exec-out screencap -p > "$screenshot" 2>/dev/null; then
        normal_home_write_profile_record "$screenshot" "$profile" "$size" "$scale" \
          "BLOCKED_INFRA" "The runtime page did not settle at the requested long-screen profile; see native screenshot and launch log."
      else
        normal_home_write_blocked_profile_record "$profile" "$size" "$scale" \
          "The API28 emulator became unavailable during native long-screen capture; see infra/long_screen_device_setup.log."
      fi
      return "$EXIT_BLOCKED_INFRA"
    fi
    "$YUJIAN_ADB_BIN" exec-out screencap -p > "$screenshot" 2>/dev/null || return 1
    cat "$launch_log" >&2
    return 1
  fi

  normal_home_capture_exact "$screenshot" "${size%x*}" "${size#*x}"
  capture_rc=$?
  if (( capture_rc != 0 )); then
    if [[ "$size" == "1080x2340" ]]; then
      normal_home_write_profile_record "$screenshot" "$profile" "$size" "$scale" \
        "BLOCKED_INFRA" "Native screencap dimensions do not match the requested 1080x2340 device profile."
      return "$EXIT_BLOCKED_INFRA"
    fi
    return 1
  fi

  normal_home_write_profile_record "$screenshot" "$profile" "$size" "$scale" "CAPTURED" ""
  printf 'NORMAL_HOME_RUNTIME_PROFILE_PASS profile=%s resolution=%s fontScale=%s\n' "$profile" "$size" "$scale"
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/normal-home-v1"
  mkdir -p "$out"
  local crop_out="$out/fish-photo-crop-acceptance"
  mkdir -p "$crop_out"
  local crop_file
  for crop_file in labelled-cases.txt fallback-cases.txt \
    input-landscape-labelled-v1.png output-landscape-labelled-v1.png \
    input-portrait-labelled-v1.png output-portrait-labelled-v1.png \
    input-left-edge-labelled-v1.png output-left-edge-labelled-v1.png \
    input-right-edge-labelled-v1.png output-right-edge-labelled-v1.png \
    input-top-edge-labelled-v1.png output-top-edge-labelled-v1.png \
    input-bottom-edge-labelled-v1.png output-bottom-edge-labelled-v1.png \
    input-no-reliable-bbox-v1.png output-no-reliable-bbox-v1.png
  do
    "$YUJIAN_ADB_BIN" exec-out run-as "$YUJIAN_APP_PACKAGE" cat \
      "cache/fish-photo-crop-acceptance/$crop_file" \
      > "$crop_out/$crop_file" 2>/dev/null || true
  done
  if [[ ! -s "$crop_out/labelled-cases.txt" || ! -s "$crop_out/fallback-cases.txt" ]]; then
    printf 'MISSING_FISH_PHOTO_CROP_INSTRUMENTATION_EVIDENCE\n' >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
    runtime_set_failure "EVIDENCE" "FISH_PHOTO_CROP_ACCEPTANCE_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  for crop_file in input-landscape-labelled-v1.png output-landscape-labelled-v1.png \
    input-portrait-labelled-v1.png output-portrait-labelled-v1.png \
    input-left-edge-labelled-v1.png output-left-edge-labelled-v1.png \
    input-right-edge-labelled-v1.png output-right-edge-labelled-v1.png \
    input-top-edge-labelled-v1.png output-top-edge-labelled-v1.png \
    input-bottom-edge-labelled-v1.png output-bottom-edge-labelled-v1.png \
    input-no-reliable-bbox-v1.png output-no-reliable-bbox-v1.png
  do
    if [[ ! -s "$crop_out/$crop_file" ]]; then
      printf 'MISSING_FISH_PHOTO_CROP_IMAGE=%s\n' "$crop_file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      runtime_set_failure "EVIDENCE" "FISH_PHOTO_CROP_IMAGE_MISSING"
      return "$EXIT_FAIL_EVIDENCE"
    fi
  done
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 1.0 || true
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 1.0 || true
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 1.0 || true

  local rc
  "$YUJIAN_ADB_BIN" shell wm size 1080x1920
  # Runtime screenshots must use the registered photo fixture. Synthetic
  # aspect-ratio panels remain useful for layout tests, but are not fish-photo
  # evidence and must never be used to close the visual Hero gate.
  local seed_method="${NORMAL_HOME_EVIDENCE_SEED_METHOD:-seedMultipleGuestCatches}"
  normal_home_run_seed "$seed_method" "$out/seed_${seed_method}.log" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_launch_app || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_exact "$out/01_normal_home_first_card.png" 1080 1920
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X1920"; return "$EXIT_BLOCKED_INFRA"; fi
  normal_home_capture_exact "$out/04_normal_home_runtime_1080x1920.png" 1080 1920
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X1920"; return "$EXIT_BLOCKED_INFRA"; fi
  "$YUJIAN_ADB_BIN" shell input swipe 900 1200 180 1200 450
  sleep 2
  normal_home_capture_exact "$out/02_normal_home_second_card.png" 1080 1920
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X1920"; return "$EXIT_BLOCKED_INFRA"; fi
python3 - "$out/01_normal_home_first_card.png" "$out/02_normal_home_second_card.png" <<'PY'
from PIL import Image, ImageChops
import sys

first, second = (Image.open(path).convert("RGB") for path in sys.argv[1:])
difference = ImageChops.difference(first.crop((200, 1180, 880, 1460)), second.crop((200, 1180, 880, 1460)))
changed_pixels = sum(1 for pixel in difference.getdata() if pixel != (0, 0, 0))
print(f"NORMAL_HOME_PAGER_FOOTER_CHANGED_PIXELS={changed_pixels}")
if changed_pixels == 0:
    raise SystemExit("NORMAL_HOME_SECOND_PAGE_CAPTURE_DID_NOT_CHANGE")
PY
  rc=$?
  if (( rc != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_SECOND_PAGE_CAPTURE_DID_NOT_CHANGE"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  cp "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/normal_home_v1.png" "$out/03_normal_home_frozen_1080x1920.png"
  cp "$YUJIAN_REPO_ROOT/app/src/main/assets/normal_home_runtime_v1/static/scene_base.png" "$out/07_normal_home_background_runtime.png"
  cp "$YUJIAN_REPO_ROOT/design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png" "$out/06_normal_home_background_source.png"
  # Preserve a strict visual failure, but collect the required native font
  # scale profiles first. Those screenshots and window-coordinate metrics are
  # still useful for a validation APK even when the frozen visual anchors do
  # not pass. Do not relax or skip the visual verifier's result.
  local visual_evidence_rc=0
  python3 "$YUJIAN_REPO_ROOT/scripts/verify_normal_home_visual_evidence.py" "$out" || {
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_VISUAL_EVIDENCE_FAILED"
    visual_evidence_rc=1
    printf '%s\n' 'NORMAL_HOME_VISUAL_EVIDENCE_FAILED_CONTINUING_REQUIRED_PROFILE_CAPTURE' >&2
  }

  printf '%s\n' \
    'NormalHomeDataParityTest: synthetic 9:16 and 4:5 fixtures verify layout behavior only; they are not real-fish visual evidence.' \
    'HomeStatsSemanticsTest: recordDays has no OnClick; fish species and catch totals navigate.' \
    'NormalHomeHeroBehaviorTest: one catch is centered; tap opens the matching recordId; multiple catches remain manual and swipeable.' \
    'API 28 responsive evidence: native screenshots and test metrics are saved for each device/font profile.' \
    'No custom Home Hero haptic; no Home sound.' \
    > "$out/10_normal_home_semantics_report.txt"

  normal_home_capture_runtime_profile "1080x1920_fs1_0" "1080x1920" "1.0" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_runtime_profile "1080x1920_fs1_3" "1080x1920" "1.3" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_runtime_profile "1080x1920_fs1_5" "1080x1920" "1.5" || return "$EXIT_FAIL_EVIDENCE"
  if (( visual_evidence_rc != 0 )); then
    # The visual gate remains FAIL_EVIDENCE. Stop here after collecting the
    # required font-scale screenshots and actual bounds; do not emit a later
    # full-parity PASS from a run whose primary visual anchors already failed.
    return "$EXIT_FAIL_EVIDENCE"
  fi
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 0.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 0.0 >/dev/null
  normal_home_launch_app > "$out/reduce_motion_launch.log" 2>&1 || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_exact "$out/normal_home_motion_neutral_1080x1920.png" 1080 1920 || return "$EXIT_FAIL_EVIDENCE"
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 1.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 1.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 1.0 >/dev/null
  "$YUJIAN_ADB_BIN" shell am force-stop "$YUJIAN_APP_PACKAGE"
  "$YUJIAN_ADB_BIN" shell rm -f /sdcard/normal_home_motion.mp4
  "$YUJIAN_ADB_BIN" shell screenrecord --time-limit 18 /sdcard/normal_home_motion.mp4 > "$out/screenrecord.log" 2>&1 &
  local screenrecord_pid=$!
  sleep 1
  normal_home_launch_app > "$out/motion_launch.log" 2>&1 || return "$EXIT_FAIL_EVIDENCE"
  wait "$screenrecord_pid"
  "$YUJIAN_ADB_BIN" pull /sdcard/normal_home_motion.mp4 "$out/11_Normal_Home_Runtime_18s.mp4" >/dev/null
  local duration
  duration="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$out/11_Normal_Home_Runtime_18s.mp4")"
  python3 - "$duration" <<'PY'
import sys
duration=float(sys.argv[1])
print(f"NORMAL_HOME_MOTION_DURATION={duration:.3f}")
if duration < 16.0:
    raise SystemExit("NORMAL_HOME_MOTION_TOO_SHORT")
PY

  # The long-screen profile may require restarting the AVD with a native
  # 1080x2340 skin. Capture it last so the base-size reduced-motion frame and
  # motion recording above remain native 1080x1920 evidence.
  local long_profile_rc=0
  normal_home_capture_runtime_profile "1080x2340_fs1_0" "1080x2340" "1.0" || long_profile_rc=$?
  if (( long_profile_rc != 0 && long_profile_rc != EXIT_BLOCKED_INFRA )); then
    return "$EXIT_FAIL_EVIDENCE"
  fi

  python3 "$YUJIAN_REPO_ROOT/scripts/verify_normal_home_a4_runtime_evidence.py" "$out" || {
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_A4_RUNTIME_EVIDENCE_INVALID"
    return "$EXIT_FAIL_EVIDENCE"
  }
  local closure_status
  closure_status="$(python3 - "$out/Normal_Home_Runtime_Closure_Result.json" <<'PY'
import json, sys
print(json.load(open(sys.argv[1], encoding="utf-8")).get("status", "BLOCKED"))
PY
)"
  if [[ "$closure_status" != "PASS" ]]; then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_RUNTIME_PARITY_${closure_status}"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local missing=0 file
  for file in \
    "$out/01_normal_home_first_card.png" \
    "$out/02_normal_home_second_card.png" \
    "$out/03_normal_home_frozen_1080x1920.png" \
    "$out/04_normal_home_runtime_1080x1920.png" \
    "$out/05_normal_home_frozen_runtime_side_by_side.png" \
    "$out/06_normal_home_background_source.png" \
    "$out/07_normal_home_background_runtime.png" \
    "$out/08_normal_home_background_parity_report.json" \
    "$out/09_normal_home_visual_parity_report.json" \
    "$out/10_normal_home_semantics_report.txt" \
    "$out/11_Normal_Home_Runtime_18s.mp4" \
    "$out/normal-home-motion-instrumentation.log" \
    "$out/Normal_Home_Runtime_Visual_Audit_Report.md" \
    "$out/Normal_Home_Hierarchy_Report.json" \
    "$out/Normal_Home_Hero_EvidenceFit_Report.json" \
    "$out/Normal_Home_Typography_Runtime_Report.json" \
    "$out/Normal_Home_Motion_Runtime_Report.json" \
    "$out/Normal_Home_Responsive_Runtime_Report.json" \
    "$out/Normal_Home_Runtime_Bounds_Report.json" \
    "$out/Normal_Home_Pixel_Diff_Report.json" \
    "$out/Hero_Fixture_Preflight_Report.json" \
    "$out/Camera_Core_Mask_Report.json" \
    "$out/Normal_Home_Runtime_Closure_Result.json"
  do
    if [[ ! -s "$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_V1_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
