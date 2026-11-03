#!/usr/bin/env python3
"""Read-only capture of API 28 device/display metadata for NH01 native profiles."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import struct
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PROFILES: dict[str, dict[str, int]] = {
    "M04": {"width_px": 360, "height_px": 640, "density_dpi": 160},
    "M07": {"width_px": 390, "height_px": 844, "density_dpi": 160},
    "M10": {"width_px": 430, "height_px": 932, "density_dpi": 160},
}


def adb(*args: str) -> str:
    """Run a bounded ADB metadata read and return text output."""
    result = subprocess.run(
        [shutil.which("adb") or "adb", *args],
        check=True,
        capture_output=True,
        text=True,
        timeout=30,
    )
    return result.stdout.replace("\r", "").strip()


def adb_bytes(*args: str) -> bytes:
    """Run a bounded binary ADB read, used for the unmodified screencap PNG."""
    result = subprocess.run(
        [shutil.which("adb") or "adb", *args],
        check=True,
        capture_output=True,
        timeout=30,
    )
    return result.stdout


def parse_physical_size(output: str) -> tuple[int, int] | None:
    match = re.search(r"^Physical size: (\d+)x(\d+)\s*$", output, re.MULTILINE)
    return (int(match.group(1)), int(match.group(2))) if match else None


def parse_override_size(output: str) -> tuple[int, int] | None:
    match = re.search(r"^Override size: (\d+)x(\d+)\s*$", output, re.MULTILINE)
    return (int(match.group(1)), int(match.group(2))) if match else None


def parse_physical_density(output: str) -> int | None:
    match = re.search(r"^Physical density: (\d+)\s*$", output, re.MULTILINE)
    return int(match.group(1)) if match else None


def parse_override_density(output: str) -> int | None:
    match = re.search(r"^Override density: (\d+)\s*$", output, re.MULTILINE)
    return int(match.group(1)) if match else None


def png_dimensions(data: bytes) -> tuple[int, int] | None:
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", data[16:24])


def font_scale_value(output: str) -> float | None:
    try:
        return float(output.strip())
    except (TypeError, ValueError):
        return None


def unavailable_payload(profile: str, output: Path, screenshot: Path) -> dict[str, Any]:
    target = PROFILES[profile]
    return {
        "schema": "NH01_DEVICE_METADATA_V2",
        "profile": "Pixel 2",
        "display_profile": profile,
        "target": {
            "resolution_px": [target["width_px"], target["height_px"]],
            "density_dpi": target["density_dpi"],
        },
        "api_level": None,
        "device_model": None,
        "build_fingerprint": None,
        "wm_size_raw": None,
        "wm_physical_size_px": None,
        "wm_override_size_px": None,
        "wm_density_raw": None,
        "wm_physical_density_dpi": None,
        "wm_override_density_dpi": None,
        "resolution_px": None,
        "density_dpi": None,
        "density_scale": None,
        "font_scale": None,
        "screencap_dimensions_px": None,
        "screencap_path": str(screenshot),
        "insets_px": None,
        "insets_capture": str(output.with_name("window_insets_dump.txt")),
        "display_validation": {"status": "BLOCKED_INFRA", "failures": ["ADB_UNAVAILABLE"]},
        "runner": os.environ.get("RUNNER_NAME"),
        "runner_os": os.environ.get("RUNNER_OS"),
        "runner_architecture": os.environ.get("RUNNER_ARCH"),
        "runner_image": os.environ.get("ImageOS"),
        "emulator_acceleration": os.environ.get("NH01_EMULATOR_ACCELERATION", "unknown"),
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", choices=tuple(PROFILES), default="M04")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--insets-dump", type=Path, required=True)
    parser.add_argument("--screencap-output", type=Path)
    parser.add_argument("--allow-unavailable", action="store_true")
    args = parser.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.insets_dump.parent.mkdir(parents=True, exist_ok=True)
    screenshot_path = args.screencap_output or args.output.with_suffix(".png")
    screenshot_path.parent.mkdir(parents=True, exist_ok=True)

    if not shutil.which("adb"):
        if not args.allow_unavailable:
            raise SystemExit("adb is unavailable")
        payload = unavailable_payload(args.profile, args.output, screenshot_path)
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return 0

    try:
        adb("wait-for-device")
        api_raw = adb("shell", "getprop", "ro.build.version.sdk")
        model = adb("shell", "getprop", "ro.product.model")
        fingerprint = adb("shell", "getprop", "ro.build.fingerprint")
        wm_size_raw = adb("shell", "wm", "size")
        wm_density_raw = adb("shell", "wm", "density")
        font_scale_raw = adb("shell", "settings", "get", "system", "font_scale")
        window_dump = adb("shell", "dumpsys", "window", "displays")
        display_dump = adb("shell", "dumpsys", "display")
        screenshot_bytes = adb_bytes("exec-out", "screencap", "-p")
    except (OSError, subprocess.SubprocessError) as error:
        if not args.allow_unavailable:
            raise SystemExit(f"ADB device metadata capture failed: {error}")
        payload = unavailable_payload(args.profile, args.output, screenshot_path)
        payload["display_validation"] = {
            "status": "BLOCKED_INFRA",
            "failures": [f"ADB_CAPTURE_FAILED: {type(error).__name__}: {error}"],
        }
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return 0

    screenshot_path.write_bytes(screenshot_bytes)
    args.insets_dump.write_text(
        "=== dumpsys window displays ===\n" + window_dump + "\n\n=== dumpsys display ===\n" + display_dump + "\n",
        encoding="utf-8",
    )

    physical_size = parse_physical_size(wm_size_raw)
    override_size = parse_override_size(wm_size_raw)
    physical_density = parse_physical_density(wm_density_raw)
    override_density = parse_override_density(wm_density_raw)
    screenshot_size = png_dimensions(screenshot_bytes)
    target = PROFILES[args.profile]
    failures: list[str] = []
    if api_raw.strip() != "28":
        failures.append(f"API_LEVEL_EXPECTED_28_GOT_{api_raw.strip() or 'MISSING'}")
    if physical_size != (target["width_px"], target["height_px"]):
        failures.append(f"PHYSICAL_SIZE_EXPECTED_{target['width_px']}x{target['height_px']}_GOT_{physical_size}")
    if override_size is not None:
        failures.append(f"WM_SIZE_OVERRIDE_PRESENT_{override_size}")
    if physical_density != target["density_dpi"]:
        failures.append(f"PHYSICAL_DENSITY_EXPECTED_{target['density_dpi']}_GOT_{physical_density}")
    if override_density is not None:
        failures.append(f"WM_DENSITY_OVERRIDE_PRESENT_{override_density}")
    if screenshot_size != (target["width_px"], target["height_px"]):
        failures.append(f"SCREENCAP_EXPECTED_{target['width_px']}x{target['height_px']}_GOT_{screenshot_size}")
    font_scale = font_scale_value(font_scale_raw)
    if font_scale != 1.0:
        failures.append(f"FONT_SCALE_EXPECTED_1.0_GOT_{font_scale}")

    payload: dict[str, Any] = {
        "schema": "NH01_DEVICE_METADATA_V2",
        "profile": "Pixel 2",
        "display_profile": args.profile,
        "target": {
            "resolution_px": [target["width_px"], target["height_px"]],
            "density_dpi": target["density_dpi"],
        },
        "api_level": int(api_raw) if api_raw.isdigit() else api_raw,
        "device_model": model,
        "build_fingerprint": fingerprint,
        "wm_size_raw": wm_size_raw,
        "wm_physical_size_px": list(physical_size) if physical_size else None,
        "wm_override_size_px": list(override_size) if override_size else None,
        "wm_density_raw": wm_density_raw,
        "wm_physical_density_dpi": physical_density,
        "wm_override_density_dpi": override_density,
        # Compatibility keys now contain physical values only, never overrides.
        "resolution_px": list(physical_size) if physical_size else None,
        "density_dpi": physical_density,
        "density_scale": round(physical_density / 160, 3) if physical_density else None,
        "font_scale": font_scale,
        "font_scale_raw": font_scale_raw,
        "screencap_dimensions_px": list(screenshot_size) if screenshot_size else None,
        "screencap_path": str(screenshot_path),
        "insets_px": None,
        "insets_capture": str(args.insets_dump),
        "display_dump_capture": str(args.insets_dump),
        "display_validation": {"status": "PASS" if not failures else "FAIL", "failures": failures},
        "runner": os.environ.get("RUNNER_NAME"),
        "runner_os": os.environ.get("RUNNER_OS"),
        "runner_architecture": os.environ.get("RUNNER_ARCH"),
        "runner_image": os.environ.get("ImageOS"),
        "emulator_acceleration": os.environ.get("NH01_EMULATOR_ACCELERATION", "unknown"),
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    if failures:
        raise SystemExit("NH01 read-only device metadata validation failed: " + "; ".join(failures))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
