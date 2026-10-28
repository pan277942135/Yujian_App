#!/usr/bin/env python3
"""Static release-boundary checks for the NH01 browser acceptance center."""

from __future__ import annotations

import hashlib
import json
import re
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VIEW = ROOT / "design/manager/nh01_acceptance"


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.buttons: list[dict[str, str]] = []
        self.links: list[str] = []
        self.ids: set[str] = set()
        self._button: dict[str, str] | None = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            self.ids.add(attrs["id"])
        if tag == "a" and attrs.get("href"):
            self.links.append(attrs["href"])
        if tag == "button":
            self._button = attrs
            if attrs.get("data-mode"):
                self.buttons.append(attrs)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    html_path = VIEW / "index.html"
    js_path = VIEW / "viewer.js"
    html = html_path.read_text(encoding="utf-8")
    js = js_path.read_text(encoding="utf-8")
    parser = Page()
    parser.feed(html)
    required_modes = {"frozen", "structured", "inspector", "runtime", "compare", "heatmap"}
    modes = {button.get("data-mode") for button in parser.buttons}
    missing = sorted(required_modes - modes)
    if missing:
        raise SystemExit(f"Missing Viewer modes: {missing}")
    required_ids = {"view-overview", "view-frozen", "view-structured", "view-inspector", "view-runtime", "view-compare", "view-heatmap", "structuredPreview", "runtimeImageWrap", "diffTable"}
    if not required_ids.issubset(parser.ids):
        raise SystemExit(f"Missing Viewer anchors: {sorted(required_ids - parser.ids)}")
    html_source = html_path.read_text(encoding="utf-8")
    for comparison_mode in ('value="side"', 'value="overlay"', 'value="opacity"', 'value="wipe"'):
        if comparison_mode not in html_source:
            raise SystemExit(f"Visual Compare is missing supported mode {comparison_mode}")
    if 'id="compareOpacity"' not in html_source or 'id="heatZoom"' not in html_source:
        raise SystemExit("Visual Compare opacity or Pixel Heatmap zoom control is missing")
    manager = (ROOT / "design/manager/index.html").read_text(encoding="utf-8")
    if 'href="./nh01_acceptance/"' not in manager:
        raise SystemExit("Design Manager navigation does not expose NH01 Acceptance Center")

    artboard_source = js.split("function makeArtboard()", 1)[1].split("function renderAllPreviews()", 1)[0]
    if "ASSET_PATHS.frozen" in artboard_source or "normal_home_v1.png" in artboard_source:
        raise SystemExit("Frozen full-screen image is incorrectly used inside Structured Preview")
    for key in ("background", "brand", "camera"):
        if f"ASSET_PATHS.{key}" not in artboard_source:
            raise SystemExit(f"Structured Preview does not load registered {key} asset")

    structure = json.loads((VIEW / "data/NH01_Structure_V1.json").read_text(encoding="utf-8"))
    evidence = json.loads((VIEW / "data/NH01_Evidence_Provenance_V1.json").read_text(encoding="utf-8"))
    browser_diff = json.loads((VIEW / "data/NH01_Browser_Preview_Diff_V1.json").read_text(encoding="utf-8"))
    mapped = {row["id"]: row for row in structure["components"]}
    required_components = {"environment", "header", "statistics", "recent_header", "fish_pager", "hero_selected", "capture_action_group", "cta", "camera"}
    if not required_components.issubset(mapped):
        raise SystemExit(f"Missing NH01 component mappings: {sorted(required_components - set(mapped))}")
    if structure["status"] == "PASS" or structure["figma_readback"]["overall"] != "NH01_FIGMA_STRUCTURAL_HANDOFF=REVIEW_REQUIRED":
        raise SystemExit("Viewer cannot promote the current structure/Figma status to PASS")
    if evidence["gate_status"]["G4_RUNTIME_VALID"] == "PASS" or evidence["gate_status"]["G5_VISUAL_PARITY_VALID"] == "PASS":
        raise SystemExit("Unrun Android Runtime/visual parity gate was incorrectly marked PASS")
    if evidence["gate_status"]["G3_COMPOSE_MAPPING_VALID"] != "PASS":
        raise SystemExit("Compose mapping did not pass source-symbol readback")
    if browser_diff["status"] == "PASS" or browser_diff["classification"] not in {"NOT_RUN", "DIAGNOSTIC_ONLY_REVIEW_REQUIRED"}:
        raise SystemExit("Browser structured comparison cannot be promoted to parity PASS")
    if 'id="browserDiffPanel"' not in html or "renderBrowserDiff" not in js:
        raise SystemExit("Frozen/structured diagnostic comparison panel is missing")
    bindings = json.loads((VIEW / "data/NH01_Data_Bindings_V1.json").read_text(encoding="utf-8"))
    if any(row["source_symbol_readback"] != "PASS" for row in bindings["bindings"]):
        raise SystemExit("A Figma-to-Compose symbol mapping is not present in the checked-in Android source")
    if structure["preview_fixture"]["hero"]["photo_status"] != "BLOCKED_ASSET_SOURCE":
        raise SystemExit("Hero fixture rights block was lost")
    if structure["preview_fixture"]["avatar_status"] != "REVIEW_REQUIRED_AUTHORITY_CONFLICT":
        raise SystemExit("Avatar authority conflict was lost")

    frozen = ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png"
    if sha(frozen) != "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377":
        raise SystemExit("Frozen source hash mismatch")
    authority_dir = ROOT / "design/cloud_hifi/NH01"
    for name in ("source_manifest.json", "source_authority_report.md", "existing_structure_inventory.json", "design_contract_inventory.md", "NH01_Structure_Report.md", "NH01_CLOUD_HIFI_FINAL_REPORT.md"):
        if not (authority_dir / name).is_file():
            raise SystemExit(f"Missing required source/structure inventory: {authority_dir / name}")
    css = (VIEW / "viewer.css").read_text(encoding="utf-8")
    if "font:600 12px inherit" in css:
        raise SystemExit("Invalid inherited font shorthand remains in Viewer CSS")
    print(f"NH01_VIEWER_STATIC_VALIDATION=PASS modes={len(required_modes)} components={len(mapped)} frozen_sha={sha(frozen)}")


if __name__ == "__main__":
    main()
