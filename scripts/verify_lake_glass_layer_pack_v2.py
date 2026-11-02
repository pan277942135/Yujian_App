#!/usr/bin/env python3
"""Verify provenance, geometry, alpha/vector candidates and static fidelity of Lake Glass V2."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image


BASE = Path("design/system/backgrounds/lake_glass_v2")
MASTER_NAME = "YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png"
SOURCE_NAME = "宁静湖光玻璃水境背景.png"
MASTER_SHA = "d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12"
SOURCE_SHA = "f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba"
SOURCE_MANIFEST_SHA = "623b978487ef294b7261c45e8ec1612ce372df61bbdca3859f5a9faa31af661b"


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--json-out", default="design/system/backgrounds/lake_glass_v2/evidence/validation_report.json")
    args = parser.parse_args()
    root = Path(args.repo_root).resolve()
    pack = root / BASE
    checks: list[dict[str, Any]] = []

    def check(name: str, passed: bool, detail: str) -> None:
        checks.append({"name": name, "passed": bool(passed), "detail": detail})

    manifest_path = pack / "assets/metadata/asset_manifest_v2.json"
    layer_path = pack / "assets/metadata/lake_layer_pack_v2.json"
    metrics_path = pack / "evidence/master_reconstruction_metrics.json"
    scope_path = pack / "evidence/task_scope_audit.json"
    source_master = pack / "assets/source_master" / MASTER_NAME
    source_original = pack / "assets/source_master" / SOURCE_NAME
    source_manifest = pack / "assets/source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_manifest.json"
    required = [manifest_path, layer_path, metrics_path, scope_path, source_master, source_original, source_manifest]
    check("required_core_files", all(p.is_file() for p in required), ", ".join(str(p.relative_to(root)) for p in required if not p.is_file()) or "all required files exist")
    if not all(p.is_file() for p in required):
        result = {"status": "FAIL", "checks": checks}
        out = root / args.json_out
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(result, indent=2))
        return 1

    manifest = load_json(manifest_path)
    layer_pack = load_json(layer_path)
    metrics = load_json(metrics_path)
    scope = load_json(scope_path)
    check("manifest_master_sha", digest(source_master) == MASTER_SHA == manifest["master"]["sha256"], digest(source_master))
    check("manifest_source_sha", digest(source_original) == SOURCE_SHA == manifest["source"]["sha256"], digest(source_original))
    check("manifest_input_manifest_sha", digest(source_manifest) == SOURCE_MANIFEST_SHA == manifest["sourceManifest"]["sha256"], digest(source_manifest))

    source_image = Image.open(source_master)
    original_image = Image.open(source_original)
    check("source_dimensions_and_format", source_image.size == (1440, 3120) and source_image.mode == "RGB" and original_image.size == (852, 1846), f"master={source_image.size}/{source_image.mode}; original={original_image.size}/{original_image.mode}")

    rows = manifest.get("derivedFiles", [])
    seen_paths: set[str] = set()
    file_failures = []
    for row in rows:
        rel = row["path"]
        if rel in seen_paths:
            file_failures.append(f"duplicate manifest path: {rel}")
            continue
        seen_paths.add(rel)
        p = root / rel
        if not p.is_file():
            file_failures.append(f"missing: {rel}")
            continue
        if digest(p) != row.get("sha256"):
            file_failures.append(f"sha mismatch: {rel}")
        if p.stat().st_size != row.get("sizeBytes"):
            file_failures.append(f"size mismatch: {rel}")
        if row.get("dimensionsPx"):
            try:
                im = Image.open(p)
                if list(im.size) != row["dimensionsPx"] or im.mode != row.get("mode"):
                    file_failures.append(f"image metadata mismatch: {rel}")
            except Exception:
                file_failures.append(f"unreadable image metadata: {rel}")
        elif p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}:
            file_failures.append(f"missing image dimensions in manifest: {rel}")
    allowed_unmanifested = {
        str((BASE / "assets/metadata/asset_manifest_v2.json").as_posix()),
        str((BASE / "evidence/validation_report.json").as_posix()),
    }
    actual_pack_files = {p.relative_to(root).as_posix() for p in pack.rglob("*") if p.is_file()}
    unmanifested = sorted(actual_pack_files - seen_paths - allowed_unmanifested)
    if unmanifested:
        file_failures.append("unmanifested: " + ", ".join(unmanifested))
    check("manifest_file_integrity", not file_failures, "; ".join(file_failures) or f"{len(rows)} manifested files pass hash, byte-size and image metadata checks")

    json_failures = []
    for p in pack.rglob("*.json"):
        if p.name == "validation_report.json":
            continue
        try:
            load_json(p)
        except Exception as exc:
            json_failures.append(f"{p.relative_to(root)}: {exc}")
    check("json_parse", not json_failures, "; ".join(json_failures) or "all package JSON parses")

    layers = layer_pack.get("layers", [])
    ids = [l.get("layerId") for l in layers]
    visible = layer_pack.get("visibleComposite", [])
    z = [l.get("zIndex") for l in layers if l.get("layerId") in visible]
    baked_duplicated = [l.get("layerId") for l in layers if l.get("status", "").startswith("BAKED_STATIC") and l.get("layerId") in visible and l.get("layerId") != "LAYER_BASE"]
    check("unique_layers_and_visible_order", len(ids) == len(set(ids)) and len(z) == len(set(z)) and visible == ["LAYER_BASE"] and not baked_duplicated, f"layerCount={len(layers)}, visibleComposite={visible}, duplicateVisibleBaked={baked_duplicated}")
    check("baked_assets_not_double_composited", layer_pack["compositing"].get("noDoubleCompositing") is True and layer_pack["compositing"].get("diagnosticAssetsExcluded") is True, "diagnostic extractions and masks are excluded from the visible stack")

    alpha_paths = [pack / "assets/grass/grass_left_candidate_diagnostic_only_rgba.png", pack / "assets/grass/grass_right_candidate_diagnostic_only_rgba.png"]
    alpha_ok = True
    alpha_details = []
    for p in alpha_paths:
        if not p.is_file():
            alpha_ok = False
            alpha_details.append(f"missing {p.name}")
            continue
        im = Image.open(p)
        a = np.asarray(im.getchannel("A")) if im.mode == "RGBA" else None
        good = a is not None and int(a.min()) == 0 and int(a.max()) > 0 and int(np.count_nonzero((a > 0) & (a < 255))) > 0
        alpha_ok &= good
        alpha_details.append(f"{p.name}: {im.mode}, softAlpha={good}")
    check("diagnostic_grass_rgba", alpha_ok, "; ".join(alpha_details) + "; not runtime approved")

    svg_paths = sorted((pack / "assets/vector").glob("*.svg"))
    svg_ok, svg_details = bool(svg_paths), []
    for p in svg_paths:
        try:
            tree = ET.parse(p)
            root_node = tree.getroot()
            has_viewbox = bool(root_node.attrib.get("viewBox"))
            has_paths = bool([e for e in root_node.iter() if e.tag.endswith("path") and e.attrib.get("d")])
            good = has_viewbox and has_paths
            svg_ok &= good
            svg_details.append(f"{p.name}: viewBox={has_viewbox}, paths={has_paths}")
        except Exception as exc:
            svg_ok = False
            svg_details.append(f"{p.name}: {exc}")
    check("svg_structure", svg_ok, "; ".join(svg_details))

    master = np.asarray(Image.open(source_master).convert("RGB"), dtype=np.uint8)
    recomposed_path = pack / "assets/reference/recomposed_static_1440x3120.png"
    if recomposed_path.is_file():
        recomposed = np.asarray(Image.open(recomposed_path).convert("RGB"), dtype=np.uint8)
        same = master.shape == recomposed.shape and np.array_equal(master, recomposed)
    else:
        same = False
    check("static_recomposition_pixel_identity", same and metrics.get("pixelIdentical") is True and metrics.get("maeRgb") == 0.0 and metrics.get("rmseRgb") == 0.0 and metrics.get("ssim") == 1.0, f"pixelIdentical={same}; metrics={metrics.get('maeRgb')}/{metrics.get('rmseRgb')}/{metrics.get('psnrDb')}/{metrics.get('ssim')}")

    required_visuals = [
        "master_annotated.png", "master_region_map.json", "master_visual_inventory.md", "master_color_samples.json",
        "recomposed_static_1440x3120.png", "master_vs_recomposed_overlay.png", "master_vs_recomposed_difference.png",
        "master_vs_recomposed_heatmap.png", "crop_matrix_contact_sheet.jpg", "layer_inspector_contact_sheet.jpg",
        "master_local_detail_review.jpg", "grass_candidates_on_black.jpg", "grass_candidates_on_white.jpg", "grass_candidates_on_checker.jpg",
        "local_details/grass_left_200pct_nearest.png", "local_details/droplet_02_200pct_nearest.png",
    ]
    missing_visuals = [n for n in required_visuals if not (pack / "assets/reference" / n).is_file()]
    check("visual_evidence", not missing_visuals, ", ".join(missing_visuals) or "annotated map, composition diffs, crops and edge reviews exist")

    allowed_prefixes = scope.get("allowedNewPrefixes", [])
    protected = scope.get("protectedPaths", [])
    check("scope_guard_definition", bool(allowed_prefixes) and all("empty_home" in p.lower() or "emptyhome" in p.lower() or "morning_lake_v1" in p.lower() for p in protected), f"allowedPrefixes={allowed_prefixes}; protectedPaths={protected}; remote PR file list remains authoritative")
    # The actual GitHub PR filenames are checked after remote submission; this local pack contains no app source edits.
    forbidden_pack_refs = [p for p in actual_pack_files if "empty_home" in p.lower() or "morning_lake_v1" in p.lower()]
    check("no_empty_home_or_v1_payload", not forbidden_pack_refs, ", ".join(forbidden_pack_refs) or "no Empty Home or Morning Lake V1 asset path exists in this package")

    status = manifest.get("status")
    valid_partial = status == "ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS" and layer_pack.get("status") == status
    check("honest_completion_status", valid_partial, f"status={status}; independent visible layers are not claimed")
    failed = [c for c in checks if not c["passed"]]
    result = {
        "taskId": "YUJIAN_LAKE_GLASS_V2_MASTER_TO_LAYERED_ASSET_PACK_P0",
        "validationStatus": "PASS_WITH_PARTIAL_LAYER_STATE" if not failed else "FAIL",
        "assetPackStatus": status,
        "checksPassed": len(checks) - len(failed),
        "checksTotal": len(checks),
        "checks": checks,
        "readyForAndroid": False,
        "reason": "Static master fallback and supporting analysis assets are verified, but independent optical layers remain baked and user review/native layered source is still needed before runtime use.",
    }
    out = root / args.json_out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
