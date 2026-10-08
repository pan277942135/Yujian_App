#!/usr/bin/env python3
"""Validate and complete an AndroidTest-produced Catch Hero screenshot matrix."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from pathlib import Path

from PIL import Image


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--matrix-dir", required=True, type=Path)
    parser.add_argument("--git-head", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--expected-size", required=True, help="Native display dimensions as WIDTHxHEIGHT")
    args = parser.parse_args()

    expected = tuple(int(part) for part in args.expected_size.lower().split("x", maxsplit=1))
    if len(expected) != 2:
        raise SystemExit("expected-size must be WIDTHxHEIGHT")
    manifest_path = args.matrix_dir / "android_runtime_manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    entries = manifest.get("screenshots", [])
    fixtures = manifest.get("fixture_manifest", {}).get("fixtures", [])
    fixture_by_id = {fixture["id"]: fixture for fixture in fixtures}
    sources = manifest.get("fixture_manifest", {}).get("sources", {})
    image_paths = sorted(args.matrix_dir.glob("*.png"))
    home = [path for path in image_paths if path.name.startswith("HOME_")]
    detail = [path for path in image_paths if path.name.startswith("DETAIL_")]
    if len(home) != 8 or len(detail) != 8 or len(image_paths) != 16:
        raise SystemExit(f"ANDROID_MATRIX_SCREENSHOT_COUNT home={len(home)} detail={len(detail)} total={len(image_paths)}")
    if len(entries) != 16:
        raise SystemExit(f"ANDROID_MATRIX_MANIFEST_ENTRIES={len(entries)} expected=16")

    entry_names = [entry.get("screenshot") for entry in entries]
    if len(set(entry_names)) != 16 or set(entry_names) != {path.name for path in image_paths}:
        raise SystemExit("ANDROID_MATRIX_SCREENSHOT_MANIFEST_MAPPING_INVALID")
    pages_by_fixture: dict[str, set[str]] = {}
    geometry: dict[str, list[dict[str, float]]] = {"HOME": [], "DETAIL": []}
    for entry in entries:
        fixture = fixture_by_id.get(entry.get("fixture_id"))
        if fixture is None or fixture["sha256"] != entry.get("fixture_sha256"):
            raise SystemExit(f"ANDROID_MATRIX_FIXTURE_HASH_MISMATCH id={entry.get('fixture_id')}")
        source = sources.get(fixture.get("source_key"))
        if source is None:
            raise SystemExit(f"ANDROID_MATRIX_SOURCE_PROVENANCE_MISSING id={entry.get('fixture_id')}")
        entry["source_photo_sha256"] = source["sha256"]
        entry["source_url"] = source["source_url"]
        entry["source_license"] = source["license"]
        if entry.get("trusted_fish_box") is not False or entry.get("mode") != "EVIDENCE_FIT":
            raise SystemExit(f"ANDROID_MATRIX_MODE_MISMATCH id={entry.get('fixture_id')} page={entry.get('page')}")
        bounds = entry.get("hero_bounds_root_px")
        if not isinstance(bounds, list) or len(bounds) != 4:
            raise SystemExit(f"ANDROID_MATRIX_GEOMETRY_MISSING id={entry.get('fixture_id')} page={entry.get('page')}")
        left, top, right, bottom = (float(value) for value in bounds)
        width, height = right - left, bottom - top
        if width <= 0 or height <= 0:
            raise SystemExit(f"ANDROID_MATRIX_GEOMETRY_INVALID id={entry.get('fixture_id')} page={entry.get('page')}")
        if entry["page"] == "HOME" and (abs(width - 740) > 8 or abs(height - 880) > 8):
            raise SystemExit(f"ANDROID_MATRIX_HOME_GEOMETRY_CHANGED id={entry.get('fixture_id')} bounds={bounds}")
        if entry["page"] == "DETAIL" and abs(width / height - 841 / 540) > 0.02:
            raise SystemExit(f"ANDROID_MATRIX_DETAIL_GEOMETRY_CHANGED id={entry.get('fixture_id')} bounds={bounds}")
        geometry[entry["page"]].append({"left": left, "top": top, "width": width, "height": height})
        pages_by_fixture.setdefault(entry["fixture_id"], set()).add(entry["page"])
        path = args.matrix_dir / entry["screenshot"]
        with Image.open(path) as image:
            if image.size != expected:
                raise SystemExit(f"ANDROID_MATRIX_SCREENSHOT_DIMENSIONS {path.name}={image.size} expected={expected}")
        entry["screenshot_sha256"] = sha256(path)
        entry["screenshot_bytes"] = path.stat().st_size
        entry["screenshot_dimensions"] = list(expected)
    if len(pages_by_fixture) != 8 or any(pages != {"HOME", "DETAIL"} for pages in pages_by_fixture.values()):
        raise SystemExit("ANDROID_MATRIX_HOME_DETAIL_PAIRING_INVALID")

    manifest["git_head"] = args.git_head
    manifest["screenshot_count"] = 16
    manifest["screenshot_capture"] = "Android UiAutomation.takeScreenshot from running Compose pages; full display; not a Frozen PNG composition"
    manifest["capture_resolution"] = args.expected_size
    manifest["measured_hero_geometry_root_px"] = geometry
    manifest["capture_device"] = subprocess.check_output(
        [args.adb, "shell", "getprop", "ro.product.model"], text=True
    ).strip()
    manifest["android_mode_selection_per_photo_and_page"] = [
        {
            "fixture_id": entry["fixture_id"],
            "page": entry["page"],
            "mode": entry["mode"],
            "input_sha256": entry["fixture_sha256"],
            "trusted_fish_box": entry["trusted_fish_box"],
            "screenshot": entry["screenshot"],
            "screenshot_sha256": entry["screenshot_sha256"],
        }
        for entry in entries
    ]
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"CATCH_HERO_ANDROID_SCREENSHOT_MATRIX PASS home={len(home)} detail={len(detail)} "
        f"display={args.expected_size} head={args.git_head}"
    )


if __name__ == "__main__":
    main()
