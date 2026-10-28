#!/usr/bin/env python3
"""Verify production recognition assets in the assembled debug APK."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from build_android_qa import verify_packaged_apk  # noqa: E402


def main() -> int:
    apk = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
    verification = verify_packaged_apk(apk)
    print("PACKAGED_RECOGNITION_ASSETS_PASS")
    print(f"apk={apk}")
    print(f"verification={verification}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
