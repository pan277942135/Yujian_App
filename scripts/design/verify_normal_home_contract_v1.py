#!/usr/bin/env python3
"""Static Normal Home spec/manifest governance gate.

Usage:
    python3 scripts/design/verify_normal_home_contract_v1.py

Does NOT substitute for Android instrumentation or native screenshot/motion evidence.
No network or third-party Python dependency is required.
"""
from __future__ import annotations

import hashlib
import json
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PAGE = ROOT / "design/pages/home/normal_home"
ENG = PAGE / "engineering"
FAILED: list[str] = []
PASSED: list[str] = []


def require(ok: bool, message: str) -> None:
    (PASSED if ok else FAILED).append(message)


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (ValueError, OSError) as exc:
        require(False, f"JSON error {path.relative_to(ROOT)}: {exc}")
        return {}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        while True:
            chunk = fh.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def verify_png(path: Path, expected_hash: str, dimensions: tuple[int, int]) -> None:
    if not path.is_file():
        require(False, f"Missing Frozen PNG {path.relative_to(ROOT)}")
        return
    with path.open("rb") as fh:
        head = fh.read(24)
    signature_ok = head[:8] == b"\x89PNG\r\n\x1a\n"
    dims = struct.unpack(">II", head[16:24]) if signature_ok and len(head) >= 24 else (-1, -1)
    require(signature_ok and dims == dimensions, f"PNG dimensions {path.relative_to(ROOT)} = {dims}")
    require(sha256(path) == expected_hash, f"PNG SHA256 {path.relative_to(ROOT)}")


