#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.EmptyHomeV2RuntimeContractTest'
}

gate_collect_evidence() {
  local home_dir="$YUJIAN_EVIDENCE_DIR/home-evidence"
  mkdir -p "$home_dir"

  local evidence_command_rc=0
  (
    set -euo pipefail

    # Emulator runner disables animations for deterministic instrumentation.
    # Empty Home parity must observe the production motion contract.
    "${YUJIAN_ADB_BIN}" shell settings put global animator_duration_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global transition_animation_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global window_animation_scale 1.0 || true

    "${YUJIAN_ADB_BIN}" shell pm clear "$YUJIAN_APP_PACKAGE"
    "${YUJIAN_ADB_BIN}" shell wm size 1080x1920
    "${YUJIAN_ADB_BIN}" logcat -c

    # Launch the production activity, then wait only for the real resumed
    # Activity. Compose text is intentionally NOT read through UIAutomator:
    # that bridge is unreliable for this screen and previously produced false
    # FAIL_EVIDENCE while the page was visibly rendered.
    "${YUJIAN_ADB_BIN}" shell monkey -p "$YUJIAN_APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null

    home_resumed=0
    for attempt in $(seq 1 20); do
      resumed="$("${YUJIAN_ADB_BIN}" shell dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|ResumedActivity' | head -n 1 || true)"
      printf 'EMPTY_HOME_ACTIVITY_READINESS attempt=%s resumed=%s\n' "$attempt" "$resumed"
      if [[ "$resumed" == *"$YUJIAN_APP_PACKAGE/com.yujian.ai.MainActivity"* ]]; then
        home_resumed=1
        break
      fi
      sleep 1
    done

    if (( home_resumed != 1 )); then
      echo 'EMPTY_HOME_ACTIVITY_NOT_RESUMED' >&2
      "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/startup_not_ready.png" || true
      "${YUJIAN_ADB_BIN}" logcat -d -b all -v threadtime > "$home_dir/startup_logcat.txt" || true
      exit 1
    fi

    # Capture the composed HomeScreen from its measured application DecorView.
    # The instrumentation path refuses any size other than the requested canonical surface.
    responsive_remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/empty_home_responsive"
    canonical_capture="runtime_static.png"
    canonical_test_log="$home_dir/canonical_capture.instrumentation.log"
    original_font_scale=$("${YUJIAN_ADB_BIN}" shell settings get system font_scale | tr -d '\015')
    if [[ ! "$original_font_scale" =~ ^[0-9]+([.][0-9]+)?$ ]]; then original_font_scale=1.0; fi
    "${YUJIAN_ADB_BIN}" shell settings put system font_scale 1.0
    "${YUJIAN_ADB_BIN}" shell mkdir -p "$responsive_remote"
    "${YUJIAN_ADB_BIN}" shell rm -f "$responsive_remote/$canonical_capture"
    timeout 90s "${YUJIAN_ADB_BIN}" shell am instrument -w -r \
      -e class 'com.yujian.ai.EmptyHomeResponsiveRuntimeTest#requiredControlsRemainVisibleInsideSafeDrawingViewport' \
      -e expectedWidthPx 1080 -e expectedHeightPx 1920 \
      -e expectedFontScale 1.0 -e evidenceName "$canonical_capture" -e canonicalCapture true \
      "$YUJIAN_INSTRUMENTATION_TARGET" > "$canonical_test_log" 2>&1 || {
        cat "$canonical_test_log" >&2
        echo 'CANONICAL_CAPTURE_INVALID instrumentation did not produce a valid app-surface capture' >&2
        exit 1
      }
    cat "$canonical_test_log"
    "${YUJIAN_ADB_BIN}" pull "$responsive_remote/$canonical_capture" "$home_dir/runtime_static.png" >/dev/null
    python3 - "$home_dir/runtime_static.png" <<'PY'
from pathlib import Path
import sys
from PIL import Image, ImageStat

path = Path(sys.argv[1])
image = Image.open(path).convert("RGB")
if image.size != (1080, 1920):
    raise SystemExit(f"CANONICAL_CAPTURE_INVALID dimensions={image.width}x{image.height}")
stat = ImageStat.Stat(image)
mean = sum(stat.mean) / 3.0
spread = sum(stat.stddev) / 3.0
if (mean > 245 and spread < 8) or (mean < 8 and spread < 8):
    raise SystemExit(f"CANONICAL_CAPTURE_INVALID blank_surface mean={mean:.2f} spread={spread:.2f}")
print(f"CANONICAL_APP_SURFACE_CAPTURE_PASS dimensions=1080x1920 mean={mean:.2f} spread={spread:.2f}")
PY
    cp "$home_dir/runtime_static.png" "$home_dir/01_empty_home_static.png"
    # Static canonical capture freezes only the Compose test clock. Restore
    # production Android animation scales before physical motion evidence.
    "${YUJIAN_ADB_BIN}" shell settings put global animator_duration_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global transition_animation_scale 1.0 || true
    "${YUJIAN_ADB_BIN}" shell settings put global window_animation_scale 1.0 || true
    sleep 4
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/runtime_4s.png"

    # The frozen parity layout uses a 1080x1920 logical wm size on the
    # 320x640 API 28 emulator. API 28 screenrecord otherwise keeps the
    # physical encoder size but projects the logical surface's upper half.
    # Capture after returning to the physical display size, then restore the
    # frozen logical size for the remaining evidence.
    "${YUJIAN_ADB_BIN}" shell wm size reset
    sleep 1
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/runtime_recording_reference.png"

    "${YUJIAN_ADB_BIN}" shell rm -f /sdcard/full_runtime_15s.mp4
    # API 28's recorder can inherit the logical wm-size projection and encode
    # only the upper portion of the portrait surface when the physical runner
    # display is 320x640. Pin the evidence stream to the runner's physical
    # surface so the MP4 contains the same full frame as screencap.
    "${YUJIAN_ADB_BIN}" shell wm size > "$home_dir/screenrecord.log" 2>&1
    "${YUJIAN_ADB_BIN}" shell screenrecord --size 320x640 --time-limit 15 --verbose /sdcard/full_runtime_15s.mp4 >> "$home_dir/screenrecord.log" 2>&1
    "${YUJIAN_ADB_BIN}" pull /sdcard/full_runtime_15s.mp4 "$home_dir/full_runtime_15s.mp4" >/dev/null
    cp "$home_dir/full_runtime_15s.mp4" "$home_dir/Empty_Home_V2_Parity_Runtime.mp4"
    "${YUJIAN_ADB_BIN}" shell wm size 1080x1920

    python3 - "$home_dir/full_runtime_15s.mp4" "$home_dir/runtime_recording_reference.png" <<'PY'
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

video_path = Path(sys.argv[1])
reference_path = Path(sys.argv[2])
probe = subprocess.run(
    ["ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(video_path)],
    check=True,
    capture_output=True,
    text=True,
)
metadata = json.loads(probe.stdout)
video_stream = next(stream for stream in metadata["streams"] if stream.get("codec_type") == "video")
duration = float(metadata["format"]["duration"])
width = int(video_stream["width"])
height = int(video_stream["height"])
if duration < 14.5:
    raise SystemExit(f"EMPTY_HOME_RUNTIME_VIDEO_TOO_SHORT duration={duration:.3f}")

with tempfile.TemporaryDirectory() as temp_dir:
    frame_path = Path(temp_dir) / "frame.png"
    subprocess.run(
        ["ffmpeg", "-loglevel", "error", "-y", "-ss", "0.5", "-i", str(video_path), "-frames:v", "1", str(frame_path)],
        check=True,
    )
    frame = Image.open(frame_path).convert("RGB")
reference = Image.open(reference_path).convert("RGB")
frame = frame.resize(reference.size, Image.Resampling.LANCZOS)

def crop(image: Image.Image, x: float, y: float, width: float, height: float) -> Image.Image:
    return image.crop((
        int(image.width * x),
        int(image.height * y),
        int(image.width * (x + width)),
        int(image.height * (y + height)),
    ))

def mae(left: Image.Image, right: Image.Image) -> float:
    diff = ImageChops.difference(left, right)
    return sum(ImageStat.Stat(diff).mean) / 3.0

camera_reference = crop(reference, 0.31, 0.69, 0.39, 0.27)
camera_frame = crop(frame, 0.31, 0.69, 0.39, 0.27)
bobber_reference = crop(reference, 0.324, 0.484, 1 / 3, 0.22)
bobber_frame = crop(frame, 0.324, 0.484, 1 / 3, 0.22)
camera_mae = mae(camera_reference, camera_frame)
bobber_mae = mae(bobber_reference, bobber_frame)
camera_pixels = list(camera_frame.getdata())
bright_fraction = sum(1 for r, g, b in camera_pixels if r > 210 and g > 210 and b > 210) / len(camera_pixels)

print(
    "EMPTY_HOME_RUNTIME_VIDEO_VALID "
    f"duration={duration:.3f} dimensions={width}x{height} "
    f"camera_mae={camera_mae:.3f} bobber_mae={bobber_mae:.3f} "
    f"camera_bright_fraction={bright_fraction:.4f}"
)
if camera_mae > 30 or bobber_mae > 20 or bright_fraction < 0.005:
    raise SystemExit(
        "EMPTY_HOME_RUNTIME_VIDEO_FRAME_INVALID "
        f"camera_mae={camera_mae:.3f} bobber_mae={bobber_mae:.3f} "
        f"camera_bright_fraction={bright_fraction:.4f}"
    )
PY

    ffmpeg -y -ss 0.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/02_camera_button_closeup.png"
    ffmpeg -y -ss 0.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' "$home_dir/03_bobber_water_contact.png"
    ffmpeg -y -ss 0.2 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' "$home_dir/04_ripple_visible.png"
    ffmpeg -y -ss 2.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/05_gold_rim_sweep_before.png"
    ffmpeg -y -ss 3.7 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/06_gold_rim_sweep_peak.png"
    ffmpeg -y -ss 5.0 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/07_gold_rim_sweep_after.png"
    ffmpeg -y -ss 1.0 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/08_camera_breath_min.png"
    ffmpeg -y -ss 3.5 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' "$home_dir/09_camera_breath_max.png"
    ffmpeg -y -ss 2.4 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/10_sun_particle_frame.png"
    ffmpeg -y -ss 0.2 -i "$home_dir/full_runtime_15s.mp4" -frames:v 1 "$home_dir/11_cloud_start.png"

    # Compare cloud drift at the same physical capture size as the video start.
    "${YUJIAN_ADB_BIN}" shell wm size reset
    sleep 50
    "${YUJIAN_ADB_BIN}" exec-out screencap -p > "$home_dir/12_cloud_later.png"
    "${YUJIAN_ADB_BIN}" shell wm size 1080x1920

    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -t 10 -c copy "$home_dir/environment_motion.mp4"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -vf 'crop=iw/3:ih*0.22:iw*0.324:ih*0.484' -an "$home_dir/bobber_ripple_crop.mp4"
    ffmpeg -y -i "$home_dir/full_runtime_15s.mp4" -vf 'crop=iw*0.39:ih*0.27:iw*0.31:ih*0.69' -an "$home_dir/camera_motion.mp4"

    python3 "$YUJIAN_REPO_ROOT/scripts/build_empty_home_runtime_evidence.py" --evidence-dir "$home_dir" --build-sha "$YUJIAN_BUILD_SHA"
    python3 - "$YUJIAN_REPO_ROOT/design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png" "$home_dir/runtime_static.png" "$home_dir/visual_parity_report.json" <<'PY'
import json
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

reference_path, runtime_path, report_path = map(Path, sys.argv[1:4])
reference = Image.open(reference_path).convert("RGB")
runtime = Image.open(runtime_path).convert("RGB")
if reference.size != runtime.size:
    runtime = runtime.resize(reference.size, Image.Resampling.LANCZOS)
box = (0, 0, 1080, 200)
diff = ImageChops.difference(reference.crop(box), runtime.crop(box))
header_mae = sum(ImageStat.Stat(diff).mean) / 3.0
report = json.loads(report_path.read_text(encoding="utf-8"))
header_status = "PASS" if header_mae <= 42.0 else "FAIL"
report["regions"]["header"] = {
    "authority": "Empty_Home_Final_Design_V2",
    "reference_box": list(box),
    "metric": "mean_absolute_rgb_error",
    "value": round(header_mae, 4),
    "threshold": 42.0,
    "status": header_status,
}
report["regions"]["fishing_composition"] = dict(report["regions"]["bobber_water"])
report["regions"]["fishing_composition"]["authority"] = "Empty_Home_Frozen_Visual_Revision_V2_2"
report["regions"]["fishing_composition"]["contract"] = "rod_line_bobber_water_contact_ripple"
report["region_failures"] = sorted(set(report.get("region_failures", [])) | ({"header"} if header_status != "PASS" else set()))
report["status"] = "PASS" if report.get("full_frame_status") == "PASS" and not report["region_failures"] else "FAIL"
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"EMPTY_HOME_REGION_GATE header_mae={header_mae:.4f} threshold=42 status={header_status} fishing=CONTRACT")
if report["status"] != "PASS":
    raise SystemExit("EMPTY_HOME_REGION_GATE_FAILED")
PY
    parity_status="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8"))["status"])' "$home_dir/visual_parity_report.json")"
    if [[ "$parity_status" != "PASS" ]]; then
      cat "$home_dir/visual_parity_report.json" >&2
      echo "EMPTY_HOME_VISUAL_PARITY_FAILED status=$parity_status" >&2
      exit 1
    fi
    cp "$YUJIAN_REPO_ROOT/design/pages/home/empty_home/source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png" "$home_dir/static_reference.png"
    # Region crops remain separate evidence so the scene background cannot
    # hide defects in frozen Hero, header, fishing composition, Camera or CTA.
    python3 - "$home_dir/runtime_static.png" "$home_dir" <<'PY'
