#!/usr/bin/env python3
"""Capture a native Android screenshot only if YuJian owns the foreground."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

from PIL import Image, ImageStat


def run(adb: str, *args: str, binary: bool = False):
    result = subprocess.run([adb, *args], stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=25, check=True)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def foreground(adb: str, package: str) -> str:
    activities = run(adb, "shell", "dumpsys", "activity", "activities")
    windows = run(adb, "shell", "dumpsys", "window", "windows")
    activity_lines = [x for x in activities.splitlines()
                      if "mResumedActivity" in x or "ResumedActivity:" in x]
    focused_lines = [x for x in windows.splitlines()
                     if "mCurrentFocus" in x or "mFocusedApp" in x]
    if not activity_lines or not focused_lines:
        raise RuntimeError("NO_FOREGROUND_ACTIVITY_OR_WINDOW")
    activity = activity_lines[0]
    focus = next((x for x in focused_lines if "mCurrentFocus" in x), focused_lines[0])
    # Android applicationIdSuffix variants can launch a class from the base
    # package (for example com.yujian.ai.uiv2/com.yujian.ai.MainActivity).
    # Foreground ownership is the component package before "/", not the class
    # package after it.
    pattern = re.escape(package) + r"/([A-Za-z0-9_.$]+)"
    match = re.search(pattern, activity)
    if match is None or re.search(pattern, focus) is None:
        raise RuntimeError("WRONG_FOREGROUND_APP activity=" + activity + " focus=" + focus)
    component = match.group()
    if "/." in component:
        component = component.replace("/.", "/" + package + ".", 1)
    pid = run(adb, "shell", "pidof", package).strip()
    if not re.fullmatch(r"\d+(?:\s+\d+)*", pid):
        raise RuntimeError("APP_PROCESS_NOT_RUNNING")
    return component


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--adb", default="adb")
    p.add_argument("--package", required=True)
    p.add_argument("--build-sha", required=True)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--width", type=int)
    p.add_argument("--height", type=int)
    args = p.parse_args()

    # Never leave a screenshot/proof pair behind after failed validation.
    args.output.parent.mkdir(parents=True, exist_ok=True)
    proof = args.output.with_name(args.output.name + ".provenance.json")
    proof.unlink(missing_ok=True)
    args.output.unlink(missing_ok=True)
    activity_before = foreground(args.adb, args.package)
    png = run(args.adb, "exec-out", "screencap", "-p", binary=True)
    args.output.write_bytes(png)
    activity_after = foreground(args.adb, args.package)
    if activity_before != activity_after:
        raise RuntimeError("FOREGROUND_CHANGED_DURING_CAPTURE")
    with Image.open(args.output) as image:
        size = image.size
        if ((args.width is not None and size[0] != args.width)
                or (args.height is not None and size[1] != args.height)):
            raise RuntimeError("NATIVE_SCREENSHOT_DIMENSIONS_INVALID " + str(size))
        if min(size) < 200 or max(ImageStat.Stat(image.convert("RGB")).stddev) < 4:
            raise RuntimeError("SCREENSHOT_BLANK_OR_INVALID")
    h = hashlib.sha256(png).hexdigest()
    proof.write_text(json.dumps({
        "package": args.package, "build_sha": args.build_sha,
        "foreground_verified": True, "resumed_activity": activity_after,
        "capture_method": "adb-exec-out-screencap",
        "screenshot_sha256": h, "native_dimensions": list(size),
        "capture_utc": datetime.now(timezone.utc).isoformat(),
        "device_serial": run(args.adb, "get-serialno").strip(),
    }, indent=2) + "\n", encoding="utf-8")
    print("VERIFIED_HIFI_SCREEN " + str(args.output) + " " + h)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, subprocess.SubprocessError, RuntimeError) as exc:
        print("FAIL_EVIDENCE: " + str(exc), file=sys.stderr)
        sys.exit(40)
