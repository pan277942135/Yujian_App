#!/usr/bin/env python3
"""Validate the active Recognition Presentation V1.3 product-state contract."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TIMELINE = ROOT / "app/src/main/assets/identify/animation/identify_timeline.json"
STATE_MACHINE = ROOT / "app/src/main/assets/identify/state_machine/identify_state_machine.json"
VERSION = "RECOGNITION_PRESENTATION_v1_3"
EXPECTED = [
    (0, "IMAGE_RECOGNIZING", "图片识别中"),
    (900, "FISH_LOCATED", "已定位到鱼体"),
    (1500, "SPECIES_RECOGNIZING", "鱼种识别中"),
    (2750, "RESOLVE", ""),
    (2950, "RESULT", "识别结果"),
]
RAW_PIPELINE = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING", "RESULT", "FAILURE"]


def main() -> None:
    timeline = json.loads(TIMELINE.read_text(encoding="utf-8"))
    state_machine = json.loads(STATE_MACHINE.read_text(encoding="utf-8"))
    assert timeline["contract_version"] == VERSION
    assert state_machine["contract_version"] == VERSION
    actual = [
        (step["time"], step["state"], step["label"])
        for step in timeline["timeline_ms"]
    ]
    assert actual == EXPECTED, actual
    assert timeline["production_pipeline"] == RAW_PIPELINE
    assert state_machine["production_pipeline"] == RAW_PIPELINE

    states = state_machine["states"]
    assert "DETECTING" not in states
    assert states.count("IMAGE_RECOGNIZING") == 1
    assert states.count("FISH_LOCATED") == 1
    assert states.count("SPECIES_RECOGNIZING") == 1
    flow = state_machine["flow"]
    assert flow["IMAGE_RECOGNIZING"] == [
        "FISH_LOCATED", "ISSUE_NO_FISH", "ISSUE_IMAGE_QUALITY", "ISSUE_TECHNICAL_FAILURE"
    ]
    assert flow["SPECIES_RECOGNIZING"] == ["RESOLVE", "ISSUE_TECHNICAL_FAILURE"]
    assert set(flow["RESOLVE"]) == {"RESULT_HIGH", "RESULT_MEDIUM", "RESULT_LOW"}
    print("recognition presentation contract v1.3: OK")


if __name__ == "__main__":
    main()