def main() -> int:
    authority = read_json(PAGE / "authority/authority_map.json")
    status = read_json(PAGE / "status.json")
    matrix = read_json(ENG / "normal_home_acceptance_matrix_v1.json")

    expected_files = [
        "README.md",
        "Layout_Responsive_Contract_V1.md",
        "State_Interaction_Contract_V1.md",
        "Motion_Feedback_Contract_V1.md",
        "Visual_Runtime_Acceptance_V1.md",
        "Archive_And_Supersession_V1.md",
        "normal_home_acceptance_matrix_v1.json",
        "normal_home_visual_pixel_targets_v1.json",
        "hero_real_photo_fixture_register_v1.json",
        "Visual_Anchor_Asset_Resolution_V1.md",
        "Short_Window_Adaptive_Freeze_V1.md",
        "Hero_Real_Photo_EvidenceFit_Contract_V1.md",
        "Quantitative_Visual_Acceptance_Contract_V1.md",
    ]
    for name in expected_files:
        require((ENG / name).is_file(), f"Engineering file exists: {name}")
    require(authority.get("engineering_binding", {}).get("entry")
            == "design/pages/home/normal_home/engineering/README.md", "authority index points to active entry")
    require(status.get("engineering_contract_status") == "ACTIVE", "engineering status ACTIVE")
    require(status.get("runtime_pass_is_independent_from_design_freeze") is True,
            "design freeze not equivalent to runtime PASS")
    precedence = authority.get("precedence") or []
    priorities = [p.get("priority") for p in precedence]
    require(priorities == list(range(1, len(priorities) + 1)), "authority priority contiguous and unique")
    require(any(p.get("authority") == "design/pages/home/normal_home/engineering/README.md"
                for p in precedence), "authority precedence includes engineering entry")
    for key, path in (authority.get("page_state_authorities") or {}).items():
        require((ROOT / path).is_file(), f"NH authority exists {key}: {path}")

    cases = matrix.get("cases") or []
    ids = [c.get("id") for c in cases]
    expected_ids = (
        [f"G{i:02d}" for i in range(1, 10)]
        + [f"V{i:02d}" for i in range(1, 7)]
        + [f"S{i:02d}" for i in range(1, 16)]
        + [f"M{i:02d}" for i in range(1, 9)]
        + [f"F{i:02d}" for i in range(1, 9)]
    )
    require(len(cases) == 46 and sorted(ids) == sorted(expected_ids),
            "46 unique required G/V/S/M/F acceptance cases")
    valid = set(matrix.get("valid_status") or [])
    require(valid == {"PASS", "FAIL", "BLOCKED_INFRA", "NOT_RUN", "REVIEW_REQUIRED"},
            "acceptance status taxonomy exact")
    require(all(c.get("acceptance_status") in valid and "assertion" in c
                and "method" in c and "implementation_coverage" in c for c in cases),
            "all matrix cases have runnable method/assertion/coverage and valid status")
    require(all(c.get("acceptance_status") == "NOT_RUN" and not c.get("evidence_refs")
                for c in cases), "no unsupported PASS or fabricated evidence in baseline checklist")
    pixels = read_json(ENG / "normal_home_visual_pixel_targets_v1.json")
    require(pixels.get("source_sha256")==status.get("source_sha256"), "measured pixel targets bound to Frozen SHA")
    masks={item.get("id"):item.get("bbox") for item in pixels.get("visible_masks",[])}
    require(masks.get("CTA_NEAR_WHITE")==[433,1527,646,1558], "measured CTA glyph bbox verified")
    require(masks.get("CAMERA_CORE_NEAR_WHITE")==[450,1590,628,1773], "measured camera core bbox verified")
    require(masks.get("CAMERA_GOLD")==[439,1593,638,1789], "measured camera gold bbox verified")
    avatar=read_json(PAGE / "assets/normal_home_default_avatar_v2_manifest.json")
    avmap=avatar.get("display_states") or {}
    require(avmap.get("logged_in_without_avatar")=="normal_home_default_avatar_v2" 
            and avmap.get("guest")=="normal_home_runtime_v1/avatar/guest_avatar.png", 
            "Home approved V2 signed-in avatar and distinct Guest resource identity")
    bg=read_json(ROOT / "app/src/main/assets/normal_home_runtime_v1/config/runtime_manifest.json")
    bgsrc=bg.get("background_provenance") or {}
    require(bgsrc.get("source_sha256")==bgsrc.get("runtime_sha256")==status.get("background_source_sha256"),
            "background runtime bitmap remains byte-equivalent with no extra processing")
    fixtures = read_json(ENG / "hero_real_photo_fixture_register_v1.json")
    entries=fixtures.get("fixtures") or []
    require(len(entries)==8 and sorted(item.get("id") for item in entries)==[f"F{i:02d}" for i in range(1,9)],
            "8 independent evidence slots identified")
    require(all(item.get("status")=="NOT_RUN" and item.get("photo_provenance")=="NOT_VERIFIED"
                and item.get("source_usage_rights")=="NOT_VERIFIED" for item in entries),
            "candidate photos never misreported as verified independent sources")
    for item in entries:
        path=item.get("source_path")
        if path:
            f=ROOT/path
            require(f.is_file() and sha256(f)==item.get("source_sha256"),
                    f"photo candidate hash {item.get('id')}: {path}")
    require(matrix.get("baseline_source_sha256") == status.get("source_sha256"),
            "machine matrix and status Frozen source SHA agree")

    png = ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png"
    verify_png(png, "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377",
               (1080, 1920))
    background = ROOT / "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png"
    verify_png(background, "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7",
               (941, 1672))

    entry = (ENG / "README.md").read_text(encoding="utf-8")
    require("A1 APPROVED" in entry and "A2 APPROVED" in entry, "A1/A2 approved policy retained")
    geo = (ENG / "Layout_Responsive_Contract_V1.md").read_text(encoding="utf-8")
    require("yWindow = yFrozen*S+delta" in geo and "SAFE_OVERFLOW" in geo
            and "NO +T" in geo, "single Y origin and safe compact fallback recorded")
    final_visual=(ENG / "Visual_Anchor_Asset_Resolution_V1.md").read_text(encoding="utf-8")
    require("1527..1558" in final_visual and "1590..1789" in final_visual,
            "verified frozen source CTA Camera optical ROI evidence documented")
    quant=(ENG / "Quantitative_Visual_Acceptance_Contract_V1.md").read_text(encoding="utf-8")
    require("max(2px,0.002*Wsafe)" in quant and "max(3px,0.003*Wsafe)" in quant,
            "numeric geometry and glyph tolerance contract registered")
    short=(ENG / "Short_Window_Adaptive_Freeze_V1.md").read_text(encoding="utf-8")
    require("320dp" in short and "480dp" in short and "160dp" in short,
            "short-screen certified profile, pinned dock and reachable viewport frozen")
    motion = (ENG / "Motion_Feedback_Contract_V1.md").read_text(encoding="utf-8")
    require("6000ms" in motion and "16s" in motion and "FROZEN NONE" in motion,
            "motion, capture evidence, and no-audio policy recorded")
    for rel in [
        "05_responsive_interaction/Normal_Home_Layout_Geometry_Contract_V2_DRAFT.md",
        "05_responsive_interaction/Normal_Home_V2_Responsive_Examples.md",
        "05_responsive_interaction/Normal_Home_V2_Authority_Conflict_Matrix.md",
        "RUNTIME_CLOSURE_V1.md",
        "DESIGN_PACKAGE_CLOSURE_V1.md",
    ]:
        fp = PAGE / rel
        require(fp.is_file() and fp.read_text(encoding="utf-8").startswith(
            "> **SUPERSESSION NOTICE"), f"legacy document marked historical: {rel}")

    for line in PASSED:
        print("PASS", line)
    for line in FAILED:
        print("FAIL", line, file=sys.stderr)
    print(f"NORMAL_HOME_SPEC_GATE passed={len(PASSED)} failed={len(FAILED)}")
    return 1 if FAILED else 0


if __name__ == "__main__":
    raise SystemExit(main())
