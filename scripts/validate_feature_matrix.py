#!/usr/bin/env python3
"""Validate the canonical static YuJian Feature Matrix."""
from __future__ import annotations
import argparse
from pathlib import Path
try:
    import yaml
except ImportError as exc:
    print("ERROR: PyYAML is required", file=__import__("sys").stderr)
    raise SystemExit(2) from exc

ROOT = Path(__file__).resolve().parents[1]
MATRIX_PATH = ROOT / "harness/FEATURE_MATRIX.yaml"
FEATURE_IDS = [
    "home_empty_v2","home_normal_v1","record_date_v2","recognition_flow_v1_2",
    "recognition_result_v1","fish_record_detail_v2","share_templates_v1",
    "fish_memory_bside_v1","my_catches_v2","fish_guide_v2","account_privacy_v1",
    "auth_login_v2","auth_register_v2","profile_edit_v1","user_agreement_v1",
]
JOURNEYS = [f"J{i:02d}" for i in range(1, 21)]
DYNAMIC_KEYS = {
    "acceptance","blockers","next_actions","current_branch_acceptance",
    "current_branch_runtime","current_branch_evidence","release_status",
    "current_work","current_pr","execution_baseline_sha","base_sha",
}

def load(path: Path):
    raw = path.read_text(encoding="utf-8")
    if "\t" in raw:
        raise ValueError("tabs are not allowed in YAML")
    value = yaml.safe_load(raw)
    if not isinstance(value, dict):
        raise ValueError("root must be a mapping")
    return value

def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--matrix", type=Path, default=MATRIX_PATH)
    args = ap.parse_args()
    try:
        matrix = load(args.matrix.resolve())
    except Exception as exc:
        print(f"ERROR: {exc}")
        return 2

    errors = []
    gaps = []
    if matrix.get("schema_version") != "1.0":
        errors.append("schema_version must be 1.0")
    if matrix.get("matrix_id") != "yujian-feature-matrix":
        errors.append("matrix_id mismatch")

    project = matrix.get("project", {})
    if project.get("repository") != "pan277942135/Yujian_App":
        errors.append("project.repository mismatch")
    if project.get("control_branch") != "main":
        errors.append("project.control_branch must be main")
    forbidden_project = DYNAMIC_KEYS & set(project)
    if forbidden_project:
        errors.append(f"project contains live execution keys: {sorted(forbidden_project)}")

    features = matrix.get("features", {})
    if list(features) != FEATURE_IDS:
        errors.append("features must contain exactly the 15 canonical feature IDs in order")
    for fid, item in features.items():
        if not isinstance(item, dict):
            errors.append(f"{fid}: feature entry must be a mapping")
            continue
        forbidden = DYNAMIC_KEYS & set(item)
        if forbidden:
            errors.append(f"{fid}: dynamic keys present: {sorted(forbidden)}")
        implementation = item.get("implementation", {})
        if not isinstance(implementation, dict):
            errors.append(f"{fid}: implementation must be a mapping")
        else:
            if "registered_runtime_status" not in implementation:
                errors.append(f"{fid}: implementation.registered_runtime_status missing")
            if "runtime_authority" not in implementation:
                errors.append(f"{fid}: implementation.runtime_authority missing")
        if "child_surfaces" not in item:
            errors.append(f"{fid}: child_surfaces missing")
        if "journey_refs" not in item:
            errors.append(f"{fid}: journey_refs missing")

    journeys = matrix.get("journeys", {})
    if list(journeys) != JOURNEYS:
        errors.append("journeys must contain J01-J20 in order")
    for jid, item in journeys.items():
        if not isinstance(item, dict) or not item.get("name"):
            errors.append(f"{jid}: journey name missing")
        if isinstance(item, dict):
            for fid in item.get("feature_refs", []) or []:
                if fid not in features:
                    errors.append(f"{jid}: unknown feature {fid}")

    routes = matrix.get("routes", {})
    if not isinstance(routes, dict) or not routes:
        errors.append("routes must be non-empty")
    else:
        for rid, item in routes.items():
            if isinstance(item, dict):
                for fid in item.get("owner_features", []) or []:
                    if fid not in features:
                        errors.append(f"{rid}: unknown owner feature {fid}")

    source = matrix.get("journey_source")
    if isinstance(source, str) and not (ROOT / source).exists():
        gaps.append(f"canonical journey source not present in this checkout: {source}")

    if DYNAMIC_KEYS & set(matrix):
        errors.append("top-level dynamic keys are forbidden")

    print("FEATURE_MATRIX_VALIDATION=" + ("FAIL" if errors else "PASS"))
    print(f"feature_count={len(features)}")
    print(f"journey_count={len(journeys)}")
    print(f"route_count={len(routes)}")
    print(f"open_gaps={len(gaps)}")
    for error in errors:
        print("- " + error, file=__import__("sys").stderr)
    for gap in gaps:
        print("OPEN_GAP: " + gap)
    return 1 if errors else 0

if __name__ == "__main__":
    raise SystemExit(main())
