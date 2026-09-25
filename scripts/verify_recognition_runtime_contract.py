#!/usr/bin/env python3
"""Validate the packaged recognition timeline and terminal routing contract."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TIMELINE = ROOT / "app/src/main/assets/identify/animation/identify_timeline.json"
STATE_MACHINE = ROOT / "app/src/main/assets/identify/state_machine/identify_state_machine.json"
VERSION = "RECOGNITION_RUNTIME_v1"
EXPECTED = [
    (0, "CAPTURED"),
    (800, "DETECTING"),
    (1500, "OUTLINE"),
    (2300, "CLASSIFYING"),
    (3000, "RESULT"),
]


def main() -> None:
    timeline = json.loads(TIMELINE.read_text(encoding="utf-8"))
    state_machine = json.loads(STATE_MACHINE.read_text(encoding="utf-8"))
    assert timeline["contract_version"] == VERSION
    assert state_machine["contract_version"] == VERSION
    actual = [(step["time"], step["state"]) for step in timeline["timeline_ms"]]
    assert actual == EXPECTED, actual
    flow = state_machine["flow"]
    assert flow["CAPTURED"] == ["DETECTING"]
    assert flow["DETECTING"] == ["OUTLINE", "NO_FISH", "TOO_FAR"]
    assert flow["OUTLINE"] == ["CLASSIFYING"]
    assert set(flow["CLASSIFYING"]) == {"SUCCESS", "CONFIRM", "UNKNOWN"}
    for terminal in ("SUCCESS", "CONFIRM", "UNKNOWN", "NO_FISH", "TOO_FAR"):
        assert flow[terminal] == [], terminal
    print("recognition runtime contract: OK")


if __name__ == "__main__":
    main()