from pathlib import Path
import shutil
import sys
from PIL import Image

image = Image.open(sys.argv[1]).convert("RGB")
out = Path(sys.argv[2])
if image.size != (1080, 1920):
    raise SystemExit(f"EMPTY_HOME_REFERENCE_CAPTURE_DIMENSIONS={image.width}x{image.height}")
for name, box in {
    "01_static_full.png": (0, 0, 1080, 1920),
    "02_header.png": (0, 0, 1080, 200),
    "03_hero.png": (50, 224, 675, 535),
    "04_rod_line.png": (0, 1140, 690, 1380),
    "05_bobber_contact.png": (440, 1260, 680, 1380),
    "06_ripple.png": (420, 1260, 700, 1380),
    "07_camera_static.png": (420, 1520, 660, 1770),
}.items():
    image.crop(box).save(out / name)
for old, new in (
    ("05_gold_rim_sweep_before.png", "08_camera_sweep_before.png"),
    ("06_gold_rim_sweep_peak.png", "09_camera_sweep_peak.png"),
    ("07_gold_rim_sweep_after.png", "10_camera_sweep_after.png"),
    ("08_camera_breath_min.png", "11_camera_breath_min.png"),
    ("09_camera_breath_max.png", "12_camera_breath_max.png"),
    ("10_sun_particle_frame.png", "13_sun_particle.png"),
    ("11_cloud_start.png", "14_cloud_start.png"),
    ("12_cloud_later.png", "15_cloud_later.png"),
):
    shutil.copyfile(out / old, out / new)
