#!/usr/bin/env python3
"""Android-only P0 verifier for Empty Home V2.  V1 verifier is intentionally untouched."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FEATURE = ROOT / "design/pages/home/empty_home"
RUNTIME = ROOT / "app/src/main/assets/empty_home_runtime_v2"
REQUIRED = {
    "static/scene_base.webp",
    "dynamic/cloud.png", "dynamic/sun_beam_mask.png", "dynamic/particle_mask.png",
    "dynamic/rod.png", "dynamic/line.png", "dynamic/bobber.png", "dynamic/ripple_mask.png",
    "camera/camera_button_base.png", "camera/camera_gold_rim_mask.png", "camera/camera_breath_glow.png",
    "config/runtime_manifest.json", "config/layer_contract.json", "config/anchor_contract.json",
    "config/motion_contract.json", "config/haptic_contract.json",
}
FORBIDDEN = ("proof", "preview", "validation", ".mp4", ".gif", "frozen", "source")


def load(path: Path) -> dict:
    return json.loads(path.read_text())


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def main() -> int:
    errors: list[str] = []
    report: dict[str, object] = {"gate": "EMPTY_HOME_RUNTIME_V2", "errors": errors}
    missing = sorted(item for item in REQUIRED if not (RUNTIME / item).is_file())
    if missing:
        fail(errors, "missing runtime assets: " + ", ".join(missing))
    forbidden = sorted(str(path.relative_to(RUNTIME)) for path in RUNTIME.rglob("*") if path.is_file() and any(token in path.name.lower() for token in FORBIDDEN))
    if forbidden:
        fail(errors, "forbidden runtime payloads: " + ", ".join(forbidden))
    if errors:
        print(json.dumps(report, indent=2))
        return 1

    runtime = load(RUNTIME / "config/runtime_manifest.json")
    anchors = load(RUNTIME / "config/anchor_contract.json")
    motion = load(RUNTIME / "config/motion_contract.json")
    layers = load(RUNTIME / "config/layer_contract.json")
    source = load(FEATURE / "source/provenance/source_manifest.json")
    production = load(FEATURE / "intermediate/validation/asset_production_report.json")
    asset_manifest = load(FEATURE / "shared/contracts/asset_manifest.json")

    if runtime.get("design_version") != "Empty_Home_Final_Design_V2":
        fail(errors, "runtime manifest does not name Frozen V2")
    if runtime.get("reference_canvas") != [1080, 1920]:
        fail(errors, "runtime reference canvas must be [1080, 1920]")
    for relative_path, expected_sha in runtime.get("sha256", {}).items():
        candidate = RUNTIME / relative_path
        if not candidate.is_file() or digest(candidate) != expected_sha:
            fail(errors, "runtime asset SHA256 mismatch: " + relative_path)
    if layers.get("order") != ["scene_base", "cloud_atmosphere", "sun_ambient", "rod", "line", "ripple", "bobber", "native_ui"]:
        fail(errors, "layer order does not preserve Ripple below Bobber")
    if layers.get("rules", {}).get("ripple_count") != 1 or layers.get("rules", {}).get("scene_base_has_baked_ripple") is not False:
        fail(errors, "single-ripple/no-baked-ripple rule failed")
    if anchors["ripple"]["center_reference_px"] != anchors["bobber"]["water_contact_reference_px"]:
        fail(errors, "ripple center must equal bobber water contact")
    bobber = motion.get("bobber", {})
    if (bobber.get("axis"), bobber.get("range_reference_px"), bobber.get("duration_ms"), bobber.get("rotation_deg"), bobber.get("scale_animation")) != ("y", 3, 4600, 0, False):
        fail(errors, "bobber Motion V2 contract failed")
    if bobber.get("keyframes") != [[0, 0], [1150, -3], [2300, 0], [3450, 3], [4600, 0]]:
        fail(errors, "bobber keyframes differ from V2")
    ripple = motion.get("ripple", {})
    if (ripple.get("count"), ripple.get("scale_from"), ripple.get("scale_to"), ripple.get("alpha_from"), ripple.get("alpha_to"), ripple.get("duration_ms")) != (1, 1.0, 1.22, 0.30, 0.0, 3200):
        fail(errors, "ripple Motion V2 contract failed")
    if motion.get("cloud", {}).get("speed_reference_px_per_s") != 0.2:
        fail(errors, "cloud speed must be 0.2 reference px/s")
    beam = motion.get("sun_particle_beam", {})
    if beam.get("duration_ms") != 4800 or beam.get("max_alpha", 1) > .18 or beam.get("particle_count_range") != [6, 12]:
        fail(errors, "sun particle beam contract failed")
    rim = motion.get("camera_gold_rim", {})
    if (rim.get("first_delay_ms"), rim.get("duration_ms"), rim.get("repeat_interval_ms")) != (3000, 1400, 9000):
        fail(errors, "camera gold rim contract failed")
    if motion.get("camera_breath", {}).get("max_scale", 2) > 1.015:
        fail(errors, "camera breath max scale exceeds V2")
    if source["frozen_input"]["sha256"] != digest(FEATURE / "source/frozen/Empty_Home_Final_Design_V2.png"):
        fail(errors, "frozen source SHA256 mismatch")
    if production.get("status") != "PASS":
        fail(errors, "asset production/static recompose gate is not PASS")
    for asset in asset_manifest.get("assets", []):
        asset_path = ROOT / asset["path"]
        if not asset_path.is_file() or asset.get("sha256") != digest(asset_path):
            fail(errors, "shared asset SHA256 mismatch: " + asset.get("asset_id", "unknown"))

    report.update({
        "status": "PASS" if not errors else "FAIL",
        "design_version": runtime.get("design_version"),
        "reference_canvas": runtime.get("reference_canvas"),
        "asset_count": len(asset_manifest.get("assets", [])),
        "runtime_file_count": len([path for path in RUNTIME.rglob("*") if path.is_file()]),
        "asset_production": production.get("status"),
    })
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
