#!/usr/bin/env python3
"""Ensure every current Yujian NavHost route has an explicit Lake Glass policy."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAV = ROOT / "app/src/main/java/com/yujian/ai/YujianApp.kt"
DETAIL = ROOT / "app/src/main/java/com/yujian/ai/ui/recorddetail/FishRecordDetailState.kt"
MAP = ROOT / "design/system/backgrounds/lake_glass_v2/evidence/android_shader_reuse/route_background_coverage_v2.json"


def fail(message: str) -> None:
    raise SystemExit(f"LAKE_GLASS_ROUTE_COVERAGE=FAIL: {message}")


def main() -> int:
    source = NAV.read_text(encoding="utf-8")
    detail_source = DETAIL.read_text(encoding="utf-8")
    expected = set(re.findall(r'composable\(\s*(?:route\s*=\s*)?"([^"]+)"', source))
    match = re.search(r'const\s+val\s+FishRecordDetailRoute\s*=\s*"([^"]+)"', detail_source)
    if not match:
        fail("FishRecordDetailRoute constant could not be resolved")
    expected.add(match.group(1))
    data = json.loads(MAP.read_text(encoding="utf-8"))
    routes = data.get("routes", [])
    mapped = {entry["route"] for entry in routes}
    missing = sorted(expected - mapped)
    stale = sorted(mapped - expected)
    if missing or stale:
        fail(f"NavHost route mismatch; missing={missing}; stale={stale}")
    for entry in routes:
        policy = entry.get("policy")
        if policy == "LAKE_GLASS" and not entry.get("host"):
            fail(f"Lake Glass route has no shared host: {entry['route']} / {entry['screen']}")
        if policy == "PROTECTED_OPAQUE_CONTENT" and not entry.get("reason"):
            fail(f"opaque-content exception has no reason: {entry['route']}")
        if policy == "KEEP_EXISTING" and entry.get("route") == "home" and entry.get("state") != "EMPTY_NO_CATCHES":
            fail("home exception is only valid for the Empty Home state")
    home_states = {entry.get("state") for entry in routes if entry.get("route") == "home"}
    if home_states != {"NORMAL_WITH_CATCHES", "EMPTY_NO_CATCHES"}:
        fail(f"home state coverage incomplete: {sorted(str(value) for value in home_states)}")
    if data.get("frozenAssetsModified") is not False or data.get("recognitionClockModified") is not False:
        fail("protected asset or recognition-clock status must remain explicitly false")
    print(f"LAKE_GLASS_ROUTE_COVERAGE=PASS routes={len(expected)} entries={len(routes)}")
    print(f"home_states={sorted(home_states)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
