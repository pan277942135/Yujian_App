#!/usr/bin/env python3
"""Fail if the built APK omits a required YuJian launcher icon resource."""

from __future__ import annotations

import hashlib
import os
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"

REQUIRED_RESOURCES = (
    "drawable/ic_launcher_background",
    "drawable/ic_launcher_foreground",
    "drawable/ic_launcher_monochrome",
    "mipmap/ic_launcher",
    "mipmap/ic_launcher_round",
)


def main() -> int:
    aapt2 = os.environ.get("YUJIAN_AAPT2")
    if not aapt2 or not Path(aapt2).is_file():
        raise RuntimeError("YUJIAN_AAPT2 must point to the CI resource compiler")
    if not APK.is_file() or APK.stat().st_size == 0:
        raise RuntimeError(f"APK missing: {APK}")

    dump = subprocess.check_output([aapt2, "dump", "resources", str(APK)], text=True)
    missing = [resource for resource in REQUIRED_RESOURCES if resource not in dump]
    if missing:
        raise RuntimeError(f"APK launcher resources missing: {', '.join(missing)}")

    digest = hashlib.sha256(APK.read_bytes()).hexdigest()
    print("PACKAGED_LAUNCHER_ICON_PASS")
    print(f"apk={APK.relative_to(ROOT)}")
    print(f"apk_sha256={digest}")
    print("resources=" + ",".join(REQUIRED_RESOURCES))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, subprocess.CalledProcessError) as error:
        print(f"PACKAGED_LAUNCHER_ICON_FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
