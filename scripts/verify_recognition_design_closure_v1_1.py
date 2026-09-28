#!/usr/bin/env python3
"""Validate Recognition Design Closure V1.1 and frozen processing contract."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "design/pages/recognition"
CONTRACT_PATH = BASE / "processing/contracts/Recognition_Processing_Contract_V1_1.json"
MANIFEST_PATH = BASE / "design/reference_manifest.json"

REQUIRED_DOCS = [
    BASE / "Recognition_Design_Closure_V1_1.md",
    BASE / "processing/spec/Recognition_Processing_Visual_Spec_V1_1.md",
    BASE / "processing/motion/Recognition_Processing_Motion_Spec_V1_1.md",
    BASE / "runtime/Recognition_Runtime_Contract_V1_1.md",
    BASE / "evidence/Recognition_Evidence_Contract_V1_1.md",
    BASE / "spec/Recognition_Acceptance_Criteria_V1_1.md",
    BASE / "processing/design/YuJian_Recognition_AI_Ambient_Field_Engineering_Spec_V1.png",
    BASE / "processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md",
]

EXPECTED_TIMING = {
    "CAPTURED": 350,
    "DETECTING": 600,
    "OUTLINE": 600,
    "CLASSIFYING": 1250,
    "nominal_total": 2800,
    "resolve_fade": 200,
    "runtime_total_min": 2500,
    "runtime_total_max": 3500,
    "fish_focus_stable_min": 1000,
}

EXPECTED_SCREENSHOTS = [
    "01_capture_transition.png",
    "02_ai_understanding.png",
    "03_fish_highlight.png",
    "04_fish_identifying.png",
    "05_result_high.png",
    "06_result_medium.png",
    "07_result_low.png",
    "08_error_no_fish.png",
    "09_error_image_quality.png",
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    errors: list[str] = []

    for path in REQUIRED_DOCS + [CONTRACT_PATH, MANIFEST_PATH]:
        if not path.is_file() or path.stat().st_size == 0:
            errors.append(f"missing required closure source: {path.relative_to(ROOT)}")

    if errors:
        raise SystemExit("\n".join(errors))

    contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    if contract.get("version") != "1.1" or contract.get("status") != "FROZEN":
        errors.append("processing contract version/status mismatch")
    if contract.get("timing_ms") != EXPECTED_TIMING:
        errors.append(f"V1.1 timing mismatch: {contract.get('timing_ms')}")
    if len(contract.get("ambient", {}).get("paths", [])) != 7:
        errors.append("ambient field must freeze exactly seven paths")
    path_ids = [p.get("id") for p in contract.get("ambient", {}).get("paths", [])]
    if path_ids != ["B1", "B2", "B3", "B4", "G1", "G2", "G3"]:
        errors.append(f"ambient path order/id mismatch: {path_ids}")
    if contract.get("fish_focus", {}).get("forbidden_phases") != ["CAPTURED", "DETECTING"]:
        errors.append("fish focus must remain forbidden before OUTLINE")
    if contract.get("fish_focus", {}).get("contour", {}).get("classifying_alpha_max") != 0.42:
        errors.append("fish contour alpha authority mismatch")
    if contract.get("evidence", {}).get("screenshots") != EXPECTED_SCREENSHOTS:
        errors.append("runtime screenshot evidence list mismatch")

    refs = manifest.get("references", [])
    if len(refs) != 9:
        errors.append(f"expected 9 Frozen references, found {len(refs)}")
    for ref in refs:
        name = ref.get("file")
        path = BASE / "design" / str(name)
        if not path.is_file():
            errors.append(f"missing Frozen PNG: {name}")
            continue
        if ref.get("status") != "FROZEN":
            errors.append(f"reference not FROZEN: {name}")
        if [ref.get("width"), ref.get("height")] != [941, 1672]:
            errors.append(f"reference dimensions changed: {name}")
        digest = sha256(path)
        if digest != ref.get("sha256"):
            errors.append(f"reference SHA mismatch: {name}")

    legacy = (BASE / "processing/motion/YuJian_Recognition_AI_Ambient_Field_Frozen_Spec_V1.md").read_text(encoding="utf-8")
    if "V1.1 supersession notice" not in legacy or "350/600/600/1250ms" not in legacy:
        errors.append("legacy V1 timing source lacks explicit V1.1 supersession notice")

    if errors:
        print(json.dumps({"status": "FAIL", "errors": errors}, ensure_ascii=False, indent=2))
        raise SystemExit(1)

    print(json.dumps({
        "status": "PASS",
        "design_closure": "Recognition V1.1",
        "frozen_references": len(refs),
        "ambient_paths": len(contract["ambient"]["paths"]),
        "timing_ms": contract["timing_ms"],
        "evidence_files": len(EXPECTED_SCREENSHOTS) + 3,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
