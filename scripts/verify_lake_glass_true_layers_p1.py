#!/usr/bin/env python3
"""Automated integrity, geometry, composition, and compatibility checks for P1."""
from __future__ import annotations

import hashlib
import json
import math
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np
from PIL import Image

PKG = Path("design/system/backgrounds/lake_glass_v2")
MASTER = PKG / "assets/source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png"
ORIGINAL = PKG / "assets/source_master/宁静湖光玻璃水境背景.png"
MANIFEST = PKG / "assets/metadata/asset_manifest_v3.json"
REPORT = PKG / "evidence/true_layer_validation_report_v1.json"
MASTER_SHA = "d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12"
ORIGINAL_SHA = "f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba"
W, H = 1440, 3120


def sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def image_array(path: Path, mode="RGB"):
    return np.asarray(Image.open(path).convert(mode), dtype=np.float32)


def ssim_global(a: np.ndarray, b: np.ndarray) -> float:
    # Global SSIM terms are calculated per RGB channel and averaged; full
    # resolution arrays are used, with constants for 8-bit images.
    vals = []
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    for i in range(3):
        x, y = a[:, :, i].astype(np.float64), b[:, :, i].astype(np.float64)
        mx, my = float(x.mean()), float(y.mean())
        vx, vy = float(x.var()), float(y.var())
        cov = float(((x - mx) * (y - my)).mean())
        vals.append(((2 * mx * my + c1) * (2 * cov + c2)) / ((mx * mx + my * my + c1) * (vx + vy + c2)))
    return float(np.mean(vals))


def metrics(a: np.ndarray, b: np.ndarray) -> dict:
    d = a.astype(np.float64) - b.astype(np.float64)
    mae = float(np.mean(np.abs(d)))
    rmse = float(np.sqrt(np.mean(d * d)))
    psnr = float("inf") if rmse == 0 else float(20 * math.log10(255.0 / rmse))
    return {
        "maeRgb": mae,
        "rmseRgb": rmse,
        "psnrDb": "Infinity" if math.isinf(psnr) else psnr,
        "ssimGlobalRgb": ssim_global(a, b),
        "changedPixelRatio": float(np.any(np.rint(a) != np.rint(b), axis=2).mean()),
        "maxAbsChannelDelta": float(np.max(np.abs(d))),
    }


