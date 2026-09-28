#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.LoginV2RuntimeTest'
}

gate_collect_evidence() {
  local login_dir="$YUJIAN_EVIDENCE_DIR/login-v2"
  mkdir -p "$login_dir"

  local remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/login_v2"
  "$YUJIAN_ADB_BIN" shell ls -la "$remote" > "$login_dir/device_files.txt" 2>&1 || true
  "$YUJIAN_ADB_BIN" pull "$remote/." "$login_dir/" > "$login_dir/adb_pull.log" 2>&1 || {
    runtime_set_failure "EVIDENCE" "LOGIN_V2_SCREENSHOT_PULL_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  local missing=0 file
  for file in \
    login_v2_idle.png \
    login_v2_filled.png \
    login_v2_password_visible.png \
    login_v2_loading.png \
    login_v2_error.png
  do
    if [[ ! -s "$login_dir/$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$login_dir/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "LOGIN_V2_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  python3 - "$YUJIAN_REPO_ROOT/design/pages/account_privacy/Login/00_Login.png" "$login_dir/login_v2_idle.png" "$login_dir" <<'PY'
from __future__ import annotations
import json
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageStat

reference_path = Path(sys.argv[1])
runtime_path = Path(sys.argv[2])
out_dir = Path(sys.argv[3])

reference = Image.open(reference_path).convert("RGB")
runtime_raw = Image.open(runtime_path).convert("RGB")

# API 28's UiAutomation screenshot may expose the emulator's physical backing
# buffer (for example 640x1280) while the Activity renders into the logical
# app surface in the upper-left quadrant (for example 320x640). Detect and
# normalize that surface before visual comparison instead of treating the
# unused black backing buffer as product pixels.
def active_surface(image: Image.Image) -> Image.Image:
    pixels = image.load()
    xs = []
    ys = []
    for y in range(image.height):
        for x in range(image.width):
            r, g, b = pixels[x, y]
            if max(r, g, b) > 12:
                xs.append(x)
                ys.append(y)
    if not xs:
        raise SystemExit("LOGIN_V2_ACTIVE_SURFACE_EMPTY")
    left, top, right, bottom = min(xs), min(ys), max(xs) + 1, max(ys) + 1
    # Status/navigation bars belong to the app frame. For the known API28
    # double-buffer case, the detected content occupies approximately the
    # upper-left half in each dimension; snap to that logical surface.
    if image.width >= 2 * right - 8 and image.height >= 2 * bottom - 8:
        right = image.width // 2
        bottom = image.height // 2
        left = 0
        top = 0
    surface = image.crop((left, top, right, bottom))
    if surface.width < 200 or surface.height < 400:
        raise SystemExit(
            f"LOGIN_V2_ACTIVE_SURFACE_INVALID size={surface.width}x{surface.height}"
        )
    return surface

runtime = active_surface(runtime_raw)
runtime.save(out_dir / "login_v2_idle_normalized.png")
reference_scaled = reference.resize(runtime.size, Image.Resampling.LANCZOS)

def mae(a, b):
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3.0

regions = {
    "full": (0.00, 0.00, 1.00, 1.00),
    "brand": (0.06, 0.05, 0.70, 0.30),
    "hero_copy": (0.05, 0.34, 0.92, 0.18),
    "form": (0.05, 0.48, 0.92, 0.34),
    "footer": (0.05, 0.82, 0.92, 0.14),
}
metrics = {}
for name, (x, y, w, h) in regions.items():
    box = (
        int(runtime.width*x), int(runtime.height*y),
        int(runtime.width*(x+w)), int(runtime.height*(y+h)),
    )
    metrics[name] = round(mae(reference_scaled.crop(box), runtime.crop(box)), 4)

thresholds = {
    "full": 65.0,
    "brand": 62.0,
    "hero_copy": 62.0,
    "form": 62.0,
    "footer": 65.0,
}
status = "PASS" if all(metrics[k] <= thresholds[k] for k in thresholds) else "FAIL"
report = {
    "status": status,
    "reference": str(reference_path),
    "runtime": str(runtime_path),
    "runtime_raw_size": list(runtime_raw.size),
    "runtime_size": list(runtime.size),
    "metrics_mae": metrics,
    "thresholds": thresholds,
}
(out_dir / "visual_parity_report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
)

canvas = Image.new("RGB", (runtime.width*2, runtime.height), "white")
canvas.paste(reference_scaled, (0, 0))
canvas.paste(runtime, (runtime.width, 0))
draw = ImageDraw.Draw(canvas)
draw.text((8, 8), "Frozen", fill="black")
draw.text((runtime.width+8, 8), "Runtime", fill="black")
canvas.save(out_dir / "login_v2_frozen_side_by_side.png")

print("LOGIN_V2_VISUAL_PARITY", json.dumps(report, ensure_ascii=False))
if status != "PASS":
    raise SystemExit("LOGIN_V2_VISUAL_PARITY_FAILED")
PY
  local parity_rc=$?
  if (( parity_rc != 0 )); then
    runtime_set_failure "EVIDENCE" "LOGIN_V2_VISUAL_PARITY_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  return "$EXIT_PASS"
}
