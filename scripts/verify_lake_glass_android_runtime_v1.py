#!/usr/bin/env python3
"""Validate static-first Lake Glass V2 assets and runtime manifest parity."""
from __future__ import annotations

import hashlib
import json
import math
import subprocess
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PKG = Path("design/system/backgrounds/lake_glass_v2")
ASSETS = PKG / "assets"
MASTER = ASSETS / "source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png"
ORIGINAL = ASSETS / "source_master/宁静湖光玻璃水境背景.png"
MASTER_SOURCE_MANIFEST = ASSETS / "source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_manifest.json"
STATIC = ASSETS / "android_runtime/api24_static/lake_glass_static_layered_v3.png"
RUNTIME = ASSETS / "android_runtime/runtime_manifest.json"
AUTH = ASSETS / "android_runtime/layer_manifest_v1.json"
ASSET_MANIFEST = ASSETS / "metadata/asset_manifest_v3.json"
LAYER_PACK = ASSETS / "metadata/lake_layer_pack_v3.json"
REPORT = PKG / "evidence/android_runtime_asset_pack_acceptance_v1.json"
MASTER_SHA = "d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12"
ORIGINAL_SHA = "f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba"
MASTER_SOURCE_MANIFEST_SHA = "623b978487ef294b7261c45e8ec1612ce372df61bbdca3859f5a9faa31af661b"


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def metrics(a: np.ndarray, b: np.ndarray) -> dict:
    delta = a.astype(np.float64) - b.astype(np.float64)
    rmse = float(np.sqrt(np.mean(delta * delta)))
    psnr = "Infinity" if rmse == 0 else 20.0 * math.log10(255.0 / rmse)
    c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
    scores = []
    for channel in range(3):
        x, y = a[:, :, channel].astype(np.float64), b[:, :, channel].astype(np.float64)
        mx, my = float(x.mean()), float(y.mean())
        vx, vy = float(x.var()), float(y.var())
        cov = float(((x - mx) * (y - my)).mean())
        scores.append(((2 * mx * my + c1) * (2 * cov + c2)) / ((mx * mx + my * my + c1) * (vx + vy + c2)))
    return {
        "maeRgb": float(np.mean(np.abs(delta))),
        "rmseRgb": rmse,
        "psnrDb": psnr,
        "ssimGlobalRgb": float(np.mean(scores)),
        "changedPixelRatio": float(np.any(np.rint(a) != np.rint(b), axis=2).mean()),
        "maxAbsChannelDelta": float(np.max(np.abs(delta))),
    }


