#!/usr/bin/env python3
"""Capture Pixel 2 API28 display properties and raw Window Insets diagnostics."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path


def adb(*args: str) -> str:
    result = subprocess.run(
        [shutil.which("adb") or "adb", *args],
        check=True,
        capture_output=True,
        text=True,
        timeout=20,
    )
    return result.stdout.replace("\r", "").strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--insets-dump", type=Path, required=True)
    parser.add_argument("--allow-unavailable", action="store_true")
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.insets_dump.parent.mkdir(parents=True, exist_ok=True)
    def write_unavailable() -> int:
        payload = {
            "profile": "Pixel 2",
            "api_level": 28,
            "resolution_px": None,
            "density_dpi": None,
            "density_scale": None,
            "insets_px": None,
            "runner": os.environ.get("RUNNER_NAME"),
            "device_status": "BLOCKED_INFRA",
            "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        }
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return 0

    if not shutil.which("adb"):
        if not args.allow_unavailable:
            raise SystemExit("adb is unavailable")
        return write_unavailable()

    try:
        adb("wait-for-device")
        adb("shell", "wm", "size", "1080x1920")
        adb("shell", "wm", "density", "480")
        adb("shell", "settings", "put", "system", "font_scale", "1.0")
        api = adb("shell", "getprop", "ro.build.version.sdk")
        model = adb("shell", "getprop", "ro.product.model")
        fingerprint = adb("shell", "getprop", "ro.build.fingerprint")
        size_text = adb("shell", "wm", "size")
        density_text = adb("shell", "wm", "density")
        window_dump = adb("shell", "dumpsys", "window", "displays")
    except (OSError, subprocess.SubprocessError) as error:
        if not args.allow_unavailable:
            raise SystemExit(f"ADB device metadata capture failed: {error}")
        return write_unavailable()
    args.insets_dump.write_text(window_dump + "\n", encoding="utf-8")
    size = re.search(r"(?:Override|Physical) size: (\d+)x(\d+)", size_text)
    density = re.search(r"(?:Override|Physical) density: (\d+)", density_text)
    payload = {
        "profile": "Pixel 2",
        "api_level": int(api) if api.isdigit() else api,
        "resolution_px": [int(size.group(1)), int(size.group(2))] if size else None,
        "density_dpi": int(density.group(1)) if density else None,
        "density_scale": round(int(density.group(1)) / 160, 3) if density else None,
        "font_scale": 1.0,
        "insets_px": None,
        "insets_capture": str(args.insets_dump),
        "build_fingerprint": fingerprint,
        "device_model": model,
        "runner": os.environ.get("RUNNER_NAME"),
        "runner_image": os.environ.get("ImageOS"),
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if payload["api_level"] != 28 or payload["resolution_px"] != [1080, 1920] or payload["density_dpi"] != 480:
        raise SystemExit("Pixel 2 API28 display profile does not meet 1080x1920@480dpi contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