PY

    # The real HomeScreen instrumentation gate is rerun for each actual WM
    # viewport/font scale; it asserts safe-area bounds and saves app-only PNGs.
    responsive_remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/empty_home_responsive"
    density_output="$("${YUJIAN_ADB_BIN}" shell wm density 2>&1 | tr -d '\r')"
    density_dpi="$(awk '/Override density:/ {override=$3} /Physical density:/ {physical=$3} END {print override ? override : physical}' <<< "$density_output")"
    if [[ ! "$density_dpi" =~ ^[0-9]+$ ]]; then
      printf 'EMPTY_HOME_RESPONSIVE_DENSITY_UNAVAILABLE=%s\n' "$density_output" >&2
      exit 1
    fi
    original_font_scale="$("${YUJIAN_ADB_BIN}" shell settings get system font_scale 2>/dev/null | tr -d '\r')"
    if [[ ! "$original_font_scale" =~ ^[0-9]+([.][0-9]+)?$ ]]; then original_font_scale=1.0; fi
    responsive_log="$home_dir/responsive_matrix.tsv"
    : > "$responsive_log"
    restore_responsive_device() {
      "${YUJIAN_ADB_BIN}" shell wm size 1080x1920 >/dev/null 2>&1 || true
      "${YUJIAN_ADB_BIN}" shell settings put system font_scale "$original_font_scale" >/dev/null 2>&1 || true
    }
    trap restore_responsive_device EXIT
    for profile in '320 640' '360 780' '393 852' '411 891'; do
      read -r width_dp height_dp <<< "$profile"
      pixel_width="$(python3 -c 'import sys; print(round(int(sys.argv[1])*int(sys.argv[3])/160))' "$width_dp" "$height_dp" "$density_dpi")"
      pixel_height="$(python3 -c 'import sys; print(round(int(sys.argv[2])*int(sys.argv[3])/160))' "$width_dp" "$height_dp" "$density_dpi")"
      for font_scale in 1.0 1.3; do
        font_tag="${font_scale/./_}"
        screenshot="responsive_${width_dp}x${height_dp}_font_${font_tag}.png"
        test_log="$home_dir/${screenshot%.png}.instrumentation.log"
        "${YUJIAN_ADB_BIN}" shell wm size "${pixel_width}x${pixel_height}"
        "${YUJIAN_ADB_BIN}" shell settings put system font_scale "$font_scale"
        "${YUJIAN_ADB_BIN}" shell am force-stop "$YUJIAN_APP_PACKAGE"
        timeout 90s "${YUJIAN_ADB_BIN}" shell am instrument -w -r \
          -e class 'com.yujian.ai.EmptyHomeResponsiveRuntimeTest#requiredControlsRemainVisibleInsideSafeDrawingViewport' \
          -e expectedWidthDp "$width_dp" -e expectedHeightDp "$height_dp" \
          -e expectedFontScale "$font_scale" -e evidenceName "$screenshot" \
          "$YUJIAN_INSTRUMENTATION_TARGET" > "$test_log" 2>&1 || {
            cat "$test_log" >&2
            printf 'EMPTY_HOME_RESPONSIVE_PROFILE_FAILED=%sdpx%sdp fontScale=%s\n' "$width_dp" "$height_dp" "$font_scale" >&2
            exit 1
          }
        "${YUJIAN_ADB_BIN}" pull "$responsive_remote/$screenshot" "$home_dir/$screenshot" >/dev/null
        python3 - "$home_dir/$screenshot" "$width_dp" "$height_dp" "$font_scale" "$pixel_width" "$pixel_height" <<'PY'