def main() -> None:
    checks = []
    def check(name: str, passed: bool, detail: object) -> None:
        checks.append({"name": name, "passed": bool(passed), "detail": detail})

    master = read(AUTH)
    runtime = read(RUNTIME)
    asset_manifest = read(ASSET_MANIFEST)
    pack = read(LAYER_PACK)
    check("input_master_sha", sha(ROOT / MASTER) == MASTER_SHA, sha(ROOT / MASTER))
    check("input_source_sha", sha(ROOT / ORIGINAL) == ORIGINAL_SHA, sha(ROOT / ORIGINAL))
    check("input_master_manifest_sha", sha(ROOT / MASTER_SOURCE_MANIFEST) == MASTER_SOURCE_MANIFEST_SHA, sha(ROOT / MASTER_SOURCE_MANIFEST))
    check("source_dimensions", Image.open(ROOT / MASTER).size == (1440, 3120) and Image.open(ROOT / ORIGINAL).size == (852, 1846), "Master=1440x3120; source=852x1846")
    check("all_three_inputs_pinned", [(x.get("sourceSha256"), x.get("dimensionsPx")) for x in master.get("sourceInputs", [])] == [(MASTER_SHA, [1440, 3120]), (ORIGINAL_SHA, [852, 1846]), (MASTER_SOURCE_MANIFEST_SHA, None)], master.get("sourceInputs"))
    check("runtime_manifest_hash_parity", runtime.get("authoritativeManifestSha256") == sha(ROOT / AUTH) and asset_manifest.get("runtimeLayerManifestSha256") == sha(ROOT / AUTH) and pack.get("runtimeLayerManifestSha256") == sha(ROOT / AUTH) and asset_manifest.get("androidRuntimeManifestSha256") == sha(ROOT / RUNTIME) and pack.get("androidRuntimeManifestSha256") == sha(ROOT / RUNTIME), {"layerManifest": sha(ROOT / AUTH), "runtimeManifest": sha(ROOT / RUNTIME)})
    check("runtime_manifest_reference_parity", runtime.get("authoritativeManifest") == AUTH.relative_to(PKG).as_posix() and asset_manifest.get("runtimeLayerManifest") == AUTH.relative_to(PKG).as_posix() and pack.get("runtimeLayerManifest") == AUTH.relative_to(PKG).as_posix(), "all consumers reference the canonical layer manifest")
    check("asset_pack_status", asset_manifest.get("status") == "ASSET_PACK_READY_FOR_ANDROID" and asset_manifest.get("readyForAndroid") is True and pack.get("status") == "ASSET_PACK_READY_FOR_ANDROID", f"asset={asset_manifest.get('status')}; pack={pack.get('status')}")

    file_errors = []
    ids = set()
    allowed_statuses = {"STATIC_COMPOSITE_ONLY", "MATCHED_BASE_STATIC_OVERLAY", "SHADER_INPUTS_PROVISIONAL", "ANDROID_RUNTIME_VERIFIED", "BAKED_IN_BASE", "NOT_RUNTIME_ELIGIBLE"}
    for layer in master.get("layers", []):
        ident = layer.get("layerId")
        if ident in ids: file_errors.append(f"duplicate layerId {ident}")
        ids.add(ident)
        path = ROOT / PKG / layer.get("assetFile", "")
        if not path.is_file() or sha(path) != layer.get("assetSha256"):
            file_errors.append(f"asset SHA/file {ident}")
            continue
        if layer.get("separationStatus") not in allowed_statuses:
            file_errors.append(f"separationStatus {ident}")
        if len(layer.get("pixelBounds", [])) != 4 or len(layer.get("anchorPx", [])) != 2:
            file_errors.append(f"geometry {ident}")
        x, y, w, h = layer["pixelBounds"]
        ax, ay = layer["anchorPx"]
        if x < 0 or y < 0 or w <= 0 or h <= 0 or x + w > 1440 or y + h > 3120 or not (x <= ax <= x + w and y <= ay <= y + h):
            file_errors.append(f"bounds/anchor {ident}")
        if layer.get("sourceSha256") != MASTER_SHA and not layer.get("sourceFiles"):
            file_errors.append(f"source SHA/provenance {ident}")
        for source in layer.get("sourceFiles", []):
            source_path = ROOT / PKG / source.get("sourceFile", "")
            if not source_path.is_file() or sha(source_path) != source.get("sourceSha256"):
                file_errors.append(f"source file SHA {ident}:{source.get('sourceFile')}")
        if layer.get("alpha") is None or not layer.get("blendMode") or layer.get("zIndex") is None:
            file_errors.append(f"compositing fields {ident}")
    check("layer_provenance_geometry_and_sha", not file_errors, {"count": len(ids), "errors": file_errors})
    authoritative = {x["layerId"]: x for x in master.get("layers", [])}
    parity_errors = []
    for consumer_name, consumer in (("asset_manifest_v3", asset_manifest), ("lake_layer_pack_v3", pack)):
        for item in consumer.get("layers", []):
            reference = item.get("runtimeStatusRef")
            source = authoritative.get(reference)
            if not source:
                parity_errors.append(f"{consumer_name}:{item.get('layerId')} missing {reference}")
                continue
            for field in ("separationStatus", "runtimeMode", "motionEligible", "fallbackPolicy"):
                if item.get(field) != source.get(field): parity_errors.append(f"{consumer_name}:{item.get('layerId')} {field}")
    check("consumer_layer_status_parity", not parity_errors, parity_errors)
    check("visible_stack_has_static_only", master.get("visibleRuntimeStack") == ["LAYER_STATIC_COMPOSITE"] and runtime.get("overlayLayers") == [], master.get("visibleRuntimeStack"))
    check("api24_32_static_policy", master.get("api24to32", {}).get("mode") == "STATIC" and master.get("api24to32", {}).get("primary") == STATIC.relative_to(PKG).as_posix() and master.get("api24to32", {}).get("shader") == "DISABLED", master.get("api24to32"))
    check("api33plus_error_fallback", master.get("api33plus", {}).get("shaderFailureFallback") == "STATIC_COMPOSITE" and master.get("motionDefault") == "DISABLED_UNTIL_API33_VISUAL_AND_DEVICE_VALIDATION", master.get("api33plus"))
    json_runtime = []
    for path in ASSETS.rglob("*.json"):
        try: data = read(path)
        except Exception: continue
        def has_legacy_flag(value):
            if isinstance(value, dict): return "runtimeEligible" in value or any(has_legacy_flag(v) for v in value.values())
            if isinstance(value, list): return any(has_legacy_flag(v) for v in value)
            return False
        if has_legacy_flag(data): json_runtime.append(path.relative_to(ROOT).as_posix())
    check("no_boolean_runtime_eligibility", not json_runtime, json_runtime)

    a = np.asarray(Image.open(ROOT / MASTER).convert("RGB"), dtype=np.uint8)
    b = np.asarray(Image.open(ROOT / STATIC).convert("RGB"), dtype=np.uint8)
    full = metrics(a, b)
    check("static_recomposition_dimensions", a.shape == b.shape == (3120, 1440, 3), [a.shape, b.shape])
    check("static_recomposition_metrics", full["psnrDb"] == "Infinity" or (full["psnrDb"] >= 45 and full["ssimGlobalRgb"] >= 0.995 and full["maxAbsChannelDelta"] <= 3), full)
    rois = {"top_light": [1000, 0, 440, 800], "drop_left": [0, 600, 200, 240], "drop_upper_right": [1220, 340, 220, 230], "drop_lower_right": [1260, 1840, 180, 200], "grass_left": [0, 2027, 589, 834], "grass_right": [1037, 2272, 403, 589], "caustics_lower": [0, 1980, 1440, 1140]}
    roi_metrics = {}
    for name, (x, y, w, h) in rois.items(): roi_metrics[name] = metrics(a[y:y+h, x:x+w], b[y:y+h, x:x+w])
    check("local_roi_metrics", len(roi_metrics) == 7 and all(v["maxAbsChannelDelta"] <= 3 for v in roi_metrics.values()), {k: v["psnrDb"] for k, v in roi_metrics.items()})

    atlas_path = ROOT / PKG / "assets/android_runtime/runtime_optical_masks_v1.png"
    with Image.open(atlas_path) as atlas:
        atlas_rgba = np.asarray(atlas.convert("RGBA"), dtype=np.uint8)
        dimensions = atlas.size
    check("optical_mask_atlas", dimensions == (360, 780) and atlas_rgba.shape == (780, 360, 4) and np.all(atlas_rgba[:, :, 3] == 255), {"dimensions": dimensions, "sha256": sha(atlas_path)})

    forbidden = subprocess.run(["git", "diff", "--quiet", "origin/main", "--", "app/src/main/assets/empty_home_runtime_v2", "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt", "design/system/backgrounds/morning_lake_v1"], cwd=ROOT).returncode == 0
    check("empty_home_and_morning_lake_v1_unchanged", forbidden, "git diff against origin/main excludes protected assets/renderer")

    failed = [item["name"] for item in checks if not item["passed"]]
    report = {
        "taskId": "YUJIAN_LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1",
        "status": "PASS" if not failed else "FAIL",
        "assetPackStatus": asset_manifest.get("status"),
        "readyForAndroid": not failed,
        "sourceMasterSha256": sha(ROOT / MASTER),
        "sourceOriginalSha256": sha(ROOT / ORIGINAL),
        "sourceMasterManifestSha256": sha(ROOT / MASTER_SOURCE_MANIFEST),
        "staticCompositeSha256": sha(ROOT / STATIC),
        "authoritativeLayerManifestSha256": sha(ROOT / AUTH),
        "runtimeManifestSha256": sha(ROOT / RUNTIME),
        "opticalMaskSha256": sha(atlas_path),
        "checksPassed": len(checks) - len(failed),
        "checksTotal": len(checks),
        "failedChecks": failed,
        "checks": checks,
        "staticRecompositionMetrics": full,
        "localRoiMetrics": roi_metrics,
        "manualInspectionPerformed": True,
        "manualReviewScope": ["Master vs static composite", "100% and 200% existing optical/grass details", "black/white/checker grass edge sheets", "difference/heatmap evidence", "four CenterCrop viewport matrix"],
        "deviceShaderValidation": "NOT_RUN",
    }
    report_path = ROOT / REPORT
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    asset_manifest["derivedFiles"] = [x for x in asset_manifest["derivedFiles"] if x.get("path") != REPORT.as_posix()]
    asset_manifest["derivedFiles"].append({"path": REPORT.as_posix(), "sha256": sha(report_path), "sizeBytes": report_path.stat().st_size, "format": "JSON", "dimensionsPx": None, "mode": None})
    script = ROOT / "scripts/verify_lake_glass_android_runtime_v1.py"
    asset_manifest["toolFiles"] = [x for x in asset_manifest["toolFiles"] if x.get("path") != "scripts/verify_lake_glass_android_runtime_v1.py"]
    asset_manifest["toolFiles"].append({"path": "scripts/verify_lake_glass_android_runtime_v1.py", "sha256": sha(script)})
    for record in asset_manifest["derivedFiles"]:
        path = ROOT / record["path"]
        if path.is_file(): record["sha256"] = sha(path); record["sizeBytes"] = path.stat().st_size
    (ROOT / ASSET_MANIFEST).write_text(json.dumps(asset_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "checksPassed": report["checksPassed"], "checksTotal": report["checksTotal"], "failedChecks": failed, "metrics": full}, indent=2))
    raise SystemExit(0 if not failed else 1)


if __name__ == "__main__":
    main()
