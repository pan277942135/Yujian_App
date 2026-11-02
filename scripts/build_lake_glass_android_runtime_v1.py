#!/usr/bin/env python3
"""Build the canonical Lake Glass V2 Android runtime manifest and mask atlas."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PKG = Path("design/system/backgrounds/lake_glass_v2")
ASSETS = PKG / "assets"
MASTER = ASSETS / "source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png"
MASTER_SHA = "d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12"
MASTER_MANIFEST_SHA = "623b978487ef294b7261c45e8ec1612ce372df61bbdca3859f5a9faa31af661b"
CANVAS = (1440, 3120)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rel(path: Path) -> str:
    return path.relative_to(PKG).as_posix()


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def dump_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def image_l(path: Path) -> Image.Image:
    with Image.open(path) as im:
        return im.convert("L")


def build_mask_atlas() -> tuple[Path, list[dict]]:
    master = Image.open(MASTER)
    if master.size != CANVAS or sha(MASTER) != MASTER_SHA:
        raise SystemExit("The pinned Master is missing, has the wrong dimensions, or has a SHA mismatch.")
    size = (360, 780)
    caustic_source = ASSETS / "caustics/caustics_luma_analysis_mask.png"
    sunlight_source = ASSETS / "sunlight/sunlight_soft_analysis_mask.png"
    drop_sources = [ASSETS / f"droplets/droplet_{n}_region_mask.png" for n in ("01_left", "02_upper_right", "03_lower_right")]
    channels = [image_l(p).resize(size, Image.Resampling.LANCZOS) for p in (caustic_source, sunlight_source)]
    drop = np.maximum.reduce([np.asarray(image_l(p), dtype=np.uint8) for p in drop_sources])
    channels.append(Image.fromarray(drop, "L").resize(size, Image.Resampling.LANCZOS))
    atlas = Image.merge("RGBA", (channels[0], channels[1], channels[2], Image.new("L", size, 255)))
    target = ASSETS / "android_runtime/runtime_optical_masks_v1.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(target, format="PNG", optimize=True)
    sources = [caustic_source, sunlight_source, *drop_sources]
    records = [{"sourceFile": rel(p), "sourceSha256": sha(p)} for p in sources]
    return target, records


def main() -> None:
    if sha(MASTER) != MASTER_SHA:
        raise SystemExit("Pinned Master SHA mismatch")
    source_manifest = ASSETS / "source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_manifest.json"
    if sha(source_manifest) != MASTER_MANIFEST_SHA:
        raise SystemExit("The supplied Master Manifest changed; refusing to normalize or replace the input.")
    asset_manifest_path = ASSETS / "metadata/asset_manifest_v3.json"
    layer_pack_path = ASSETS / "metadata/lake_layer_pack_v3.json"
    runtime_path = ASSETS / "android_runtime/runtime_manifest.json"
    atlas, atlas_sources = build_mask_atlas()
    asset_manifest = load_json(asset_manifest_path)
    asset_manifest["sourceTaskId"] = asset_manifest.get("taskId")
    asset_manifest["taskId"] = "YUJIAN_LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1"
    layer_pack = load_json(layer_pack_path)
    anchors = load_json(ASSETS / "droplets/droplets_anchors_v2.json")

    canonical_layers: list[dict] = []
    def add_layer(layer_id: str, asset_path: Path, bounds: list[int], anchor: list[int], z: int,
                  blend: str, alpha: float, separation: str, mode: str, fallback: str,
                  source_files: list[dict] | None = None, effect_channel: str | None = None,
                  note: str = "") -> None:
        item = {
            "layerId": layer_id,
            "assetFile": rel(asset_path),
            "assetSha256": sha(asset_path),
            "sourceFile": rel(MASTER),
            "sourceSha256": MASTER_SHA,
            "sourceFiles": source_files or [{"sourceFile": rel(MASTER), "sourceSha256": MASTER_SHA}],
            "format": asset_path.suffix[1:].upper(),
            "dimensionsPx": list(Image.open(asset_path).size) if asset_path.suffix.lower() == ".png" else None,
            "pixelBounds": bounds,
            "anchorPx": anchor,
            "anchorNormalized": [round(anchor[0] / CANVAS[0], 6), round(anchor[1] / CANVAS[1], 6)],
            "zIndex": z,
            "blendMode": blend,
            "alpha": alpha,
            "separationStatus": separation,
            "runtimeMode": mode,
            "motionEligible": mode.startswith("API33_") or mode == "STATIC_COMPOSITE",
            "fallbackPolicy": fallback,
            "note": note,
        }
        if effect_channel:
            item["effectChannel"] = effect_channel
        canonical_layers.append(item)

    static = ASSETS / "android_runtime/api24_static/lake_glass_static_layered_v3.png"
    master_fallback = ASSETS / "android_runtime/api24_static/lake_glass_master_fallback.png"
    mask_bounds = [0, 0, 1440, 3120]
    add_layer("LAYER_STATIC_COMPOSITE", static, mask_bounds, [0, 0], 0, "SRC", 1.0,
              "STATIC_COMPOSITE_ONLY", "STATIC_COMPOSITE", "STATIC_COMPOSITE",
              note="Validated full-scene composition; it is the only visible runtime surface on API 24–32 and the universal fallback.")
    add_layer("LAYER_MASTER_FALLBACK", master_fallback, mask_bounds, [0, 0], 0, "SRC", 1.0,
              "STATIC_COMPOSITE_ONLY", "STATIC_FALLBACK_ONLY", "STATIC_COMPOSITE",
              note="Byte-identical user-supplied Master fallback; retained for exact source recovery.")

    layer_by_id = {x["layerId"]: x for x in layer_pack["layers"]}
    base = layer_by_id["LAYER_BASE_CLEAN"]
    add_layer("LAYER_BASE_CLEAN_DIAGNOSTIC", PKG / base["assetFile"], base["pixelBounds"], base["anchorPx"], 0,
              "SRC", 1.0, "NOT_RUNTIME_ELIGIBLE", "DIAGNOSTIC_ONLY", "STATIC_COMPOSITE",
              note="Repaired plate retains baked upper-right sunlight and lower grass/root-bed residuals; not drawn separately.")

    add_layer("LAYER_CAUSTICS_MODULATION", atlas, mask_bounds, [720, 2850], 1,
              "IN_PLACE_LUMA_MULTIPLY", 0.0025, "SHADER_INPUTS_PROVISIONAL", "API33_IN_PLACE_MASKED_MODULATION",
              "STATIC_COMPOSITE", source_files=atlas_sources, effect_channel="R",
              note="Uses the Master-derived caustics mask to multiply existing baked luminance; never draws a second caustics sheet.")
    add_layer("LAYER_SUNLIGHT_MODULATION", atlas, mask_bounds, [1361, 109], 2,
              "IN_PLACE_LUMA_MULTIPLY", 0.0015, "BAKED_IN_BASE", "API33_IN_PLACE_LOW_AMPLITUDE_MODULATION",
              "STATIC_COMPOSITE", source_files=atlas_sources, effect_channel="G",
              note="The Master light remains baked and fixed at the upper-right anchor; the field can only modulate existing pixels.")

    for index, drop in enumerate(anchors["droplets"], start=1):
        x, y, w, h = drop["bbox"]
        add_layer(f"LAYER_DROPLET_{index:02d}_REFRACTION", atlas, [x, y, w, h], drop["center"], 3 + index,
                  "IN_PLACE_SCREEN_SPACE_REFRACTION", 0.35, "SHADER_INPUTS_PROVISIONAL",
                  "API33_FIXED_ANCHOR_IN_PLACE_REFRACTION", "STATIC_COMPOSITE",
                  source_files=atlas_sources, effect_channel="B",
                  note="Fixed Master anchor/SDF bounds from the resource anchor file; no RGBA surface is drawn. Normal maps remain estimates.")

    for side in ("left", "right"):
        src = layer_by_id[f"LAYER_GRASS_{side.upper()}"]
        add_layer(f"LAYER_GRASS_{side.upper()}_DIAGNOSTIC", PKG / src["assetFile"], src["pixelBounds"], src["anchorPx"], 10 + (side == "right"),
                  "SRC_OVER_DIAGNOSTIC_ONLY", 1.0, "NOT_RUNTIME_ELIGIBLE", "DIAGNOSTIC_ONLY", "STATIC_COMPOSITE",
                  note="Flattened-source extraction has estimated alpha and baked root-bed overlap; do not draw over the complete scene.")

    canonical = {
        "schemaVersion": "1.0",
        "manifestId": "LAKE_GLASS_V2_ANDROID_RUNTIME_LAYER_MANIFEST_V1",
        "taskIds": ["YUJIAN_LAKE_GLASS_V2_ASSET_PACK_ANDROID_RUNTIME_INTEGRATION_V1", "YUJIAN_LAKE_GLASS_V2_OPEN_SOURCE_SHADER_REUSE_V1"],
        "master": {"sourceFile": rel(MASTER), "sourceSha256": MASTER_SHA, "dimensionsPx": list(CANVAS)},
        "sourceInputs": [
            {"role": "master", "sourceFile": rel(MASTER), "sourceSha256": MASTER_SHA, "dimensionsPx": list(CANVAS)},
            {"role": "original_source", "sourceFile": "assets/source_master/宁静湖光玻璃水境背景.png", "sourceSha256": "f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba", "dimensionsPx": [852, 1846]},
            {"role": "master_manifest", "sourceFile": rel(source_manifest), "sourceSha256": MASTER_MANIFEST_SHA, "dimensionsPx": None},
        ],
        "canvasSizePx": list(CANVAS),
        "coordinateSystem": "Master px, top-left origin; every visible/effect sample uses one viewport transform.",
        "visibleRuntimeStack": ["LAYER_STATIC_COMPOSITE"],
        "api24to32": {"mode": "STATIC", "primary": rel(static), "fallback": rel(master_fallback), "shader": "DISABLED"},
        "api33plus": {"mode": "OPTIONAL_AGSL_IN_PLACE_MODULATION", "staticFallback": rel(static), "shaderFailureFallback": "STATIC_COMPOSITE", "overlayLayers": [], "effects": ["LAYER_CAUSTICS_MODULATION", "LAYER_SUNLIGHT_MODULATION", "LAYER_DROPLET_01_REFRACTION", "LAYER_DROPLET_02_REFRACTION", "LAYER_DROPLET_03_REFRACTION"]},
        "motionDefault": "DISABLED_UNTIL_API33_VISUAL_AND_DEVICE_VALIDATION",
        "noDuplicateVisibleSurfaces": True,
        "noGLSurfaceView": True,
        "layers": canonical_layers,
    }
    canonical_path = ASSETS / "android_runtime/layer_manifest_v1.json"
    dump_json(canonical_path, canonical)
    manifest_sha = sha(canonical_path)

    # All three consumer manifests carry the same authoritative reference and statuses.
    status_by_id = {x["layerId"]: {k: x[k] for k in ("separationStatus", "runtimeMode", "motionEligible", "fallbackPolicy")} for x in canonical_layers}
    for container in (asset_manifest, layer_pack):
        container["runtimeLayerManifest"] = rel(canonical_path)
        container["runtimeLayerManifestSha256"] = manifest_sha
        for item in container.get("layers", []):
            item.pop("runtimeEligible", None)
            ident = item["layerId"]
            canonical_id = ident
            if ident == "LAYER_BASE_CLEAN": canonical_id = "LAYER_BASE_CLEAN_DIAGNOSTIC"
            elif ident == "LAYER_SUNLIGHT_FIELD": canonical_id = "LAYER_SUNLIGHT_MODULATION"
            elif ident == "LAYER_CAUSTICS": canonical_id = "LAYER_CAUSTICS_MODULATION"
            elif ident == "LAYER_GRASS_LEFT": canonical_id = "LAYER_GRASS_LEFT_DIAGNOSTIC"
            elif ident == "LAYER_GRASS_RIGHT": canonical_id = "LAYER_GRASS_RIGHT_DIAGNOSTIC"
            elif ident.startswith("LAYER_DROPLET_"):
                canonical_id = ident.replace("LAYER_DROPLET_01_LEFT", "LAYER_DROPLET_01_REFRACTION").replace("LAYER_DROPLET_02_UPPER_RIGHT", "LAYER_DROPLET_02_REFRACTION").replace("LAYER_DROPLET_03_LOWER_RIGHT", "LAYER_DROPLET_03_REFRACTION")
            item.update(status_by_id[canonical_id])
            item["runtimeStatusRef"] = canonical_id
        container["status"] = "ASSET_PACK_READY_FOR_ANDROID"
        container["readyForAndroid"] = True
        if "visibleCompositeLayerIds" in container:
            container["sourceReconstructionLayerIds"] = container["visibleCompositeLayerIds"]
            container["visibleCompositeLayerIds"] = ["LAYER_STATIC_COMPOSITE"]
            container["excludedFromVisibleStack"] = [x["layerId"] for x in canonical_layers if x["layerId"] != "LAYER_STATIC_COMPOSITE"]
    asset_manifest["statusReason"] = "Static composite is visually verified. Extracted grass and RGBA drop candidates stay diagnostic; baked effects are modulated in place only, with an API 24+ static fallback."
    asset_manifest["originPermission"] = "USER_PROVIDED_TASK_INPUT; task authorization covers derived assets; no ownership claim beyond supplied source."
    asset_manifest.setdefault("derivedFiles", [])
    for path in (atlas, canonical_path):
        record = {"path": path.as_posix(), "sha256": sha(path), "sizeBytes": path.stat().st_size, "format": path.suffix[1:].upper(), "dimensionsPx": None, "mode": None}
        if path.suffix.lower() == ".png":
            with Image.open(path) as im:
                record.update({"dimensionsPx": list(im.size), "mode": im.mode, "colorSpace": "sRGB", "decodedMemoryBytesRgba8888": im.width * im.height * 4})
        asset_manifest["derivedFiles"] = [x for x in asset_manifest["derivedFiles"] if x.get("path") != record["path"]] + [record]
    dump_json(asset_manifest_path, asset_manifest)

    layer_pack["status"] = "ASSET_PACK_READY_FOR_ANDROID"
    layer_pack["readyForAndroid"] = True
    layer_pack["runtimeVisibleStack"] = ["LAYER_STATIC_COMPOSITE"]
    dump_json(layer_pack_path, layer_pack)

    runtime = {
        "schemaVersion": "2.0",
        "authoritativeManifest": rel(canonical_path),
        "authoritativeManifestSha256": manifest_sha,
        "androidHost": "LakeGlassBackgroundHost",
        "renderer": "LakeGlassRenderer",
        "assetRepository": "LakeGlassAssetRepository",
        "ambientController": "AmbientController (separate clock; never the recognition SceneClock)",
        "api24to32": canonical["api24to32"],
        "api33plus": canonical["api33plus"],
        "singleGlobalMatrix": True,
        "overlayLayers": [],
        "diagnosticAssetsExcludedFromApk": True,
        "decodeCache": "one async process-level scene cache; no per-frame Bitmap or RuntimeShader allocation",
        "status": "ASSET_PACK_READY_FOR_ANDROID",
    }
    dump_json(runtime_path, runtime)
    runtime_hash = sha(runtime_path)
    for container_path in (asset_manifest_path, layer_pack_path):
        container = load_json(container_path)
        container["androidRuntimeManifest"] = rel(runtime_path)
        container["androidRuntimeManifestSha256"] = runtime_hash
        dump_json(container_path, container)

    # Normalize leaf parameter files: a boolean is not a runtime policy.
    for path in ASSETS.rglob("*.json"):
        if path in (asset_manifest_path, layer_pack_path, canonical_path, runtime_path):
            continue
        if path.parent.name == "source_master":
            continue
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        def strip(value):
            if isinstance(value, dict):
                value.pop("runtimeEligible", None)
                for child in value.values(): strip(child)
            elif isinstance(value, list):
                for child in value: strip(child)
        strip(value)
        if path.name == "caustics_params_v2.json":
            value["separationStatus"] = "SHADER_INPUTS_PROVISIONAL"
            value["runtimeMode"] = "API33_IN_PLACE_MASKED_MODULATION"
            value["motionEligible"] = False
        elif path.name in ("sunlight_params_v2.json", "sunlight_anchor_v2.json"):
            value["separationStatus"] = "BAKED_IN_BASE"
            value["runtimeMode"] = "API33_IN_PLACE_LOW_AMPLITUDE_MODULATION"
            value["motionEligible"] = False
        elif path.name in ("droplets_anchors.json", "droplets_anchors_v2.json"):
            value["runtimeMode"] = "API33_FIXED_ANCHOR_IN_PLACE_REFRACTION"
            value["motionEligible"] = False
            for drop in value.get("droplets", []):
                drop["separationStatus"] = "SHADER_INPUTS_PROVISIONAL"
                drop["runtimeMode"] = "API33_FIXED_ANCHOR_IN_PLACE_REFRACTION"
        elif "motion_bones" in path.name:
            value["separationStatus"] = "NOT_RUNTIME_ELIGIBLE"
            value["runtimeMode"] = "DIAGNOSTIC_ONLY"
            value["motionEligible"] = False
        dump_json(path, value)

    review = ASSETS / "metadata/layer_feasibility_review.md"
    review.write_text("""# Layer feasibility review\n\n## Canonical runtime decision\n\n`assets/android_runtime/layer_manifest_v1.json` is authoritative for every consumer. The supplied images are flattened RGB. No safe transparent cutout can be used as an independent visible runtime surface without risking edge contamination or duplication. The API 24–32 and universal fallback draw only `LAYER_STATIC_COMPOSITE`.\n\n- Sunlight is `BAKED_IN_BASE`; its right-upper source and reflection remain in the static image. The optional API 33+ pass only multiplies existing pixels through the Master-derived green mask.\n- Caustics are `SHADER_INPUTS_PROVISIONAL`; the pass modulates existing lower-water luminance through the red mask and never draws a second caustic sheet.\n- Three droplets are `SHADER_INPUTS_PROVISIONAL`; fixed anchor/SDF refraction samples the same composite in place. The extracted RGBA and estimated normal maps are diagnostic only.\n- Grass candidates are `NOT_RUNTIME_ELIGIBLE`; broad leaves and the root-bed overlaps remain represented by the static composite. Skeleton SVGs are estimated guides, not recovered source vectors.\n- Base clean plate, standalone grass, droplet RGBA, caustic texture, and procedural sunlight field are not drawn over the static composite.\n\nThis is a complete static-first Android asset pack with optional provisional shader inputs. It does not claim device shader validation.\n""", encoding="utf-8")
    (PKG / "assets/android_runtime/android_asset_compatibility.md").write_text("""# Android asset compatibility\n\nThe single source of runtime layer status is [`layer_manifest_v1.json`](layer_manifest_v1.json). Its SHA-256 is pinned by `runtime_manifest.json`, `asset_manifest_v3.json`, and `lake_layer_pack_v3.json`.\n\n- API 24–32: draw only the 1440×3120 `LAYER_STATIC_COMPOSITE`; the unchanged Master is the decode/error fallback. No RuntimeShader, OpenGL, EGL, or `GLSurfaceView`.\n- API 33+: the same static composition remains the baseline. AGSL may apply low-amplitude in-place luminance modulation and fixed-anchor droplet refraction through the combined Master-derived mask. It draws no independent grass, water-drop, sunlight, or caustic overlay.\n- Any capability, compile, draw, or quality failure returns to `LAYER_STATIC_COMPOSITE`. Motion defaults off until API 33 visual and device validation.\n- All pixels and masks use one CenterCrop transform from the 1440×3120 Master coordinate system. Diagnostic maps are not loaded in the production scene.\n- The 360×780 RGBA optical atlas stores caustic weight in R, baked sunlight weight in G, and the union of the three original droplet region masks in B. Its alpha is opaque.\n""", encoding="utf-8")
    (PKG / "evidence/layer_pack_audit_report.md").write_text("""# Lake Glass V2 asset pack audit\n\nStatus: **ASSET_PACK_READY_FOR_ANDROID** (static-first). The complete, verified static composition is the only visible runtime surface. It is safe for API 24–32 and shader fallback. Provisional optical shader inputs do not create separate water-drop, sunlight, grass, or caustic surfaces.\n\nThe single authority is `assets/android_runtime/layer_manifest_v1.json`; `asset_manifest_v3.json`, `lake_layer_pack_v3.json`, and `runtime_manifest.json` pin its SHA-256. Each layer carries source/asset SHA-256, Master bounds, anchor, z-index, blend operation, alpha, separation status, runtime mode, motion policy and fallback.\n\n- Grass extraction alpha and root-bed overlap are not certified for runtime; use static composition only.\n- The sunlight clean plate remains blocked by baked reflection; the light stays in Base.\n- Droplet normal maps are estimated; shader refraction is constrained to the existing fixed droplet regions and samples the same composite.\n- Caustic modulation is non-tiled and multiplies existing luminance only.\n- Design status remains candidate, not frozen. No runtime device or dynamic visual acceptance is implied by the asset-pack status.\n""", encoding="utf-8")
    (PKG / "evidence/final_delivery_report.md").write_text("""# Lake Glass V2 asset pack delivery\n\n**Status: ASSET_PACK_READY_FOR_ANDROID.** The validated full-scene static composite is the only visible runtime surface. API 33+ shader masks operate in place and do not duplicate any baked element. Grass and standalone drop cutouts remain diagnostic-only; sunlight remains baked.\n\n- Master: 1440×3120, SHA-256 `d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12`.\n- Static reconstruction: 1440×3120; run `python3 scripts/verify_lake_glass_android_runtime_v1.py` for current exact metrics and hashes.\n- Runtime protocol: `assets/android_runtime/layer_manifest_v1.json` is authoritative; all consumer manifests pin it.\n- Design status remains `CANDIDATE_VISUAL_NOT_FROZEN`; no design freeze is inferred.\n- Android shader execution and physical device validation are separate gates and are not asserted by this asset status.\n""", encoding="utf-8")

    # Re-register all touched assets and the scripts that produce/verify them.
    asset_manifest = load_json(asset_manifest_path)
    for record in asset_manifest["derivedFiles"]:
        registered = ROOT / record["path"]
        if registered.is_file():
            record["sha256"] = sha(registered)
            record["sizeBytes"] = registered.stat().st_size
    def file_record(path: Path) -> dict:
        rec = {"path": (PKG / path.relative_to(PKG)).as_posix(), "sha256": sha(path), "sizeBytes": path.stat().st_size, "format": path.suffix[1:].upper(), "dimensionsPx": None, "mode": None}
        try:
            with Image.open(path) as im:
                rec.update({"dimensionsPx": list(im.size), "mode": im.mode, "colorSpace": "sRGB", "decodedMemoryBytesRgba8888": im.width * im.height * 4})
        except Exception:
            pass
        return rec
    extra_records = [atlas, canonical_path, runtime_path]
    failure_evidence = PKG / "evidence/true_layer_validation_failure_20261102.json"
    if (ROOT / failure_evidence).is_file(): extra_records.append(failure_evidence)
    for path in extra_records:
        rec = file_record(path)
        asset_manifest["derivedFiles"] = [x for x in asset_manifest["derivedFiles"] if x.get("path") != rec["path"]] + [rec]
    asset_manifest["toolFiles"] = []
    for script in ("scripts/build_lake_glass_true_layers_p1.py", "scripts/verify_lake_glass_true_layers_p1.py", "scripts/generate_lake_glass_preview_p1.py", "scripts/build_lake_glass_android_runtime_v1.py", "scripts/verify_lake_glass_android_runtime_v1.py"):
        script_path = ROOT / script
        if script_path.is_file(): asset_manifest["toolFiles"].append({"path": script, "sha256": sha(script_path)})
    # The manifest SHA itself is intentionally excluded to avoid a self-reference.
    dump_json(asset_manifest_path, asset_manifest)

    print(json.dumps({"manifest": rel(canonical_path), "manifestSha256": manifest_sha,
                      "runtimeManifestSha256": runtime_hash, "opticalMaskAtlas": rel(atlas),
                      "opticalMaskSha256": sha(atlas), "layers": len(canonical_layers)}, indent=2))


if __name__ == "__main__":
    main()
