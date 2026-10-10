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
            if raw[:8] != b"\\x89PNG\\r\\n\\x1a\\n" or len(raw) < 24:
                fail(f"NH07 invalid PNG header: {file_path}")
            width, height = struct.unpack(">II", raw[16:24])
            if (width, height) != (side, side):
                fail(f"NH07 actual PNG dimensions mismatch: {file_path}")
        guest = manifest.get("states", {}).get("GUEST", "")
        if not guest or not (root / guest).exists():
            fail("NH07 Guest must resolve to separate registered source")

    app = app_path.read_text(encoding="utf-8") if app_path.exists() else ""
    required_logic = '(selectedKey === key || selectedKey.startsWith(key + "/"))'
    if required_logic not in app:
        fail("selected page no longer auto-expands its submenu")

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