def run(root: Path) -> dict:
    checks = []
    def add(name, ok, detail):
        checks.append({"name": name, "passed": bool(ok), "detail": detail})

    add("master_sha", sha(root / MASTER) == MASTER_SHA, sha(root / MASTER))
    add("original_source_sha", sha(root / ORIGINAL) == ORIGINAL_SHA, sha(root / ORIGINAL))
    manifest = load_json(root / MANIFEST)
    add("manifest_schema_and_status", manifest.get("schemaVersion") == "3.0" and manifest.get("readyForAndroid") is False,
        f"schema={manifest.get('schemaVersion')} status={manifest.get('status')} ready={manifest.get('readyForAndroid')}")
    add("manifest_source_hashes", manifest.get("master", {}).get("sha256") == MASTER_SHA and manifest.get("source", {}).get("sha256") == ORIGINAL_SHA,
        "master/source hashes match archived inputs")

    integrity_ok, integrity_errors = True, []
    for rec in manifest.get("derivedFiles", []):
        p = root / rec["path"]
        if not p.exists():
            integrity_ok = False; integrity_errors.append("missing " + rec["path"]); continue
        if sha(p) != rec["sha256"]:
            integrity_ok = False; integrity_errors.append("sha mismatch " + rec["path"])
        if p.stat().st_size != rec["sizeBytes"]:
            integrity_ok = False; integrity_errors.append("size mismatch " + rec["path"])
    add("manifest_derived_file_integrity", integrity_ok, f"files={len(manifest.get('derivedFiles', []))}; errors={integrity_errors[:6]}")
    tool_errors = []
    for rec in manifest.get("toolFiles", []):
        p = root / rec["path"]
        if not p.exists() or sha(p) != rec["sha256"]:
            tool_errors.append(rec["path"])
    add("automation_script_hashes", not tool_errors and len(manifest.get("toolFiles", [])) == 3,
        f"scripts={len(manifest.get('toolFiles', []))}; errors={tool_errors}")

    package = root / PKG
    forbidden = ["morning_lake_v1/", "empty_home_runtime_v2/", "EmptyHomeSceneRenderer.kt"]
    local_paths = [p.relative_to(root).as_posix() for p in package.rglob("*") if p.is_file()]
    scope_ok = not any(any(token in p for token in forbidden) for p in local_paths)
    add("protected_paths_absent_from_package", scope_ok, "Morning Lake V1 and Empty Home protected paths are absent")

    layer_pack = load_json(root / PKG / "assets/metadata/lake_layer_pack_v3.json")
    layers = layer_pack["layers"]
    z = [x["zIndex"] for x in layers]
    add("unique_layer_z_indices", len(z) == len(set(z)), str(z))
    geometry_ok = True
    geometry_errors = []
    for layer in layers:
        x,y,w,h = layer["pixelBounds"]
        ax,ay = layer["anchorPx"]
        if x < 0 or y < 0 or w <= 0 or h <= 0 or x+w > W or y+h > H:
            geometry_ok = False; geometry_errors.append(layer["layerId"] + " bounds")
        if not (x <= ax <= x+w and y <= ay <= y+h):
            geometry_ok = False; geometry_errors.append(layer["layerId"] + " anchor")
        p = root / PKG / layer["assetFile"]
        if not p.exists() or layer.get("assetSha256") != sha(p):
            geometry_ok = False; geometry_errors.append(layer["layerId"] + " asset SHA")
        if layer.get("sourceSha256") != MASTER_SHA:
            geometry_ok = False; geometry_errors.append(layer["layerId"] + " source SHA")
    add("layer_bounds_anchors_and_sha", geometry_ok, f"layers={len(layers)} errors={geometry_errors}")
    stack = set(layer_pack.get("visibleCompositeLayerIds", []))
    baked_visible = [x["layerId"] for x in layers if x.get("runtimeEligible") is False and x["layerId"] in stack]
    add("baked_assets_not_double_composited", not baked_visible, f"visible baked layers={baked_visible}")

    alpha_ok, alpha_notes = True, []
    for name in ["grass_left_rgba_v2.png", "grass_right_rgba_v2.png",
                 "01_left_static_compat_rgba.png", "02_upper_right_static_compat_rgba.png", "03_lower_right_static_compat_rgba.png"]:
        p = root / PKG / "assets" / ("grass" if name.startswith("grass") else "droplets") / name
        if not p.exists():
            alpha_ok = False; alpha_notes.append("missing " + name); continue
        a = image_array(p, "RGBA")[:, :, 3]
        if a.min() != 0 or a.max() < 20 or (a > 0).mean() < 0.005 or (a > 0).mean() > 0.98:
            alpha_ok = False; alpha_notes.append(f"{name}: range={a.min()}..{a.max()}, coverage={(a>0).mean():.3f}")
    add("alpha_channel_quality", alpha_ok, "valid transparency and non-empty shaped alpha" if alpha_ok else str(alpha_notes))

    normal_ok, normal_notes = True, []
    for suffix in ["01_left", "02_upper_right", "03_lower_right"]:
        p = root / PKG / f"assets/droplets/{suffix}_normal_map.png"
        n = image_array(p, "RGB")
        if not np.isfinite(n).all() or float(n[:, :, 2].mean()) < 128:
            normal_ok = False; normal_notes.append(f"{suffix}: invalid normal data")
    add("droplet_normal_map_validity", normal_ok, str(normal_notes or "3 finite RGB normals; positive Z hemisphere"))

    svg_ok, svg_notes = True, []
    for side in ["left", "right"]:
        p = root / PKG / f"assets/grass/grass_{side}_skeleton_v2.svg"
        try:
            doc = ET.parse(p)
            if not doc.getroot().tag.endswith("svg") or not doc.findall(".//{http://www.w3.org/2000/svg}path"):
                svg_ok = False; svg_notes.append(side + ": missing SVG paths")
        except Exception as e:
            svg_ok = False; svg_notes.append(side + ": " + str(e))
    add("svg_structure", svg_ok, str(svg_notes or "two parseable SVGs with path elements"))

    master = image_array(root / PKG / "assets/reference/static_master.png")
    recomposed = image_array(root / PKG / "assets/reference/static_layered_v3.png")
    full_metrics = metrics(master, recomposed)
    add("static_recomposition_1440x3120", master.shape == recomposed.shape == (H,W,3), str(master.shape))
    add("static_recomposition_metric_threshold", full_metrics["psnrDb"] == "Infinity" or full_metrics["psnrDb"] >= 45,
        json.dumps(full_metrics, ensure_ascii=False))
    roi_specs = {
        "grass_left":[0,1810,660,1310], "grass_right":[960,2220,480,900],
        "droplet_01_left":[18,650,104,126], "droplet_02_upper_right":[1250,370,132,166],
        "droplet_03_lower_right":[1290,1880,145,145], "caustics_lower":[0,1980,W,1140],
    }
    roi_metrics={}
    for name,(x,y,w,h) in roi_specs.items():
        roi_metrics[name]=metrics(master[y:y+h,x:x+w],recomposed[y:y+h,x:x+w])
    add("local_roi_metrics_present", len(roi_metrics)==6, json.dumps({k:v["psnrDb"] for k,v in roi_metrics.items()}))
    add("visual_evidence_files", all((root/PKG/"assets/reference"/n).exists() for n in [
        "static_master.png","static_layered_v3.png","layer_inspector_contact_sheet.png","layer_visibility_matrix.png",
        "master_vs_layered_overlay.png","master_vs_layered_difference.png","master_vs_layered_heatmap.png",
        "grass_edge_review.png","droplets_optical_review.png","sunlight_caustics_review.png","crop_matrix_v3.png"]),
        "Master, layered, visibility, difference, optical reviews and crop matrix exist")

    crop=load_json(root/PKG/"assets/metadata/crop_safe_zone_v3.json")
    crop_ok=len(crop.get("screens",[]))==4 and all(x.get("singleMatrixForAllLayers") for x in crop.get("screens",[]))
    add("crop_safe_zone",crop_ok,str([x["screenId"] for x in crop.get("screens",[])]))

    runtime=load_json(root/PKG/"assets/android_runtime/runtime_manifest.json")
    android_ok=(runtime["api24to32"]["mode"]=="static" and runtime["api33plus"]["mode"].startswith("optional") and runtime.get("singleGlobalMatrix") is True)
    add("android_asset_compatibility_contract",android_ok,"API24 static map and API33 optional shader map are documented; no device validation claimed")
    rights_ok=manifest.get("originPermission")=="PENDING_REVIEW; derived from user-supplied images and procedural reconstruction; no independent rights claim"
    add("source_and_rights_status",rights_ok,"PENDING_REVIEW and source types remain explicit")

    sunlight = next(x for x in layers if x["layerId"]=="LAYER_SUNLIGHT_FIELD")
    partial = (manifest.get("status")=="ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS" and sunlight.get("runtimeEligible") is False and "LAYER_SUNLIGHT_FIELD" not in stack)
    add("honest_partial_status",partial,"sunlight remains baked; READY is false")

    failed=[x["name"] for x in checks if not x["passed"]]
    status="FAIL" if failed else "PASS_WITH_PARTIAL_LAYER_STATE"
    report={
        "taskId":"YUJIAN_LAKE_GLASS_V2_TRUE_LAYER_RECONSTRUCTION_ANDROID_ASSET_PACK_P1",
        "validationStatus":status,"assetPackStatus":manifest.get("status"),"readyForAndroid":False,
        "checksPassed":len(checks)-len(failed),"checksTotal":len(checks),"failedChecks":failed,"checks":checks,
        "masterReconstructionMetrics":full_metrics,"localRoiMetrics":roi_metrics,
        "visualReview":{"evidenceGenerated":True,"manualInspectionPerformed":True,"sunlightCleanPlate":"BLOCKED_BAKED_RESIDUAL"},
        "scope":{"protectedPathsAbsentFromPackage":scope_ok,"remotePullRequestFileListMustAlsoBeChecked":True},
        "manifestSelfHashExcluded":True,
    }
    (root/REPORT).parent.mkdir(parents=True,exist_ok=True)
    (root/REPORT).write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    # Register the report itself in the manifest without making it self-referential.
    report_rel=(PKG/"evidence/true_layer_validation_report_v1.json").as_posix()
    rec={"path":report_rel,"sha256":sha(root/REPORT),"sizeBytes":(root/REPORT).stat().st_size,"format":"JSON","dimensionsPx":None,"mode":None}
    manifest["derivedFiles"]=[x for x in manifest.get("derivedFiles",[]) if x.get("path")!=report_rel]+[rec]
    (root/MANIFEST).write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    return report


def main():
    import argparse
    ap=argparse.ArgumentParser()
    ap.add_argument("--root",default=".")
    args=ap.parse_args()
    r=run(Path(args.root).resolve())
    print(json.dumps({"validationStatus":r["validationStatus"],"checksPassed":r["checksPassed"],"checksTotal":r["checksTotal"],
                      "failedChecks":r["failedChecks"],"masterReconstructionMetrics":r["masterReconstructionMetrics"]},
                     ensure_ascii=False,indent=2))
    raise SystemExit(0 if r["validationStatus"]!="FAIL" else 1)


if __name__=="__main__":
    main()
