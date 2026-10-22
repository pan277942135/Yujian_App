#!/usr/bin/env python3
"""Validate SWOS V1.1 static contracts, manager registration, privacy and scope."""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FRAME_PATH = ROOT / "design/pages/recognition/processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json"
TRANSITION_PATH = ROOT / "design/pages/recognition/continuity/SWOS_Shared_Photo_Transition_Contract_V1.json"
MANIFEST_PATH = ROOT / "design/pages/recognition/processing/optics/SWOS_Static_Asset_Manifest_V1_1.json"
REGISTRY_PATH = ROOT / "design/registry/experience_registry_v1.json"
EXPECTED_SOURCE_SHA = "ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"invalid/missing JSON {path.relative_to(ROOT)}: {exc}")


def rect_mid(a: dict, b: dict) -> dict:
    keys = ("x", "y", "width", "height")
    return {key: (a[key] + b[key]) / 2 for key in keys}


def same_rect(a: dict, b: dict) -> bool:
    return all(abs(float(a[key]) - float(b[key])) < 1e-7 for key in ("x", "y", "width", "height"))


frames = read_json(FRAME_PATH)
transition = read_json(TRANSITION_PATH)
manifest = read_json(MANIFEST_PATH)
registry = read_json(REGISTRY_PATH)

keys = frames.get("keyframes", [])
require(frames.get("frame_count") == 15 and len(keys) == 15, "must contain exactly 15 keyframes")
processing = [frame for frame in keys if frame.get("track") == "PROCESSING"]
rr09 = [frame for frame in keys if frame.get("track") == "RR09"]
require(len(processing) == 8 and [f["id"] for f in processing] == [f"P{i}" for i in range(8)], "Processing must be P0–P7, exactly 8 frames")
require(len(rr09) == 7 and [f["id"] for f in rr09] == ["R0", "R0.5", "R1", "R2", "R3", "R4", "R5"], "RR09 must be R0–R5 with R0.5, exactly 7 frames")
require([f["relative_ms"] for f in processing] == [0, 350, 950, 1200, 1550, 2050, 2600, 2800], "Processing time samples drifted")
require([f["relative_ms"] for f in rr09] == [0, 100, 200, 380, 550, 680, 850], "RR09 relative samples drifted")
require([f["global_nominal_ms"] for f in rr09] == [2800, 2900, 3000, 3180, 3350, 3480, 3650], "RR09 global samples drifted")
require(frames.get("processing", {}).get("duration_ms") == 2800 and frames.get("rr09", {}).get("duration_ms") == 850 and frames.get("total_nominal_duration_ms") == 3650, "duration must remain 2800 + 850 = 3650ms")
require(frames.get("playback_implemented") is False and frames.get("android_runtime") == "NOT_RUN", "static contract must not claim playback or Android runtime")
require(all(frame.get("business_state_change") is False for frame in keys), "a static sample may not add business states")
require(all(frame.get("species_name_reveal") == "HIDDEN" for frame in processing), "Processing may not reveal a species name")
require(processing[-1].get("ambient_water_strength") == 0 and processing[-1].get("fish_gold_strength") == 100, "P7 must release ambient water and preserve fish gold")
require([f["ambient_water_strength"] for f in processing] == [0, 10, 45, 75, 60, 35, 10, 0], "ambient water strength curve drifted")
require([f["fish_gold_strength"] for f in processing] == [0, 0, 15, 45, 100, 100, 100, 100], "fish gold strength curve drifted")
require([f["fish_gold_strength"] for f in processing[-4:]] == [100, 100, 100, 100], "fish gold must hold 1550–2800ms")
require(rr09[1]["id"] == "R0.5" and rr09[1]["relative_ms"] == 100 and rr09[1]["global_nominal_ms"] == 2900, "R0.5 must be at RR09 +100ms / global 2900ms")
require(rr09[2]["id"] == "R1" and rr09[2]["relative_ms"] == 200, "R1 must remain at RR09 +200ms")
samples = transition["samples"]
require(same_rect(samples["R0"]["foreground_rect"], transition["processing_photo_content_rect"]), "R0 must retain the Processing source foreground rect")
require(rr09[2]["gold_transfer_progress"] == 0 and rr09[3]["gold_transfer_progress"] == 100, "gold transfer must begin after the photo lands at R1")
require(rr09[-1]["ambient_water_strength"] == 0 and rr09[-1]["fish_gold_strength"] == 0, "R5 must have no continuing glow")

expected_foreground_mid = rect_mid(transition["processing_photo_content_rect"], transition["result_hero_photo_content_rect"])
expected_outer_mid = rect_mid(samples["R0"]["outer_rect"], samples["R1"]["outer_rect"])
require(same_rect(samples["R0.5"]["foreground_rect"], expected_foreground_mid), "R0.5 foreground must be the exact midpoint of P7 and R1")
require(same_rect(samples["R0.5"]["outer_rect"], expected_outer_mid), "R0.5 outer rect must be the exact midpoint of P7 and R1")
require(transition["sample_source"]["source_photo_sha256"] == EXPECTED_SOURCE_SHA, "sample photo identity SHA drifted")
require(transition["golden_trace_state"]["transform"] == "same foreground transform used by the photo", "gold geometry must share the photo transform")
require(transition["fish_geometry"]["production_mask"] is False and transition["fish_geometry"]["trust_state"] == "MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK", "manual contour must not be treated as production geometry")
require(transition["transform"]["stretch"] is False and transition["transform"]["generated_fill"] is False and transition["transform"]["photo_swap"] is False, "forbidden photo transform behavior found")

photo = manifest["source_photo"]
require(photo["sha256"] == EXPECTED_SOURCE_SHA and photo["repository_included"] is False, "source photo must be identified but excluded from this public repository")
require(photo["public_permission"] == "NOT_CONFIRMED", "photo publication gate must remain explicit")
require(manifest["derived_frames"]["repository_included"] is False and manifest["derived_frames"]["frame_count"] == 15, "private frame exports must not be added to public repository")
require(not any(ROOT.rglob("208758.jpg")), "private photo 208758.jpg leaked into the public repository tree")

flow = next(feature for feature in registry["features"] if feature.get("id") == "recognition_flow_v1_2")
static_view = next((view for view in flow.get("hifi_views", []) if view.get("id") == "swos_static_visual_v1_1"), None)
require(static_view is not None and static_view.get("render_mode") == "swos_static_visual_workspace", "Design Manager route/renderer registration missing")
require(static_view.get("status") == "DESIGN_ONLY" and static_view.get("adoption_status") == "DRAFT_DEPENDENT_ON_PR_186", "proposal must remain dependent and noncanonical")

changed = subprocess.run(["git", "diff", "--name-only", "origin/pr/186...HEAD"], cwd=ROOT, check=True, capture_output=True, text=True).stdout.splitlines()
for path in changed:
    require(not path.startswith("app/") and not path.startswith("android/") and not path.startswith("design/manager/rr09_motion/"), f"scope boundary violated by {path}")

print("SWOS_STATIC_VISUAL_V1_1=CONTRACT_PASS")
print("FRAME_COUNTS=PROCESSING:8 RR09:7 TOTAL:15")
print("DURATION_MS=2800+850=3650; R0.5=100ms/2900ms")
print("R0_R0_5_R1_GEOMETRY=SHARED_MIDPOINT_PASS")
print("PHOTO_PUBLICATION=NOT_CONFIRMED; SOURCE_AND_DERIVATIVES_EXCLUDED")
print("ANDROID_NOT_RUN; DYNAMIC_DEMO_NOT_RUN")
