#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.NormalHomeRuntimeContractTest,com.yujian.ai.ui.home.HomeStatsSemanticsTest,com.yujian.ai.ui.home.NormalHomeHeroBehaviorTest,com.yujian.ai.ui.home.NormalHomeDataParityTest'
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

# The lake scene must be decoded, and the upper half of the catch card must
# contain the real seeded photo rather than the transparent/error placeholder.
background = crop(0.04, 0.16, 0.92, 0.18)
card_photo = crop(0.18, 0.33, 0.64, 0.28)
bg_stat = ImageStat.Stat(background)
card_stat = ImageStat.Stat(card_photo)
bg_spread = sum(bg_stat.stddev) / 3.0
card_spread = sum(card_stat.stddev) / 3.0
bg_mean = sum(bg_stat.mean) / 3.0
card_mean = sum(card_stat.mean) / 3.0
print(
    "NORMAL_HOME_RENDER_READINESS "
    f"bg_mean={bg_mean:.2f} bg_spread={bg_spread:.2f} "
    f"card_mean={card_mean:.2f} card_spread={card_spread:.2f}"
)
if bg_spread < 18.0 or card_spread < 18.0 or card_mean > 242.0:
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

normal_home_blocked_dimension() {
  local size="$1"
  "$YUJIAN_ADB_BIN" shell wm size reset || true
  runtime_set_failure "PREFLIGHT" "DEVICE_CANNOT_CAPTURE_${size}"
  return "$EXIT_BLOCKED_INFRA"
}

