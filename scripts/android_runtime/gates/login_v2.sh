#!/usr/bin/env bash

gate_test_classes() {
  printf '%s\n' 'com.yujian.ai.LoginV2RuntimeTest,com.yujian.ai.AccountPrivacyRuntimeTest,com.yujian.ai.ui.designsystem.DesignSystemComponentPreviewTest'
}

gate_collect_evidence() {
  local login_dir="$YUJIAN_EVIDENCE_DIR/login-v2"
  mkdir -p "$login_dir"

  local remote="/sdcard/Android/data/$YUJIAN_APP_PACKAGE/files/login_v2"
  if ! "$YUJIAN_ADB_BIN" shell ls -la "$remote" > "$login_dir/device_files.txt" 2>&1; then
    runtime_set_failure "EVIDENCE" "AUTH_V2_DEVICE_EVIDENCE_DIRECTORY_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi
  "$YUJIAN_ADB_BIN" pull "$remote/." "$login_dir/" > "$login_dir/adb_pull.log" 2>&1 || {
    runtime_set_failure "EVIDENCE" "AUTH_V2_SCREENSHOT_PULL_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  }

  local missing=0 file
  for file in \
    login_v2_idle.png \
    login_v2_focus.png \
    login_v2_filled.png \
    login_v2_password_visible.png \
    login_v2_loading.png \
    login_v2_error.png \
    login_v2_validation_error.png \
    login_v2_keyboard.png \
    login_v2_small_screen.png \
    register_v2_idle.png \
    register_v2_focus.png \
    register_v2_filled.png \
    register_v2_error.png \
    register_v2_loading.png \
    register_v2_keyboard.png \
    register_v2_small_screen.png \
    p0_shared_components_default.png \
    p0_button_pressed.png \
    p0_button_disabled.png \
    p0_button_loading.png \
    account_profile_idle.png \
    account_profile_invalid_nickname.png \
    account_avatar_source_sheet.png \
    account_change_password_visible.png \
    shared_components_accessibility.png \
    shared_components_compact_large_font.png
  do
    if [[ ! -s "$login_dir/$file" ]]; then
      printf 'MISSING_EVIDENCE=%s\n' "$login_dir/$file" >> "$YUJIAN_EVIDENCE_DIR/evidence_missing.log"
      missing=1
    fi
  done
  if (( missing != 0 )); then
    runtime_set_failure "EVIDENCE" "AUTH_V2_EVIDENCE_MISSING"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  python3 - \
    "$YUJIAN_REPO_ROOT/design/pages/account_privacy/Login/frozen/Login_V2_1_Frozen_Final.png" \
    "$login_dir/login_v2_idle.png" \
    "$YUJIAN_REPO_ROOT/design/pages/account_privacy/Register/frozen/Register_V2_Frozen_Final.png" \
    "$login_dir/register_v2_idle.png" \
    "$login_dir" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageStat

login_reference = Path(sys.argv[1])
login_runtime = Path(sys.argv[2])
register_reference = Path(sys.argv[3])
register_runtime = Path(sys.argv[4])
out_dir = Path(sys.argv[5])

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
        raise SystemExit("AUTH_V2_ACTIVE_SURFACE_EMPTY")
    left, top, right, bottom = min(xs), min(ys), max(xs) + 1, max(ys) + 1
    if image.width >= 2 * right - 8 and image.height >= 2 * bottom - 8:
        right = image.width // 2
        bottom = image.height // 2
        left = 0
        top = 0
    surface = image.crop((left, top, right, bottom))
    if surface.width < 200 or surface.height < 400:
        raise SystemExit(
            f"AUTH_V2_ACTIVE_SURFACE_INVALID size={surface.width}x{surface.height}"
        )
    return surface

def mae(a: Image.Image, b: Image.Image) -> float:
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3.0

regions = {
    "full": (0.00, 0.00, 1.00, 1.00),
    "environment": (0.00, 0.00, 1.00, 0.36),
    "title": (0.05, 0.30, 0.90, 0.16),
    "form": (0.05, 0.43, 0.90, 0.37),
    "footer": (0.04, 0.80, 0.92, 0.18),
}
thresholds = {
    "full": 65.0,
    "environment": 62.0,
    "title": 62.0,
    "form": 62.0,
    "footer": 65.0,
}

def compare(label: str, reference_path: Path, runtime_path: Path) -> str:
    if not reference_path.is_file():
        raise SystemExit(f"AUTH_V2_REFERENCE_MISSING {reference_path}")
    reference = Image.open(reference_path).convert("RGB")
    runtime_raw = Image.open(runtime_path).convert("RGB")
    runtime = active_surface(runtime_raw)
    runtime.save(out_dir / f"{label}_idle_normalized.png")
    reference_scaled = reference.resize(runtime.size, Image.Resampling.LANCZOS)

    metrics = {}
    for name, (x, y, w, h) in regions.items():
        box = (
            int(runtime.width * x),
            int(runtime.height * y),
            int(runtime.width * (x + w)),
            int(runtime.height * (y + h)),
        )
        metrics[name] = round(
            mae(reference_scaled.crop(box), runtime.crop(box)),
            4,
        )

    status = "PASS" if all(
        metrics[key] <= thresholds[key] for key in thresholds
    ) else "FAIL"
    report = {
        "status": status,
        "reference": str(reference_path),
        "runtime": str(runtime_path),
        "runtime_raw_size": list(runtime_raw.size),
        "runtime_size": list(runtime.size),
        "metrics_mae": metrics,
        "thresholds": thresholds,
    }
    (out_dir / f"{label}_visual_parity_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    canvas = Image.new("RGB", (runtime.width * 2, runtime.height), "white")
    canvas.paste(reference_scaled, (0, 0))
    canvas.paste(runtime, (runtime.width, 0))
    draw = ImageDraw.Draw(canvas)
    draw.text((8, 8), "Frozen", fill="black")
    draw.text((runtime.width + 8, 8), "Runtime", fill="black")
    canvas.save(out_dir / f"{label}_frozen_side_by_side.png")

    print("AUTH_V2_VISUAL_PARITY", label, json.dumps(report, ensure_ascii=False))
    return status

statuses = [
    compare("login_v2", login_reference, login_runtime),
    compare("register_v2", register_reference, register_runtime),
]
if any(status != "PASS" for status in statuses):
    raise SystemExit("AUTH_V2_VISUAL_PARITY_FAILED")
PY
  local parity_rc=$?
  if (( parity_rc != 0 )); then
    runtime_set_failure "EVIDENCE" "AUTH_V2_VISUAL_PARITY_FAILED"
    return "$EXIT_FAIL_EVIDENCE"
  fi

  return "$EXIT_PASS"
}
