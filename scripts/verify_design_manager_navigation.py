#!/usr/bin/env python3
"""Regression gate for YuJian Design Manager navigation and Pages artifact."""

from __future__ import annotations

import argparse
import json
import hashlib
import struct
from pathlib import Path
import sys

EXPECTED_NORMAL_HOME = ["NH01", "NH02", "NH03", "NH04", "NH05", "NH06", "NH07"]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def load_json(path: Path):
    if not path.exists():
        fail(f"missing file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"invalid json {path}: {exc}")


def validate_tree(root: Path, *, built: bool = False, expected_build: str | None = None) -> None:
    design = root / "design"
    registry_path = design / "registry" / "experience_registry_v1.json"
    nav_path = design / "pages" / "home" / "normal_home" / "navigation.json"
    app_path = design / "manager" / "app.js"
    index_path = design / "manager" / "index.html"

    registry = load_json(registry_path)
    nav = load_json(nav_path)

    feature = next((x for x in registry.get("features", []) if x.get("id") == "home_normal_v1"), None)
    if not feature:
        fail("home_normal_v1 missing from experience registry")

    hifi_ids = [x.get("id") for x in feature.get("hifi_views", [])]
    if hifi_ids != EXPECTED_NORMAL_HOME:
        fail(f"home_normal_v1 hifi_views changed: {hifi_ids} != {EXPECTED_NORMAL_HOME}")

    nav_ids = [x.get("id") for x in nav.get("level_2", [])]
    if nav_ids != EXPECTED_NORMAL_HOME:
        fail(f"Normal Home navigation changed: {nav_ids} != {EXPECTED_NORMAL_HOME}")

    # NH07 is a design-only asset workspace: gate its source identity, not Android runtime.
    if not built:
        nh07 = feature["hifi_views"][-1]
        if nh07.get("id") != "NH07" or nh07.get("render_mode") != "nh07_avatar_states":
            fail("NH07 must be registered as its own avatar state workspace")
        manifest_path = root / "design/pages/home/normal_home/07_avatar_states/assets/avatar_asset_manifest_v1.json"
        manifest = load_json(manifest_path)
        expected = [(64, "1x"), (128, "2x"), (192, "3x")]
        exports = manifest.get("exports", [])
        if len(exports) != 3:
            fail("NH07 requires exactly three registered avatar density exports")
        if nh07.get("image") != exports[-1].get("path"):
            fail("NH07 registry preview must use the registered 3x avatar")
        for asset, (side, scale) in zip(exports, expected):
            if asset.get("scale") != scale or asset.get("width") != side or asset.get("height") != side:
                fail(f"NH07 density manifest ordering/dimensions incorrect for {scale}")
            file_path = root / asset["path"]
            if not file_path.exists():
                fail(f"NH07 asset missing: {file_path}")
            raw = file_path.read_bytes()
            if len(raw) != asset["bytes"] or hashlib.sha256(raw).hexdigest() != asset["sha256"]:
                fail(f"NH07 PNG bytes/hash mismatch: {file_path}")
            if raw[:8] != b"\x89PNG\r\n\x1a\n" or len(raw) < 24:
                fail(f"NH07 invalid PNG header: {file_path}")
            width, height = struct.unpack(">II", raw[16:24])
            if (width, height) != (side, side):
                fail(f"NH07 actual PNG dimensions mismatch: {file_path}")
        # The source is a real 1254px original, never an enlarged 192px export.
        master = manifest.get("high_resolution_master", {})
        source = manifest.get("source", {})
        master_path = master.get("path")
        expected_master_sha = "12ec5fc8c1a08723ea461edf327870df19b7dfe309587b18b6de28c9f701b643"
        if not master_path or source.get("path") != master_path or master.get("sha256") != expected_master_sha:
            fail("NH07 mother path/identity must be recorded consistently in source/high_resolution_master")
        if nh07.get("master_source") != master_path or nh07.get("master_sha256") != expected_master_sha:
            fail("NH07 registry must expose exact high-resolution mother")
        if (master.get("width"), master.get("height"), master.get("bytes")) != (1254, 1254, 1255352):
            fail("NH07 mother metadata must match the approved source bytes")
        master_file = root / master_path
        if not master_file.exists():
            fail(f"NH07 mother original missing: {master_path}")
        master_raw = master_file.read_bytes()
        if len(master_raw) != 1255352 or hashlib.sha256(master_raw).hexdigest() != expected_master_sha:
            fail("NH07 original mother SHA/byte count mismatch")
        if master_raw[:8] != b"\x89PNG\r\n\x1a\n" or struct.unpack(">II", master_raw[16:24]) != (1254, 1254):
            fail("NH07 mother file is not an original 1254px PNG")
        guest = manifest.get("states", {}).get("GUEST", "")
        if not guest or not (root / guest).exists():
            fail("NH07 Guest must resolve to separate registered source")

    # Record Date V2 is design-only but both committed original images are byte-identified.
    record = next((f for f in registry.get("features", []) if f.get("id") == "record_date_v2"), None)
    if record is None or record.get("display_name") != "记录日期":
        fail("Record Date V2 top-level navigation missing")
    if [v.get("id") for v in record.get("hifi_views", [])] != ["month_v2", "year_v2", "interaction"]:
        fail("Record Date V2 must register month/year/interaction direct submenus")
    record_manifest = load_json(design / "pages/record_date/v2/assets/asset_manifest.json")
    for view_id, entry_key, dim in [
        ("month_v2", "month", (853, 1844)),
        ("year_v2", "year", (935, 1683)),
    ]:
        entry = record_manifest[entry_key]
        view = next(v for v in record["hifi_views"] if v["id"] == view_id)
        if view.get("status") != "ACTIVE_CLOSURE" or view.get("image") != entry["path"]:
            fail(f"Record Date {view_id} must render registered pending-review PNG")
        data_path = root / entry["path"]
        if not data_path.is_file():
            fail(f"Record Date V2 source PNG absent: {entry['path']}")
        data = data_path.read_bytes()
        if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data) != entry["bytes"]:
            fail(f"Record Date PNG bytes/header mismatch: {view_id}")
        width, height = struct.unpack(">II", data[16:24])
        if (width, height) != dim or hashlib.sha256(data).hexdigest() != entry["sha256"]:
            fail(f"Record Date image SHA256/dimensions mismatch: {view_id}")
    if record.get("design_overall") != "ACTIVE_CLOSURE":
        fail("Record Date images must remain un-frozen until design review")

    app = app_path.read_text(encoding="utf-8") if app_path.exists() else ""
    required_logic = '(selectedKey === key || selectedKey.startsWith(key + "/"))'
    if required_logic not in app:
        fail("selected page no longer auto-expands its submenu")

    if "nh07-master-open" not in app or "openNh07MasterLightbox" not in app or "dialog.showModal" not in app:
        fail("NH07 must provide a clickable modal preview of its verified mother image")

    index = index_path.read_text(encoding="utf-8") if index_path.exists() else ""
    if built:
        if "__DESIGN_MANAGER_BUILD__" in index or "__DESIGN_MANAGER_BUILD_SHORT__" in index:
            fail("Pages artifact still contains unresolved build placeholders")
        if expected_build and expected_build not in index:
            fail(f"Pages artifact does not identify expected build {expected_build}")
        if "app.js?v=" not in index or "styles.css?v=" not in index:
            fail("Pages artifact lost cache-busted JS/CSS URLs")
    else:
        if 'app.js?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost app.js build cache-busting placeholder")
        if 'styles.css?v=__DESIGN_MANAGER_BUILD__' not in index:
            fail("source index lost styles.css build cache-busting placeholder")
        if 'name="design-manager-build" content="__DESIGN_MANAGER_BUILD__"' not in index:
            fail("source index lost deployment build marker")

    print(
        "PASS: Design Manager navigation guard | "
        f"Normal Home={','.join(hifi_ids)} | built={built}"
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, default=None)
    parser.add_argument("--expected-build", default=None)
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    validate_tree(repo_root)

    if args.site is not None:
        validate_tree(args.site.resolve(), built=True, expected_build=args.expected_build)


if __name__ == "__main__":
    main()