gate_collect_evidence() {
  local out="$YUJIAN_EVIDENCE_DIR/normal-home-v1"
  mkdir -p "$out"
  "$YUJIAN_ADB_BIN" shell settings put global animator_duration_scale 1.0 || true
  "$YUJIAN_ADB_BIN" shell settings put global transition_animation_scale 1.0 || true
  "$YUJIAN_ADB_BIN" shell settings put global window_animation_scale 1.0 || true

  local rc
  "$YUJIAN_ADB_BIN" shell wm size 1080x1920
  normal_home_run_seed seedSingleGuestCatch "$out/seed_single.log" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_launch_app || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_exact "$out/02_normal_home_runtime_1080x1920.png" 1080 1920
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X1920"; return "$EXIT_BLOCKED_INFRA"; fi

  "$YUJIAN_ADB_BIN" shell wm size 1080x2340
  normal_home_run_seed seedSingleGuestCatch "$out/seed_single_19_5_9.log" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_launch_app || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_exact "$out/04_normal_home_single_19_5_9_1080x2340.png" 1080 2340
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X2340"; return "$EXIT_BLOCKED_INFRA"; fi

  "$YUJIAN_ADB_BIN" shell wm size 1080x2400
  normal_home_run_seed seedTwoAspectPortraitGuestCatches "$out/seed_two_aspect_portraits.log" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_launch_app || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_exact "$out/01_normal_home_first_card.png" 1080 2400
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X2400"; return "$EXIT_BLOCKED_INFRA"; fi
  "$YUJIAN_ADB_BIN" shell input swipe 900 1200 180 1200 450
  sleep 2
  normal_home_capture_exact "$out/02_normal_home_second_card.png" 1080 2400
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X2400"; return "$EXIT_BLOCKED_INFRA"; fi
  python3 - "$out/01_normal_home_first_card.png" "$out/02_normal_home_second_card.png" <<'PY'
from PIL import Image, ImageChops, ImageStat
import sys

first, second = (Image.open(path).convert("RGB") for path in sys.argv[1:])
region = (140, 600, 940, 1800)
mae = sum(ImageStat.Stat(ImageChops.difference(first.crop(region), second.crop(region))).mean) / 3.0
print(f"NORMAL_HOME_PAGER_CAPTURE_MAE={mae:.2f}")
if mae < 6.0:
    raise SystemExit("NORMAL_HOME_SECOND_PAGE_CAPTURE_DID_NOT_CHANGE")
PY
  rc=$?
  if (( rc != 0 )); then
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_SECOND_PAGE_CAPTURE_DID_NOT_CHANGE"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  normal_home_run_seed seedMultipleGuestCatches "$out/seed_multiple_20_9.log" || return "$EXIT_FAIL_EVIDENCE"
  normal_home_launch_app || return "$EXIT_FAIL_EVIDENCE"
  normal_home_capture_exact "$out/05_normal_home_multiple_20_9_1080x2400.png" 1080 2400
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X2400"; return "$EXIT_BLOCKED_INFRA"; fi

  "$YUJIAN_ADB_BIN" shell wm size 1080x2520
  sleep 2
  normal_home_capture_exact "$out/06_normal_home_multiple_21_9_1080x2520.png" 1080 2520
  rc=$?
  if (( rc != 0 )); then normal_home_blocked_dimension "1080X2520"; return "$EXIT_BLOCKED_INFRA"; fi

  cp "$YUJIAN_REPO_ROOT/design/system/core_visual_v1/reference/normal_home_v1.png" "$out/01_normal_home_frozen_1080x1920.png"
  cp "$YUJIAN_REPO_ROOT/app/src/main/assets/normal_home_runtime_v1/static/scene_base.png" "$out/08_normal_home_background_runtime.png"
  cp "$YUJIAN_REPO_ROOT/design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png" "$out/07_normal_home_background_source.png"
  python3 "$YUJIAN_REPO_ROOT/scripts/verify_normal_home_visual_evidence.py" "$out" || {
    runtime_set_failure "EVIDENCE" "NORMAL_HOME_VISUAL_EVIDENCE_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  printf '%s\n' \
    'NormalHomeDataParityTest: deterministic 9:16 and 4:5 portraits share one cover viewport; card content, long locations, stats, header, carousel and navigation remain visible and actionable.' \
    'HomeStatsSemanticsTest: recordDays has no OnClick; fish species and catch totals navigate.' \
    'NormalHomeHeroBehaviorTest: one catch is centered; tap opens the matching recordId; multiple catches remain manual and swipeable.' \
    'Home state, valid-record filtering, timestamp ordering and Reduce Motion: covered by testDebugUnitTest.' \
    'No custom Home Hero haptic; no Home sound.' \
    > "$out/11_normal_home_semantics_report.txt"

  "$YUJIAN_ADB_BIN" shell wm size reset
  sleep 1
  "$YUJIAN_ADB_BIN" shell rm -f /sdcard/normal_home_motion.mp4
  "$YUJIAN_ADB_BIN" shell screenrecord --time-limit 10 /sdcard/normal_home_motion.mp4 > "$out/screenrecord.log" 2>&1
  "$YUJIAN_ADB_BIN" pull /sdcard/normal_home_motion.mp4 "$out/12_Normal_Home_Runtime_10s.mp4" >/dev/null
  local duration
  duration="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$out/12_Normal_Home_Runtime_10s.mp4")"
  python3 - "$duration" <<'PY'
import sys
duration=float(sys.argv[1])
print(f"NORMAL_HOME_MOTION_DURATION={duration:.3f}")
if duration < 9.5:
    raise SystemExit("NORMAL_HOME_MOTION_TOO_SHORT")
PY

  local missing=0 file
  for file in \
    "$out/01_normal_home_first_card.png" \
    "$out/02_normal_home_second_card.png" \
    "$out/01_normal_home_frozen_1080x1920.png" \
    "$out/02_normal_home_runtime_1080x1920.png" \
    "$out/03_normal_home_frozen_runtime_side_by_side.png" \
    "$out/04_normal_home_single_19_5_9_1080x2340.png" \
    "$out/05_normal_home_multiple_20_9_1080x2400.png" \
    "$out/06_normal_home_multiple_21_9_1080x2520.png" \
    "$out/07_normal_home_background_source.png" \
    "$out/08_normal_home_background_runtime.png" \
    "$out/09_normal_home_background_parity_report.json" \
    "$out/10_normal_home_visual_parity_report.json" \
    "$out/11_normal_home_semantics_report.txt" \
    "$out/12_Normal_Home_Runtime_10s.mp4"
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
