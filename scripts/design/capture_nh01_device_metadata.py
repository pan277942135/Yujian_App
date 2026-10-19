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


def parse_wm_size(output: str) -> tuple[int, int] | None:
    """Prefer an explicitly configured WindowManager size over physical size."""
    for source in ("Override", "Physical"):
        match = re.search(rf"{source} size: (\d+)x(\d+)", output)
        if match:
            return int(match.group(1)), int(match.group(2))
    return None


def parse_wm_density(output: str) -> int | None:
    """Prefer the WindowManager density override over the panel's physical DPI."""
    for source in ("Override", "Physical"):
        match = re.search(rf"{source} density: (\d+)", output)
        if match:
            return int(match.group(1))
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--insets-dump", type=Path, required=True)
    parser.add_argument("--allow-unavailable", action="store_true")
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.insets_dump.parent.mkdir(parents=True, exist_ok=True)
    acceleration = os.environ.get("NH01_EMULATOR_ACCELERATION", "unknown")
    kvm_preflight = {
        "status": os.environ.get("NH01_KVM_PREFLIGHT_STATUS", "NOT_RUN"),
        "source_run_id": os.environ.get("NH01_KVM_PREFLIGHT_RUN_ID"),
        "source_sha": os.environ.get("NH01_KVM_PREFLIGHT_SOURCE_SHA"),
    }
    software_tcg_attempt = {
        "status": os.environ.get("NH01_TCG_ATTEMPT_STATUS", "NOT_RUN"),
        "source_run_id": os.environ.get("NH01_TCG_ATTEMPT_RUN_ID"),
        "source_sha": os.environ.get("NH01_TCG_ATTEMPT_SOURCE_SHA"),
        "failure": os.environ.get("NH01_TCG_ATTEMPT_FAILURE"),
    }
    def write_unavailable() -> int:
        payload = {
            "profile": "Pixel 2",
            "api_level": 28,
            "resolution_px": None,
            "density_dpi": None,
            "density_scale": None,
            "insets_px": None,
            "runner": os.environ.get("RUNNER_NAME"),
            "runner_os": os.environ.get("RUNNER_OS"),
            "runner_architecture": os.environ.get("RUNNER_ARCH"),
            "emulator_acceleration": acceleration,
            "kvm_preflight": kvm_preflight,
            "software_tcg_attempt": software_tcg_attempt,
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
    size = parse_wm_size(size_text)
    density = parse_wm_density(density_text)
    payload = {
        "profile": "Pixel 2",
        "api_level": int(api) if api.isdigit() else api,
        "resolution_px": list(size) if size else None,
        "density_dpi": density,
        "density_scale": round(density / 160, 3) if density else None,
        "font_scale": 1.0,
        "insets_px": None,
        "insets_capture": str(args.insets_dump),
        "build_fingerprint": fingerprint,
        "device_model": model,
        "runner": os.environ.get("RUNNER_NAME"),
        "runner_os": os.environ.get("RUNNER_OS"),
        "runner_architecture": os.environ.get("RUNNER_ARCH"),
        "runner_image": os.environ.get("ImageOS"),
        "emulator_acceleration": acceleration,
        "kvm_preflight": kvm_preflight,
        "software_tcg_attempt": software_tcg_attempt,
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if payload["api_level"] != 28 or payload["resolution_px"] != [1080, 1920] or payload["density_dpi"] != 480:
        raise SystemExit("Pixel 2 API28 display profile does not meet 1080x1920@480dpi contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
