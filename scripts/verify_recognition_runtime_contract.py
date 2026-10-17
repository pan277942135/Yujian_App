#!/usr/bin/env python3
"""Validate Android's Recognition Processing V3.1 contract projection."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AUTHORITY = ROOT / "design/system_v3/08_Page_Contracts/recognition/processing/State_Machine_V3_1.json"
TIMELINE = ROOT / "app/src/main/assets/identify/animation/identify_timeline.json"
STATE_MACHINE = ROOT / "app/src/main/assets/identify/state_machine/identify_state_machine.json"
VERSION = "recognition_processing_state_machine_v3_1"
EXPECTED_COPY = {
    "CAPTURED": ("正在准备识别", "AI 已获取这张照片"),
    "DETECTING": ("正在理解这张照片", "寻找这次鱼获的线索"),
    "OUTLINE": ("已定位到鱼体", "正在分析这次鱼获"),
    "CLASSIFYING": ("正在认识这条鱼", "分析鱼体特征"),
    "RESULT_READY": ("", ""),
}
PRODUCTION_PIPELINE = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING", "RESULT", "FAILURE"]
EXIT_STATES = ["NO_FISH", "IMAGE_QUALITY_WARNING", "TECHNICAL_FAILURE"]


def duration_ms(text: str) -> int:
    match = re.search(r"(?:>=\s*)?(\d+)\s*ms", text)
    if not match:
        raise AssertionError(f"state duration is not parseable: {text!r}")
    return int(match.group(1))


def main() -> None:
    authority = json.loads(AUTHORITY.read_text(encoding="utf-8"))
    timeline = json.loads(TIMELINE.read_text(encoding="utf-8"))
    state_machine = json.loads(STATE_MACHINE.read_text(encoding="utf-8"))

    authority_states = authority["states"]
    expected_ids = ["CAPTURED", "DETECTING", "OUTLINE", "CLASSIFYING", "RESULT_READY"]
    assert [row["id"] for row in authority_states] == expected_ids
    expected_rows = [
        {
            "state": row["id"],
            "minimum_duration_ms": duration_ms(row["duration"]),
            "title": EXPECTED_COPY[row["id"]][0],
            "subtitle": EXPECTED_COPY[row["id"]][1],
        }
        for row in authority_states
    ]
    assert [row["minimum_duration_ms"] for row in expected_rows] == [220, 320, 350, 250, 0]

    for actual in (timeline, state_machine):
        assert actual["contract_version"] == VERSION
    assert timeline["presentation_sequence"] == expected_ids
    assert timeline["states"] == expected_rows
    assert timeline["production_pipeline"] == PRODUCTION_PIPELINE

    expected_flows = {
        "CAPTURED": ["DETECTING", "TECHNICAL_FAILURE"],
        "DETECTING": ["OUTLINE", "NO_FISH", "IMAGE_QUALITY_WARNING", "TECHNICAL_FAILURE"],
        "OUTLINE": ["CLASSIFYING", "TECHNICAL_FAILURE"],
        "CLASSIFYING": ["RESULT_READY", "TECHNICAL_FAILURE"],
        "RESULT_READY": [],
        "NO_FISH": [],
        "IMAGE_QUALITY_WARNING": [],
        "TECHNICAL_FAILURE": [],
    }
    assert state_machine["states"] == expected_ids
    assert state_machine["production_pipeline"] == PRODUCTION_PIPELINE
    assert state_machine["minimum_duration_ms"] == {
        row["state"]: row["minimum_duration_ms"] for row in expected_rows
    }
    assert state_machine["flow"] == expected_flows
    assert "Immediate handoff" in state_machine["result_ready_rule"]
    assert "real pipeline event" in state_machine["timing_rule"]
    assert all(state not in state_machine["states"] for state in ["IMAGE_RECOGNIZING", "RESOLVE", *EXIT_STATES])
    print("recognition processing contract v3.1: OK")


if __name__ == "__main__":
    main()
