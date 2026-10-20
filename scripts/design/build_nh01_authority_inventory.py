#!/usr/bin/env python3
"""Write the NH01 source and design-contract inventory from the validated bundle."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "design/manager/nh01_acceptance/data"
OUT = ROOT / "design/cloud_hifi/NH01"


def load(name: str) -> dict:
    return json.loads((DATA / name).read_text(encoding="utf-8"))


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    source = load("NH01_Source_Manifest_V1.json")
    mapping = json.loads((DATA / "source/M1_NH01_FIGMA_NODE_MAPPING_V2.json").read_text(encoding="utf-8"))
    structure = load("NH01_Structure_V1.json")
    tree = load("NH01_Component_Tree_V1.json")
    bindings = load("NH01_Data_Bindings_V1.json")
    assets = load("NH01_Assets_V1.json")
    OUT.mkdir(parents=True, exist_ok=True)
    write_json(OUT / "source_manifest.json", source)
    write_json(OUT / "existing_structure_inventory.json", tree)

    asset_by_id = {item["id"]: item for item in assets["assets"]}
    source_rows = [
        ("Frozen NH01", "frozen_full_reference", "Only visual authority; used as a separate read-only reference."),
        ("Morning Lake background", "morning_lake_background", "Source bytes verified; historical Figma fill remains blocked."),
        ("Brand title artwork", "brand_title", "Source bytes verified; Figma IMAGE Fill readback recorded; export not run."),
        ("Camera artwork", "camera_artwork", "Source bytes verified; Android runtime geometry remains not run."),
        ("Hero photo fixture", "hero_photo_fixture", "Usage rights and provenance not verified; no image displayed in the structured preview."),
        ("Avatar candidate", "avatar_default_nh07", "Authority conflict remains open; candidate is not rendered as approved."),
    ]
    lines = [
        "# NH01 Source Authority Report",
        "",
        f"Canonical main at handoff: {source['source']['canonical_main_sha_at_handoff']}.",
        f"PR #164 mapping snapshot: {source['pr164']['source_head_sha']} / SHA-256 {source['pr164']['mapping_sha256']}.",
        "",
        "| Source | GitHub path | SHA-256 | Source status | Figma / usage status |",
        "|---|---|---|---|---|",
    ]
    for label, asset_id, note in source_rows:
        item = asset_by_id[asset_id]
        lines.append(
            f"| {label} | {item['repo_path']} | {item['sha256']} | {item['status']} | {note} |"
        )
    lines.extend([
        "",
        "The Frozen hash verifies checked-in source bytes only. It does not prove Figma import, photo rights, or Android parity.",
        "",
        f"Importer report and ZIP are reused from PR #164 at {source['pr164']['source_head_sha']}; neither was rebuilt.",
    ])
    (OUT / "source_authority_report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    lines = [
        "# NH01 Design Contract Inventory",
        "",
        "Contract sources and SHA values are generated from the repository. Runtime status is kept separate from contract status.",
        "",
        "| Contract | SHA-256 | Role |",
        "|---|---|---|",
    ]
    for item in source["source_contracts"]:
        lines.append(f"| {item['repo_path']} | {item['sha256']} | {item['role']} |")
    lines.extend([
        "",
        "## Geometry anchors (Frozen pixel space)",
        "",
        "| Component | Geometry | Evidence class |",
        "|---|---|---|",
        "| Header | 88, 104, 904 × 104 | Contract anchor |",
        "| Statistics | 150, 304, 780 × 116 | Contract anchor |",
        "| Recent Catch Header | 108, 494, 864 × 70 | Contract anchor |",
        "| Selected Hero | 170, 596, 740 × 880; radius 32 | Frozen frame contract |",
        "| CTA container | 330, 1495, 420 × 58 | Compose container fact; glyph is a separate ROI |",
        "| Camera artwork | 435.5, 1583, 208 × 208 | Existing Compose mapping; conflicts with touch frame at y=1564 |",
        "| Camera touch target | 430.5, 1564, 216 × 216 | Responsive contract; runtime bounds NOT_RUN |",
        "",
        "These anchors are not API28 runtime measurements.",
    ])
    (OUT / "design_contract_inventory.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    rows = {item["figma_node"]: item for item in bindings["bindings"]}
    lines = [
        "# NH01 Structure Report",
        "",
        f"PR #164 mapping snapshot: {structure['source']['pr164_head_sha']} / SHA-256 {structure['source']['mapping_sha256']}.",
        f"Mapped top-level and component rows: {len(structure['components'])}.",
        f"Compose source symbols resolved: {sum(item['status'] == 'PASS' for item in rows.values())}/{len(rows)}.",
        "",
        "| Component ID | Figma node | Compose source | Geometry evidence | Mapping status |",
        "|---|---|---|---|---|",
    ]
    for item in structure["components"]:
        binding = rows.get(item["figma_node_id"])
        source_status = binding["status"] if binding else "NOT_MAPPED"
        geometry = item["rect"].get("evidence", "Source contract")
        lines.append(f"| {item['id']} | {item['figma_node_id']} | {item['compose']} | {geometry} | {source_status} |")
    lines.extend([
        "",
        "Figma mapping is a preserved PR #164 snapshot, not live re-readback. Runtime bounds, photo rights, current Figma export, and visual parity remain separate gates.",
    ])
    (OUT / "NH01_Structure_Report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    gate_status = load("NH01_Evidence_Provenance_V1.json")["gate_status"]
    lines = [
        "# NH01 Cloud High-Fidelity Final Report",
        "",
        "- Branch: design/nh01-cloud-structured-parity-v1",
        "- Draft PR: https://github.com/pan277942135/Yujian_App/pulls",
        f"- Frozen SHA-256: {source['frozen_authority']['sha256']}",
        f"- PR #164 mapping source SHA: {source['pr164']['source_head_sha']}",
        f"- Mapping SHA-256: {source['pr164']['mapping_sha256']}",
        f"- Structured component rows: {len(structure['components'])}",
        f"- Compose source symbols resolved: {sum(item['status'] == 'PASS' for item in rows.values())}/{len(rows)}",
        f"- Unresolved PR #164 conflicts: {len(mapping.get('conflicts', []))}",
        "- Android APK SHA-256: NOT_RUN",
        "- Browser Viewer: NOT_RUN until exact-source Chromium workflow evidence",
        "- API28 Runtime: NOT_RUN",
        "- Pixel Diff / Responsive: NOT_RUN",
        "- Viewer URL: https://pan277942135.github.io/Yujian_App/design/manager/nh01_acceptance/",
        "",
        "## Gates",
        "",
        "| Gate | Status |",
        "|---|---|",
    ]
    for gate, status in gate_status.items():
        lines.append(f"| {gate} | {status} |")
    lines.extend([
        "",
        "Figma Native acceptance remains independent. PR #164 Importer tests do not prove live Figma import. Hero photo rights and avatar authority are unresolved; no approved asset status is inferred.",
    ])
    (OUT / "NH01_CLOUD_HIFI_FINAL_REPORT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")

    (OUT / "README.md").write_text(
        "# NH01 Cloud High-Fidelity Closure\n\n"
        "The browser acceptance center is in ../manager/nh01_acceptance/. "
        "Structured JSON in that route is generated from the PR #164 mapping snapshot and the registered Frozen contracts.\n\n"
        "This directory stores source authority, existing structure inventory, and contract inventory. "
        "Android runtime and visual statuses are updated only from exact-source runtime evidence.\n",
        encoding="utf-8",
    )
    print(f"NH01_AUTHORITY_INVENTORY=PASS components={len(structure['components'])} bindings={len(rows)}")


if __name__ == "__main__":
    main()
