#!/usr/bin/env python3
"""Feature-neutral design-side checks delegated to the Android V2 verifier."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


if __name__ == "__main__":
    sys.exit(subprocess.call([sys.executable, str(ROOT / "scripts/verify_empty_home_runtime_v2.py")]))
