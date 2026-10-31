#!/usr/bin/env python3
"""Validate the active Web Hi-Fi V2 authorities after retirement of duplicate machine JSONs."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "design/pages/recognition/web/contracts"
EXPECTED_CONTRACT_ID = "YUJIAN_AI_CATCH_RECOGNITION_WEB_HIFI_V1"
EXPECTED_BASELINE_SHA = "27c34ee5d3cf90921df433cb43c671e42b8ffa80b8f5cebfe9b397da2ee853e2"
EXPECTED_PHOTO_SHA = "ab7227cf7205ee2ac342b6cd75cd899747879506c95c33b888b6adf0acb47987"


def check(condition, message):
    if not condition:
        raise AssertionError(message)


def load(path):
    target = ROOT / path
    check(target.is_file(), f"missing active authority: {path}")
    return json.loads(target.read_text(encoding="utf-8"))


def read_text(path):
    target = ROOT / path
    check(target.is_file(), f"missing active document: {path}")
    return target.read_text(encoding="utf-8")


def rect_mid(a, b):
    keys = ("x", "y", "width", "height")
    return {key: (float(a[key]) + float(b[key])) / 2 for key in keys}


def same_rect(a, b):
    return all(abs(float(a[key]) - float(b[key])) < 1e-7 for key in ("x", "y", "width", "height"))


def main():
    names = ("Visual_Tokens", "Acceptance_Matrix", "Asset_Manifest", "Work_B_Implementation_Mapping")
    docs = {name: load(f"design/pages/recognition/web/contracts/{name}_V2.json") for name in names}

    for name, document in docs.items():
        check(document.get("contract_id") == EXPECTED_CONTRACT_ID, f"{name}: contract identity")
        check(document.get("version") == "2.0", f"{name}: contract version")
        check(document.get("authority_status") == "VISUAL_CONTRACT_DEFINED", f"{name}: authority status")
        check(document.get("adoption_status") == "MAIN_ADOPTED_DESIGN_ONLY", f"{name}: adoption status")
        check(document.get("adoption_pr") == 190 and document.get("adopted_source_prs") == [186, 187], f"{name}: adoption provenance")
        check(document.get("adoption_scope") == "DESIGN_CONTRACT_ONLY", f"{name}: adoption scope")
        check(document.get("runtime_visual_verified") is False, f"{name}: runtime must remain unverified")
        check(document.get("pixel_frozen_source") == "FROZEN_PRIVATE_SHA_BOUND", f"{name}: private pixel baseline status")
        check(document.get("pixel_baseline_id") == "YUJIAN_SWOS_WEB_PIXEL_BASELINE_V1", f"{name}: pixel baseline ID")
        check(document.get("pixel_baseline_contract") == "design/pages/recognition/web/VISUAL_BASELINE_FREEZE_V1.md", f"{name}: pixel baseline contract")
        check(document.get("page_id") == "ai_catch_recognition_web", f"{name}: page ID")
        check(bool(document.get("acceptance_criteria")), f"{name}: acceptance criteria")

    visual = docs["Visual_Tokens"]
    tokens = visual.get("tokens", {})
    colors = tokens.get("colors", [])
    typography = tokens.get("typography", [])
    check(len(colors) >= 10 and len(typography) >= 10, "visual token catalog is incomplete")
    check(len({item.get("id") for item in colors}) == len(colors), "duplicate color token IDs")
    check(len({item.get("id") for item in typography}) == len(typography), "duplicate typography token IDs")
    check(tokens.get("space_px") == [4, 8, 12, 16, 20, 24, 32, 40], "spacing token scale drift")

    acceptance = docs["Acceptance_Matrix"]
    checks = acceptance.get("checks", [])
    check([item.get("id") for item in checks] == [f"A{i:02d}" for i in range(1, 19)], "acceptance matrix must retain A01–A18")
    check(acceptance.get("swos_web_motion_technical_contract_ref") == "design/pages/recognition/web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md", "SWOS Markdown authority reference")
    check(acceptance.get("swos_web_motion_machine_contract_ref") in (None, ""), "retired duplicate SWOS Web JSON must not remain an active authority")

    mapping_doc = docs["Work_B_Implementation_Mapping"]
    check(bool(mapping_doc.get("tasks")) and bool(mapping_doc.get("do_not")), "Work B mapping must retain tasks and non-goals")
    check(mapping_doc.get("swos_web_hifi_motion_contract") == "design/pages/recognition/web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md", "Work B SWOS Markdown authority reference")
    check(mapping_doc.get("swos_web_hifi_motion_machine_contract") in (None, ""), "Work B must not reference a retired duplicate SWOS Web JSON")

    contract = read_text("design/pages/recognition/web/WEB_HIFI_CONTRACT_V2.md")
    handoff_a = read_text("design/pages/recognition/web/WEB_HIFI_HANDOFF_A_V2.md")
    handoff_b = read_text("design/pages/recognition/web/WEB_HIFI_HANDOFF_B_V2.md")
    gap = read_text("design/pages/recognition/web/WEB_HIFI_GAP_MATRIX_V2.md")
    combined = "\n".join((contract, handoff_a, handoff_b, gap))
    check("三阶段结构" in contract and "五节点" in contract, "three-stage/five-node page structure")
    check("3+2" in contract, "3+2 result-state structure")
    for state in ("HIGH", "MEDIUM", "LOW", "NO_FISH", "IMAGE_QUALITY", "TECHNICAL_FAILURE"):
        check(state in contract, f"missing result state: {state}")
    for width in ("1440×1000", "1280×800", "1024×768", "768×1024", "480dp", "430×932", "390×844", "360×640", "320×480"):
        check(width in contract, f"responsive viewport missing: {width}")
    for timing in ("3650ms", "2800ms", "850ms", "R0.5"):
        check(timing in combined, f"SWOS timing reference missing: {timing}")
    check("HIGH" in contract and "RR09" in contract, "HIGH-only RR09 result reveal contract")
    check("0.45" in contract and "0.12" in contract and "0.55" in contract, "existing confidence threshold decision must remain documented")

    frame_path = "design/pages/recognition/processing/optics/SWOS_15_Frame_Keyframe_Contract_V1_1.json"
    transition_path = "design/pages/recognition/continuity/SWOS_Shared_Photo_Transition_Contract_V1.json"
    other_authorities = (
        "design/pages/recognition/processing/optics/SWOS_Optical_Contract_V1.json",
        "design/pages/recognition/result/motion/recognition_result_motion_contract_v3.json",
        "design/pages/recognition/result/media/hero_media_contract.json",
    )
    frames = load(frame_path)
    transition = load(transition_path)
    for authority in other_authorities:
        load(authority)

    keyframes = frames.get("keyframes", [])
    processing = [frame for frame in keyframes if frame.get("track") == "PROCESSING"]
    rr09 = [frame for frame in keyframes if frame.get("track") == "RR09"]
    check(frames.get("frame_count") == 15 and len(keyframes) == 15, "15-frame SWOS contract")
    check([frame.get("id") for frame in processing] == [f"P{i}" for i in range(8)], "Processing P0–P7 frame IDs")
    check([frame.get("id") for frame in rr09] == ["R0", "R0.5", "R1", "R2", "R3", "R4", "R5"], "RR09 R0–R5 frame IDs")
    check((frames.get("processing", {}).get("duration_ms"), frames.get("rr09", {}).get("duration_ms"), frames.get("total_nominal_duration_ms")) == (2800, 850, 3650), "2800+850=3650ms timing")
    check([frame.get("relative_ms") for frame in processing] == [0, 350, 950, 1200, 1550, 2050, 2600, 2800], "Processing frame timing")
    check([frame.get("relative_ms") for frame in rr09] == [0, 100, 200, 380, 550, 680, 850], "RR09 relative frame timing")
    check([frame.get("global_nominal_ms") for frame in rr09] == [2800, 2900, 3000, 3180, 3350, 3480, 3650], "RR09 global frame timing")
    check([frame.get("ambient_water_strength") for frame in processing] == [0, 10, 45, 75, 60, 35, 10, 0], "ambient water intensity curve")
    check([frame.get("fish_gold_strength") for frame in processing] == [0, 0, 15, 45, 100, 100, 100, 100], "fish gold intensity curve")
    check(rr09[1].get("relative_ms") == 100 and rr09[1].get("global_nominal_ms") == 2900, "R0.5 timing")

    samples = transition.get("samples", {})
    expected_foreground_mid = rect_mid(transition["processing_photo_content_rect"], transition["result_hero_photo_content_rect"])
    expected_outer_mid = rect_mid(samples["R0"]["outer_rect"], samples["R1"]["outer_rect"])
    check(same_rect(samples["R0.5"]["foreground_rect"], expected_foreground_mid), "R0.5 shared-photo foreground midpoint")
    check(same_rect(samples["R0.5"]["outer_rect"], expected_outer_mid), "R0.5 container midpoint")
    check(transition.get("sample_source", {}).get("source_photo_sha256") == EXPECTED_PHOTO_SHA, "shared source photo SHA")
    check(transition.get("golden_trace_state", {}).get("transform") == "same foreground transform used by the photo", "gold trace must share the photo transform")
    check(transition.get("fish_geometry", {}).get("production_mask") is False, "manual fish geometry is not a production Mask")
    check(transition.get("fish_geometry", {}).get("trust_state") == "MANUAL_REFERENCE_ONLY_NOT_PRODUCTION_MASK", "fish geometry trust state")
    check(transition.get("transform", {}).get("stretch") is False and transition.get("transform", {}).get("generated_fill") is False and transition.get("transform", {}).get("photo_swap") is False, "forbidden photo transform behavior")

    assets = docs["Asset_Manifest"]
    approved = assets.get("approved_web_hifi", {})
    check(approved.get("sha256") == EXPECTED_BASELINE_SHA, "frozen Web reference SHA")
    check(approved.get("public_permission") == "NOT_CONFIRMED", "private Web reference publication permission")
    check(approved.get("status") == "USER_CONFIRMED_VISUAL_REFERENCE", "Web visual reference status")
    check(approved.get("pixel_authority_status") == "FROZEN_PRIVATE_SHA_BOUND", "private pixel authority status")
    baseline = load("design/pages/recognition/web/VISUAL_REFERENCE_MANIFEST_V1.json").get("pixel_baseline", {})
    check(baseline.get("baseline_id") == "YUJIAN_SWOS_WEB_PIXEL_BASELINE_V1" and baseline.get("status") == "FROZEN_PRIVATE_SHA_BOUND", "private baseline identity/status")
    check(baseline.get("sha256") == EXPECTED_BASELINE_SHA and baseline.get("source_bytes_reverified_this_execution") is False, "private baseline SHA/verification boundary")

    registry = load("design/registry/experience_registry_v1.json")
    feature = next(item for item in registry["features"] if item.get("id") == "recognition_flow_v1_2")
    web_view = next(item for item in feature["hifi_views"] if item.get("id") == "web_hifi_contract_v2")
    check(web_view.get("authority") == "design/pages/recognition/web/WEB_HIFI_CONTRACT_V2.md", "Design Manager Web V2 Markdown authority")
    check(web_view.get("pixel_frozen_source") == "FROZEN_PRIVATE_SHA_BOUND" and web_view.get("pixel_baseline_id") == "YUJIAN_SWOS_WEB_PIXEL_BASELINE_V1", "Design Manager private baseline binding")
    for link in web_view.get("contract_links", []):
        check((ROOT / link["path"]).is_file(), f"missing Design Manager Web V2 resource: {link['path']}")
    swos_view = next(item for item in feature["hifi_views"] if item.get("id") == "swos_web_hifi_motion_contract_v1_0")
    check(swos_view.get("authority") == "design/pages/recognition/web/SWOS_WEB_HIFI_MOTION_TECHNICAL_CONTRACT_V1_0.md", "Design Manager SWOS Markdown authority")
    check(swos_view.get("machine_authority") in (None, ""), "Design Manager must not advertise a retired duplicate SWOS Web JSON")
    for link in swos_view.get("contract_links", []):
        check((ROOT / link["path"]).is_file(), f"missing Design Manager SWOS resource: {link['path']}")

    for path in (
        "design/pages/recognition/web/WEB_HIFI_HANDOFF_A_V2.md",
        "design/pages/recognition/web/WEB_HIFI_HANDOFF_B_V2.md",
        "design/pages/recognition/web/WEB_HIFI_GAP_MATRIX_V2.md",
        "design/pages/recognition/web/VISUAL_BASELINE_FREEZE_V1.md",
    ):
        check((ROOT / path).is_file(), f"missing active handoff/baseline document: {path}")

    print("WEB_HIFI_CONTRACT_V2=PASS active_contract_files=4 stages=3 steps=5 states=6 frames=15 duration_ms=3650 responsive_viewports=9")
    print("PIXEL_FROZEN_SOURCE=FROZEN_PRIVATE_SHA_BOUND MAIN_ADOPTED=YES_SCOPE=DESIGN_CONTRACT_ONLY RUNTIME_VISUAL_VERIFIED=NO")


if __name__ == "__main__":
    main()
