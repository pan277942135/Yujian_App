#!/usr/bin/env python3
"""SWOS/RR09 V1.2 optical authority, candidate-calibration and runtime truth gate."""
from __future__ import annotations
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OPTICS = ROOT / "design/pages/recognition/processing/optics"
NAME = "SWOS_RR09_Optical_Parameters_V1_2.json"
ACCEPT = "SWOS_RR09_Visual_Acceptance_V1_2.json"
CONTRACT = "SWOS_RR09_Optical_Rendering_Acceptance_Contract_V1_2.md"
EXPECTED = ["P0","P1","P2","P3","P4","P5","P6","P7","R0","R0.5","R1","R2","R3","R4","R5"]

def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))

def main() -> None:
    v = load_json(OPTICS / NAME)
    a = load_json(OPTICS / ACCEPT)
    original = load_json(OPTICS / "SWOS_15_Frame_Keyframe_Contract_V1_1.json")
    transition = load_json(ROOT / "design/pages/recognition/continuity/SWOS_Shared_Photo_Transition_Contract_V1.json")
    assert v["contract_id"] == "YUJIAN-SWOS-RR09-OPTICAL-RENDERING-ACCEPTANCE-V1_2"
    assert v["contract_authority_status"] == "FROZEN_CONTRACT_INPUT"
    assert v["numeric_calibration_status"] == "CALIBRATION_CANDIDATE_NOT_VISUALLY_APPROVED"
    assert v["runtime_visual_status"] == "NOT_VERIFIED"
    assert v["production_mask_status"] == "NOT_QUALIFIED"
    assert a["normative_status"] == "FROZEN_MEASUREMENT_RULES"
    assert a["outcome_status"] == "NOT_RUN"
    assert a["parameters_status"] == "CALIBRATION_CANDIDATE"
    assert v["frozen"]["total_nominal_ms"] == 3650
    assert v["frozen"]["processing_duration_ms"] == 2800
    assert v["frozen"]["rr09_duration_ms"] == 850
    frozen = v["frozen"]["keyframes"] + v["frozen"]["reveal"]
    assert [f["id"] for f in frozen] == EXPECTED
    assert [f["frame_id"] for f in a["expected_samples"]] == EXPECTED
    assert [f["id"] for f in original["keyframes"]] == EXPECTED
    for index, item in enumerate(frozen):
        original_item = original["keyframes"][index]
        assert item["global_ms"] == original_item["global_nominal_ms"], (item["id"], "timestamp mismatch")
        for key in ("ambient_water_strength","fish_gold_strength"):
            if key in item:
                assert item[key] == original_item[key], (item["id"], key)
    assert original["total_nominal_duration_ms"] == 3650
    assert v["frozen"]["keyframes"][-1]["ambient_water_strength"] == 0
    assert v["frozen"]["keyframes"][-1]["fish_gold_strength"] == 100
    assert all(f["fish_gold_strength"] == 100 for f in v["frozen"]["keyframes"][4:])
    mid = next(f for f in v["frozen"]["reveal"] if f["id"] == "R0.5")
    assert (mid["relative_ms"],mid["global_ms"]) == (100,2900)
    assert transition["samples"]["R0.5"]["global_nominal_ms"] == 2900
    assert list(transition["samples"]["R0.5"]["outer_rect"].values()) == a["geometry"]["r0_5_reference"]["outer_rect"]
    assert list(transition["samples"]["R0.5"]["foreground_rect"].values()) == a["geometry"]["r0_5_reference"]["foreground_rect"]
    assert v["frozen"]["fish_layers_use_same_trusted_mask"]
    assert v["frozen"]["same_source_photo_sha_and_single_transform"]
    assert v["calibration_candidate"]["ambient_exit"]["end_state"]["ambient_strength"] == 0
    assert v["calibration_candidate"]["ambient_exit"]["end_state"]["fish_gold_strength"] == 100
    assert v["calibration_candidate"]["rr09"]["r5_particle_count"] == 0
    for key, value in v["calibration_candidate"]["palette"].items():
        assert isinstance(value,str) and len(value)==7 and value[0]=="#", key
        assert all(c in "0123456789ABCDEFabcdef" for c in value[1:]), key
    assert a["priority_frames"] == ["P3","P4","P7","R0.5","R2","R4"]
    assert len(a["visual_checks"]) == 6
    assert all(row["runtime_status"] == "NOT_RUN" for row in a["expected_samples"])
    assert all(x["status"] == "NOT_RUN" for x in a["visual_checks"])
    md = (OPTICS / CONTRACT).read_text(encoding="utf-8")
    assert "CALIBRATION_CANDIDATE" in md and "MUST_MEASURE_RUNTIME" in md
    registry = load_json(ROOT/"design/registry/experience_registry_v1.json")
    feature = next(x for x in registry["features"] if x["id"] == "recognition_flow_v1_2")
    view = next(x for x in feature["hifi_views"] if x["id"] == "swos_rr09_optical_v1_2")
    assert view["render_mode"] == "swos_optical_parameters_v1_2"
    assert view["contract_freeze_status"] == "FROZEN_CONTRACT_INPUT"
    assert view["numeric_calibration_status"] == v["numeric_calibration_status"]
    assert view["runtime_visual_status"] == "NOT_VERIFIED"
    assert (ROOT / view["authority"]).is_file()
    assert (ROOT / view["machine_authority"]).is_file()
    assert (ROOT / view["secondary_authority"]).is_file()
    assert "swos_rr09_optical_v1_2" in (ROOT/"harness/FEATURE_MATRIX.yaml").read_text(encoding="utf-8")
    assert "SWOS-RR09-OPTICAL-V1_2" in (ROOT/"harness/UNIFIED_CONTRACT_REGISTRY.yaml").read_text(encoding="utf-8")
    assert "swos_optical_v1_2.js" in (ROOT/"design/manager/index.html").read_text(encoding="utf-8")
    assert "SWOSOpticalV12" in (ROOT/"design/manager/app.js").read_text(encoding="utf-8")
    print("SWOS_RR09_OPTICAL_V1_2=PASS (authority frozen / numeric candidate / runtime NOT_RUN)")

if __name__ == "__main__":
    main()