from pathlib import Path
import sys
from PIL import Image, ImageStat

path = Path(sys.argv[1])
expected = (int(sys.argv[5]), int(sys.argv[6]))
image = Image.open(path).convert("RGB")
stat = ImageStat.Stat(image)
mean, spread = sum(stat.mean) / 3, sum(stat.stddev) / 3
if not (expected[0] * .95 <= image.width <= expected[0] * 1.05):
    raise SystemExit(f"responsive width {image.width} differs from target {expected[0]}")
if not (expected[1] * .90 <= image.height <= expected[1] * 1.05):
    raise SystemExit(f"responsive height {image.height} differs from target {expected[1]}")
if (mean > 245 and spread < 8) or (mean < 8 and spread < 8):
    raise SystemExit(f"responsive screenshot is blank: mean={mean:.2f}, spread={spread:.2f}")
print(f"EMPTY_HOME_RESPONSIVE_CAPTURE_PASS {sys.argv[2]}x{sys.argv[3]}dp fontScale={sys.argv[4]} capture={image.width}x{image.height}")
PY
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$width_dp" "$height_dp" "$font_scale" "$pixel_width" "$pixel_height" "$screenshot" >> "$responsive_log"
      done
    done
    python3 - "$responsive_log" "$home_dir/responsive_matrix.json" <<'PY'
import json
import sys
from pathlib import Path

source, destination = map(Path, sys.argv[1:3])
profiles = []
for row in source.read_text().splitlines():
    width, height, scale, pixel_width, pixel_height, screenshot = row.split("\t")
    profiles.append({"width_dp": int(width), "height_dp": int(height), "font_scale": float(scale),
                     "configured_width_px": int(pixel_width), "configured_height_px": int(pixel_height),
                     "screenshot": screenshot, "instrumentation": "PASS"})
if len(profiles) != 8:
    raise SystemExit(f"expected 8 responsive viewport/font-scale results, got {len(profiles)}")
destination.write_text(json.dumps({"status": "PASS", "profiles": profiles}, ensure_ascii=False, indent=2) + "\n")
print("EMPTY_HOME_RESPONSIVE_MATRIX_PASS profiles=8")
PY
    restore_responsive_device
    trap - EXIT
  )
  evidence_command_rc=$?
  if (( evidence_command_rc != 0 )); then
    runtime_set_failure "EVIDENCE" "EMPTY_HOME_V2_EVIDENCE_COMMAND_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  local missing=0 file
  for file in \
    "$home_dir/runtime_static.png" \
    "$home_dir/runtime_4s.png" \
    "$home_dir/Empty_Home_V2_Parity_Runtime.mp4" \
    "$home_dir/visual_parity_report.json" \
    "$home_dir/runtime_debug.json" \
    "$home_dir/static_reference.png" \
    "$home_dir/01_static_full.png" \
    "$home_dir/02_header.png" \
    "$home_dir/03_hero.png" \
    "$home_dir/04_rod_line.png" \
    "$home_dir/05_bobber_contact.png" \
    "$home_dir/06_ripple.png" \
    "$home_dir/07_camera_static.png" \
    "$home_dir/08_camera_sweep_before.png" \
    "$home_dir/09_camera_sweep_peak.png" \
    "$home_dir/10_camera_sweep_after.png" \
    "$home_dir/11_camera_breath_min.png" \
    "$home_dir/12_camera_breath_max.png" \
    "$home_dir/13_sun_particle.png" \
    "$home_dir/14_cloud_start.png" \
    "$home_dir/15_cloud_later.png" \
    "$home_dir/full_runtime_15s.mp4" \
    "$home_dir/bobber_ripple_crop.mp4" \
    "$home_dir/camera_motion.mp4" \
    "$home_dir/responsive_matrix.json"
  do
    if [[ ! -s "$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done

  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "EMPTY_HOME_V2_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  return "$EXIT_PASS"
}
