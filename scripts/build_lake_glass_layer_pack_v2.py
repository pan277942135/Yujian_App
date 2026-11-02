#!/usr/bin/env python3
"""Build an auditable Lake Glass V2 asset pack from the supplied flattened master."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import shutil
from pathlib import Path
from statistics import median
from typing import Any

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont


MASTER_NAME = "YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png"
SOURCE_NAME = "宁静湖光玻璃水境背景.png"
SOURCE_MANIFEST_NAME = "YuJian_Lake_Glass_V2_Master_Candidate_02_manifest.json"
MASTER_SHA = "d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12"
SOURCE_SHA = "f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba"
SOURCE_MANIFEST_SHA = "623b978487ef294b7261c45e8ec1612ce372df61bbdca3859f5a9faa31af661b"
W, H = 1440, 3120


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def smoothstep(low: float, high: float, x: np.ndarray) -> np.ndarray:
    t = np.clip((x - low) / (high - low), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def png_gray(path: Path, array: np.ndarray) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(array, 0, 255).astype(np.uint8), mode="L").save(path, optimize=True)


def crop_preview(master: Image.Image, w: int, h: int, policy: str) -> tuple[Image.Image, dict[str, Any]]:
    scale = max(w / master.width, h / master.height)
    sw, sh = int(round(master.width * scale)), int(round(master.height * scale))
    scaled = master.resize((sw, sh), Image.Resampling.LANCZOS)
    excess_x, excess_y = max(0, sw - w), max(0, sh - h)
    if policy == "TopCenter":
        ox, oy = excess_x / 2, 0
    elif policy == "Center":
        ox, oy = excess_x / 2, excess_y / 2
    elif policy == "BottomCenter":
        ox, oy = excess_x / 2, excess_y
    else:
        raise ValueError(policy)
    left, top = int(round(ox)), int(round(oy))
    out = scaled.crop((left, top, left + w, top + h))
    source_crop = [
        round(left / scale, 2),
        round(top / scale, 2),
        round((left + w) / scale, 2),
        round((top + h) / scale, 2),
    ]
    return out, {
        "viewportDp": [w, h],
        "viewportAspect": round(w / h, 6),
        "sourceAspect": round(master.width / master.height, 6),
        "scale": round(scale, 8),
        "scaledPx": [sw, sh],
        "sourceCropLTRB": source_crop,
        "cropPolicy": policy,
        "croppedSourcePx": [round(excess_x / scale, 2), round(excess_y / scale, 2)],
    }


def build(args: argparse.Namespace) -> dict[str, Any]:
    root = Path(args.repo_root).resolve()
    inputs = Path(args.input_dir).resolve()
    out = root / "design/system/backgrounds/lake_glass_v2"
    src_master = inputs / MASTER_NAME
    src_source = inputs / SOURCE_NAME
    src_manifest = inputs / SOURCE_MANIFEST_NAME
    for p in (src_master, src_source, src_manifest):
        if not p.is_file():
            raise FileNotFoundError(p)
    actual = {p.name: sha256(p) for p in (src_master, src_source, src_manifest)}
    expected = {MASTER_NAME: MASTER_SHA, SOURCE_NAME: SOURCE_SHA, SOURCE_MANIFEST_NAME: SOURCE_MANIFEST_SHA}
    if actual != expected:
        raise ValueError(f"Input SHA mismatch: {json.dumps({'expected': expected, 'actual': actual}, ensure_ascii=False)}")

    master = Image.open(src_master).convert("RGB")
    source = Image.open(src_source).convert("RGB")
    if master.size != (W, H) or source.size != (852, 1846):
        raise ValueError(f"Unexpected dimensions: master={master.size}, source={source.size}")
    supplied_manifest = json.loads(src_manifest.read_text(encoding="utf-8"))

    paths = {
        "source": out / "assets/source_master",
        "base": out / "assets/base",
        "sunlight": out / "assets/sunlight",
        "caustics": out / "assets/caustics",
        "grass": out / "assets/grass",
        "droplets": out / "assets/droplets",
        "masks": out / "assets/masks",
        "vector": out / "assets/vector",
        "metadata": out / "assets/metadata",
        "reference": out / "assets/reference",
        "evidence": out / "evidence",
    }
    for p in paths.values():
        p.mkdir(parents=True, exist_ok=True)

    # Archive the user-provided bytes exactly. The static base intentionally retains all baked features.
    shutil.copyfile(src_master, paths["source"] / MASTER_NAME)
    shutil.copyfile(src_source, paths["source"] / SOURCE_NAME)
    shutil.copyfile(src_manifest, paths["source"] / SOURCE_MANIFEST_NAME)
    base_path = paths["base"] / "lake_base_static_master.png"
    shutil.copyfile(src_master, base_path)

    rgb = np.asarray(master, dtype=np.uint8)
    rf = rgb[:, :, 0].astype(np.float32)
    gf = rgb[:, :, 1].astype(np.float32)
    bf = rgb[:, :, 2].astype(np.float32)
    lum = 0.2126 * rf + 0.7152 * gf + 0.0722 * bf

    # These masks describe observed regions and potential modulation inputs; none repaint baked features.
    sun_roi = np.zeros((H, W), dtype=np.float32)
    sun_roi[:1550, :] = 1.0
    sunlight_luma = smoothstep(182.0, 248.0, lum) * smoothstep(-2.0, 34.0, rf - bf) * sun_roi
    sunlight_u8 = np.clip(sunlight_luma * 255.0, 0, 255).astype(np.uint8)
    png_gray(paths["sunlight"] / "sunlight_luma_analysis_mask.png", sunlight_u8)
    soft = Image.fromarray(sunlight_u8, mode="L").filter(ImageFilter.GaussianBlur(radius=24))
    soft.save(paths["sunlight"] / "sunlight_soft_analysis_mask.png", optimize=True)
    sunlight_regions = {
        "sourceFile": MASTER_NAME,
        "sourceSha256": MASTER_SHA,
        "sourcePixelBounds": [0, 0, W, 1550],
        "lightOriginApproxPx": [1370, 120],
        "dominantDirection": "upper-right toward lower-left / center",
        "analysisOnly": True,
        "visibleLayer": False,
        "method": "luminance and warm-channel threshold sampled from the flattened RGB master; not a recovered original light layer",
        "runtimeEligible": False,
        "reason": "Sunlight and water-surface refraction are composited into the same RGB pixels; overlay would duplicate light.",
    }
    write_json(paths["sunlight"] / "sunlight_anchor.json", sunlight_regions)

    # High-frequency lower-field luminance is an analysis mask only; texture remains baked in the base.
    lum_img = Image.fromarray(np.clip(lum, 0, 255).astype(np.uint8), mode="L")
    local = np.asarray(lum_img.filter(ImageFilter.GaussianBlur(radius=14)), dtype=np.float32)
    hi = np.maximum(lum - local, 0.0)
    y_ramp = np.clip((np.arange(H, dtype=np.float32) - 1920.0) / 520.0, 0.0, 1.0)[:, None]
    caustics = smoothstep(4.0, 30.0, hi) * y_ramp
    caustics_u8 = np.clip(caustics * 255.0, 0, 255).astype(np.uint8)
    png_gray(paths["caustics"] / "caustics_luma_analysis_mask.png", caustics_u8)
    region = np.zeros((H, W), dtype=np.uint8)
    region[1980:, :] = 255
    png_gray(paths["caustics"] / "caustics_region_mask.png", region)
    write_json(paths["caustics"] / "caustics_params.json", {
        "sourceFile": MASTER_NAME,
        "sourceSha256": MASTER_SHA,
        "analysisMask": "caustics_luma_analysis_mask.png",
        "regionMask": "caustics_region_mask.png",
        "pixelBounds": [0, 1980, W, H - 1980],
        "blendMode": "none; diagnostic mask only",
        "motionEligible": False,
        "runtimeEligible": False,
        "tile": None,
        "reason": "Observed lower caustics cannot be separated from the underlying water pixels; no seamless repeatable tile is recoverable from one flattened frame.",
    })

    # Conservative green-dominance extraction creates diagnostic candidates, not runtime cutouts.
    region_masks: dict[str, np.ndarray] = {}
    grass_specs = {
        "left": (0, 1810, 660, 1310),
        "right": (960, 2220, 480, 900),
    }
    grass_candidate_paths: dict[str, Path] = {}
    for side, (x, y, w, h) in grass_specs.items():
        x2, y2 = min(W, x + w), min(H, y + h)
        green_blue = gf[y:y2, x:x2] - bf[y:y2, x:x2]
        green_red = gf[y:y2, x:x2] - rf[y:y2, x:x2]
        alpha = smoothstep(7.0, 45.0, green_blue) * smoothstep(0.0, 42.0, green_red)
        alpha_img = Image.fromarray(np.clip(alpha * 255.0, 0, 255).astype(np.uint8), mode="L")
        alpha_img = alpha_img.filter(ImageFilter.GaussianBlur(radius=0.65))
        a = np.asarray(alpha_img, dtype=np.uint8)
        rgba = np.dstack((rgb[y:y2, x:x2, :], a))
        candidate = Image.fromarray(rgba, mode="RGBA")
        cpath = paths["grass"] / f"grass_{side}_candidate_diagnostic_only_rgba.png"
        candidate.save(cpath, optimize=True)
        grass_candidate_paths[side] = cpath
        full_mask = np.zeros((H, W), dtype=np.uint8)
        full_mask[y:y2, x:x2] = a
        region_masks[side] = full_mask

    # Per-droplet ellipses are inspection ROIs only; refracted background is deliberately not cut out.
    droplet_specs = [
        {"id": "droplet_01_left", "bbox": [18, 650, 104, 126], "center": [70, 713]},
        {"id": "droplet_02_upper_right", "bbox": [1250, 370, 132, 166], "center": [1316, 453]},
        {"id": "droplet_03_lower_right", "bbox": [1290, 1880, 145, 145], "center": [1362, 1952]},
    ]
    d_anchor_list = []
    for spec in droplet_specs:
        x, y, w, h = spec["bbox"]
        mask = Image.new("L", (W, H), 0)
        draw = ImageDraw.Draw(mask)
        pad_x, pad_y = round(w * 0.09), round(h * 0.08)
        draw.ellipse((x + pad_x, y + pad_y, x + w - pad_x, y + h - pad_y), fill=255)
        mask_path = paths["droplets"] / f"{spec['id']}_region_mask.png"
        mask.save(mask_path, optimize=True)
        d_anchor_list.append({
            **spec,
            "centerNormalized": [round(spec["center"][0] / W, 6), round(spec["center"][1] / H, 6)],
            "regionMask": mask_path.name,
            "rgbaAsset": None,
            "status": "BAKED_STATIC",
            "runtimeEligible": False,
            "reason": "Visible refraction contains the background itself; an RGBA cutout would carry contaminated pixels and cannot be composited faithfully over a different scene.",
        })
    write_json(paths["droplets"] / "droplets_anchors.json", {
        "canvasSize": [W, H],
        "coordinateSystem": "master pixel coordinates, origin top-left",
        "sourceFile": MASTER_NAME,
        "sourceSha256": MASTER_SHA,
        "droplets": d_anchor_list,
    })

    # Construct a central content-safe zone, plus region masks for visual audit.
    quiet = Image.new("L", (W, H), 0)
    qdraw = ImageDraw.Draw(quiet)
    qdraw.rounded_rectangle((170, 535, 1270, 2330), radius=220, fill=220)
    quiet = quiet.filter(ImageFilter.GaussianBlur(radius=95))
    quiet_path = paths["masks"] / "content_quiet_zone.png"
    png_gray(quiet_path, np.asarray(quiet, dtype=np.uint8))
    write_json(paths["masks"] / "ui_protection_zones.json", {
        "canvasSize": [W, H],
        "sourceFile": MASTER_NAME,
        "quietZoneMask": "content_quiet_zone.png",
        "regions": [
            {"id": "central_content_quiet_zone", "bbox": [170, 535, 1100, 1795], "purpose": "low-detail content protection reference; no UI is drawn into the background"},
            {"id": "top_brand_title", "bbox": [0, 0, W, 440], "purpose": "avoid high-contrast decorative overlays; sunlight is already baked"},
            {"id": "bottom_navigation", "bbox": [0, 2670, W, 450], "purpose": "keep future controls readable; no UI baked into image"},
        ],
    })

    # Editable, manually estimated motion guide paths. They are not traced alpha contours or production bones.
    left_svg = '''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="3120" viewBox="0 0 1440 3120">
  <!-- Diagnostic guide only. Curves are approximate visual anchors, not source-vector recovery. -->
  <g id="grass-left-motion-guide" fill="none" stroke="#27a879" stroke-width="8" stroke-linecap="round">
    <path id="blade-left-01" d="M 78 3070 C 50 2790 54 2220 8 1905"/>
    <path id="blade-left-02" d="M 150 3090 C 116 2810 94 2510 58 2285"/>
    <path id="blade-left-03" d="M 238 3100 C 276 2760 460 2500 590 2375"/>
    <path id="blade-left-04" d="M 330 3100 C 440 2820 570 2700 760 2635"/>
    <path id="blade-left-05" d="M 125 3120 C 158 2910 252 2760 424 2670"/>
  </g>
</svg>
'''
    right_svg = '''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1440" height="3120" viewBox="0 0 1440 3120">
  <!-- Diagnostic guide only. Curves are approximate visual anchors, not source-vector recovery. -->
  <g id="grass-right-motion-guide" fill="none" stroke="#27a879" stroke-width="8" stroke-linecap="round">
    <path id="blade-right-01" d="M 1380 3090 C 1350 2830 1275 2520 1135 2340"/>
    <path id="blade-right-02" d="M 1320 3100 C 1390 2830 1415 2660 1430 2495"/>
    <path id="blade-right-03" d="M 1220 3110 C 1160 2850 1085 2740 1035 2600"/>
    <path id="blade-right-04" d="M 1425 3040 C 1385 2850 1388 2700 1360 2580"/>
    <path id="blade-right-05" d="M 1260 3120 C 1290 2910 1350 2785 1435 2705"/>
  </g>
</svg>
'''
    (paths["vector"] / "grass_left_skeleton_guide.svg").write_text(left_svg, encoding="utf-8")
    (paths["vector"] / "grass_right_skeleton_guide.svg").write_text(right_svg, encoding="utf-8")
    write_json(paths["grass"] / "grass_motion_bones.json", {
        "canvasSize": [W, H],
        "status": "DIAGNOSTIC_GUIDE_ONLY",
        "runtimeEligible": False,
        "reason": "Guide curves are hand-estimated from the flattened visual; no verified per-leaf alpha or root segmentation exists.",
        "proposedMotion": {"periodSeconds": [16, 24], "tipMaxOffsetDp": 2, "rootPinned": True},
        "guides": [
            {"side": "left", "svg": "../vector/grass_left_skeleton_guide.svg", "approximate": True},
            {"side": "right", "svg": "../vector/grass_right_skeleton_guide.svg", "approximate": True},
        ],
    })

    # Annotated visual inventory and crop tests.
    font = ImageFont.load_default()
    annotated = master.copy().convert("RGBA")
    layer = Image.new("RGBA", annotated.size, (0, 0, 0, 0))
    ad = ImageDraw.Draw(layer)
    region_boxes = [
        ("D1", (18, 650, 122, 776), (214, 77, 50, 245)),
        ("D2", (1250, 370, 1382, 536), (214, 77, 50, 245)),
        ("D3", (1290, 1880, 1435, 2025), (214, 77, 50, 245)),
        ("SUN", (620, 0, 1439, 1050), (255, 190, 60, 230)),
        ("GRASS-L", (0, 1810, 660, 3120), (30, 190, 110, 230)),
        ("GRASS-R", (960, 2220, 1440, 3120), (30, 190, 110, 230)),
        ("CAUSTICS", (0, 1980, 1440, 3120), (80, 130, 240, 190)),
    ]
    for label, box, color in region_boxes:
        ad.rectangle(box, outline=color, width=5)
        ad.rectangle((box[0], box[1], box[0] + 112, box[1] + 38), fill=color)
        ad.text((box[0] + 5, box[1] + 5), label, fill=(0, 0, 0, 255), font=font)
    # Quiet-zone outline only; crop variants use magenta, orange and white.
    ad.rounded_rectangle((170, 535, 1270, 2330), radius=220, outline=(240, 65, 190, 220), width=4)
    annotated = Image.alpha_composite(annotated, layer).convert("RGB")
    annotated.save(paths["reference"] / "master_annotated.png", optimize=True)

    region_map = {
        "canvasSize": [W, H], "origin": "top-left", "units": "px",
        "regions": [
            {"id": "sunlight_upper_right", "bbox": [620, 0, 820, 1050], "anchorPx": [1370, 120], "anchorNormalized": [0.951389, 0.038462], "status": "BAKED_STATIC_WITH_ANALYSIS_MASK"},
            {"id": "quiet_content_zone", "bbox": [170, 535, 1100, 1795], "status": "REFERENCE_MASK"},
            {"id": "grass_left", "bbox": [0, 1810, 660, 1310], "anchorPx": [90, 3060], "anchorNormalized": [0.0625, 0.980769], "status": "BAKED_STATIC_WITH_DIAGNOSTIC_ALPHA_CANDIDATE"},
            {"id": "grass_right", "bbox": [960, 2220, 480, 900], "anchorPx": [1370, 3090], "anchorNormalized": [0.951389, 0.990385], "status": "BAKED_STATIC_WITH_DIAGNOSTIC_ALPHA_CANDIDATE"},
            *[{"id": d["id"], "bbox": d["bbox"], "anchorPx": d["center"], "anchorNormalized": d["centerNormalized"], "status": "BAKED_STATIC_REGION_MASK_ONLY"} for d in d_anchor_list],
            {"id": "lower_caustics", "bbox": [0, 1980, W, H - 1980], "status": "BAKED_STATIC_WITH_ANALYSIS_MASK"},
        ],
    }
    write_json(paths["reference"] / "master_region_map.json", region_map)

    previews_dir = paths["reference"]
    preview = master.copy()
    preview.thumbnail((512, 1109), Image.Resampling.LANCZOS)
    preview.save(previews_dir / "candidate02_preview.jpg", quality=92, optimize=True)

    # One visible layer: the exact master. This is deliberate and is recorded as a partial asset state.
    recomposed = Image.open(base_path).convert("RGB")
    recomposed.save(previews_dir / "recomposed_static_1440x3120.png", optimize=True)
    overlay = Image.blend(master, recomposed, 0.5)
    overlay.save(previews_dir / "master_vs_recomposed_overlay.png", optimize=True)
    diff = np.abs(np.asarray(master, dtype=np.int16) - np.asarray(recomposed, dtype=np.int16)).astype(np.uint8)
    diff_gray = np.max(diff, axis=2)
    diff_img = Image.fromarray(diff_gray, mode="L")
    diff_img.save(previews_dir / "master_vs_recomposed_difference.png", optimize=True)
    heat = np.zeros((H, W, 3), dtype=np.uint8)
    heat[:, :, 0] = np.clip(diff_gray.astype(np.float32) * 12.0, 0, 255).astype(np.uint8)
    heat[:, :, 1] = np.clip(diff_gray.astype(np.float32) * 2.0, 0, 255).astype(np.uint8)
    Image.fromarray(heat, mode="RGB").save(previews_dir / "master_vs_recomposed_heatmap.png", optimize=True)
    # The alpha extraction is diagnostic only; edge composites expose its limitations without feeding runtime.
    checker = Image.new("RGB", (320, 320), (218, 218, 218))
    cd = ImageDraw.Draw(checker)
    tile = 32
    for yy in range(0, 320, tile):
        for xx in range(0, 320, tile):
            if (xx // tile + yy // tile) % 2:
                cd.rectangle((xx, yy, xx + tile - 1, yy + tile - 1), fill=(150, 150, 150))
    for bg_name, bg_color in (("black", (0, 0, 0)), ("white", (255, 255, 255)), ("checker", None)):
        bg = Image.new("RGB", (W, H), bg_color) if bg_color else Image.new("RGB", (W, H), (220, 220, 220))
        if bg_color is None:
            bdraw = ImageDraw.Draw(bg)
            for yy in range(0, H, 96):
                for xx in range(0, W, 96):
                    if (xx // 96 + yy // 96) % 2:
                        bdraw.rectangle((xx, yy, xx + 95, yy + 95), fill=(145, 145, 145))
        for side, (x, y, w, h) in grass_specs.items():
            fg = Image.open(grass_candidate_paths[side]).convert("RGBA")
            bg.paste(fg, (x, y), fg.getchannel("A"))
        bg.thumbnail((720, 1560), Image.Resampling.LANCZOS)
        bg.save(previews_dir / f"grass_candidates_on_{bg_name}.jpg", quality=88, optimize=True)

    # Crop contact sheet: same source transform for all layers, no per-element movement.
    viewports = [(320, 480), (360, 640), (390, 844), (430, 932)]
    policies = ["TopCenter", "Center", "Center"]
    policy_labels = ["TopCenter", "CenterCrop", "Final candidate: Center"]
    sheet = Image.new("RGB", (3 * 340, len(viewports) * 535), (239, 244, 244))
    sd = ImageDraw.Draw(sheet)
    crop_records = []
    for row, (vw, vh) in enumerate(viewports):
        for col, policy in enumerate(policies):
            thumb, record = crop_preview(master, vw, vh, policy)
            record["status"] = "SIMULATED_NOT_ANDROID_CAPTURE"
            crop_records.append(record)
            thumb.thumbnail((300, 450), Image.Resampling.LANCZOS)
            x0, y0 = col * 340 + 20, row * 535 + 60
            sheet.paste(thumb, (x0, y0))
            sd.text((col * 340 + 20, row * 535 + 14), f"{vw}x{vh} dp | {policy_labels[col]}", fill=(20, 45, 50), font=font)
    sheet.save(previews_dir / "crop_matrix_contact_sheet.jpg", quality=91, optimize=True)
    crop_policy = {
        "sourceCanvasPx": [W, H],
        "fit": "CROP; same affine transform for every layer",
        "preliminaryCandidate": "CenterCrop / verticalAlignment=0.5",
        "status": "PRELIMINARY_SIMULATION_ONLY_NOT_DEVICE_VALIDATED",
        "reason": "CenterCrop distributes short-screen loss across the bright top and lower grass. TopCenter preserves more sunlight but removes more bottom grass; no crop can preserve both edges at 320x480 without changing scale or moving layers.",
        "records": crop_records,
    }
    write_json(paths["metadata"] / "crop_safe_zone.json", crop_policy)
    (previews_dir / "crop_policy_recommendation.md").write_text(
        "# Crop policy recommendation\n\n"
        "Use a single uniform CenterCrop transform as the preliminary candidate. At 390x844 and 430x932 the source ratio is nearly exact. At 360x640, approximately 560 source px are removed vertically; at 320x480, approximately 960 px are removed. CenterCrop splits that loss across top and bottom. TopCenter keeps more of the upper light field but cuts substantially more of the grass at the bottom. The 320x480 case cannot preserve both edge features; treat this as a product/device compatibility risk for the later Android phase. This sheet is a Pillow simulation, not an Android capture.\n",
        encoding="utf-8",
    )

    # Local 100% / 200% inspection regions.
    roi_defs = {
        "grass_left": (0, 1810, 660, 3120),
        "grass_right": (960, 2220, 1440, 3120),
        "droplet_01": (0, 620, 160, 810),
        "droplet_02": (1210, 330, 1435, 570),
        "droplet_03": (1260, 1840, 1440, 2070),
        "sunlight": (780, 0, 1440, 950),
    }
    detail_dir = previews_dir / "local_details"
    detail_dir.mkdir(parents=True, exist_ok=True)
    roi_contact = Image.new("RGB", (1200, 3 * 700), (238, 242, 242))
    rd = ImageDraw.Draw(roi_contact)
    for i, (name, box) in enumerate(roi_defs.items()):
        crop = master.crop(box)
        crop.save(detail_dir / f"{name}_100pct.png", optimize=True)
        crop.resize((crop.width * 2, crop.height * 2), Image.Resampling.NEAREST).save(detail_dir / f"{name}_200pct_nearest.png", optimize=True)
        thumb = crop.copy()
        thumb.thumbnail((560, 600), Image.Resampling.LANCZOS)
        x0, y0 = 10 + (i % 2) * 600, (i // 2) * 700 + 55
        roi_contact.paste(thumb, (x0, y0))
        rd.text((x0, y0 - 25), f"{name} / ROI preview", fill=(25, 50, 55), font=font)
    roi_contact.crop((0, 0, 1200, math.ceil(len(roi_defs) / 2) * 700)).save(previews_dir / "master_local_detail_review.jpg", quality=93, optimize=True)

    # Layer inspector: source, masks and candidate extractions; candidates are explicitly diagnostic.
    panels = []
    def panel(title: str, image: Image.Image) -> None:
        thumb = image.convert("RGB")
        thumb.thumbnail((230, 500), Image.Resampling.LANCZOS)
        card = Image.new("RGB", (250, 550), (248, 250, 250))
        card.paste(thumb, ((250 - thumb.width) // 2, 35))
        ImageDraw.Draw(card).text((8, 8), title, fill=(15, 45, 52), font=font)
        panels.append(card)
    panel("VISIBLE: STATIC MASTER", master)
    for title, path in [
        ("SUNLIGHT MASK", paths["sunlight"] / "sunlight_luma_analysis_mask.png"),
        ("CAUSTICS MASK", paths["caustics"] / "caustics_luma_analysis_mask.png"),
        ("GRASS LEFT CANDIDATE", grass_candidate_paths["left"]),
        ("GRASS RIGHT CANDIDATE", grass_candidate_paths["right"]),
    ]:
        panel(title, Image.open(path))
    inspector = Image.new("RGB", (250 * len(panels), 550), (230, 236, 236))
    for i, p in enumerate(panels):
        inspector.paste(p, (i * 250, 0))
    inspector.save(previews_dir / "layer_inspector_contact_sheet.jpg", quality=90, optimize=True)

    # Color samples are calculated from master pixel patches, not chosen as invented swatches.
    sample_points = {
        "surface_highlight": [1360, 90],
        "upper_water": [260, 300],
        "central_water": [720, 1450],
        "lower_water": [720, 2360],
        "left_grass": [85, 2840],
        "lower_caustic": [700, 2730],
    }
    samples = {}
    for name, (x, y) in sample_points.items():
        patch = rgb[max(0, y - 2):min(H, y + 3), max(0, x - 2):min(W, x + 3), :]
        med = [int(median(patch[:, :, c].flatten().tolist())) for c in range(3)]
        samples[name] = {"xyPx": [x, y], "medianRgb5x5": med, "hex": "#%02X%02X%02X" % tuple(med)}
    write_json(paths["reference"] / "master_color_samples.json", {
        "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "method": "median of 5x5 source RGB patches", "samples": samples,
    })
    (paths["reference"] / "master_visual_inventory.md").write_text(
        "# Master visual inventory\n\n"
        "The supplied 1440x3120 image is flattened RGB with no alpha and no editable native layer source. The top-right sunlight, three refractive droplets, grass groups, lower caustics and central low-detail water are visibly identifiable. Their optical interaction is already mixed into neighboring water pixels. The only defensible full fidelity visible layer is the original master-as-base.\n\n"
        "- Lake Base: exact whole-image static base.\n"
        "- Sunlight: warm luminance and soft masks are analysis-only; no separate light layer enters the visible stack.\n"
        "- Caustics: lower-field high-pass and region masks are analysis-only; no repeatable tile was recovered.\n"
        "- Grass left/right: RGB dominance masks produce diagnostic alpha candidates. Faded blade edges and water tint contaminate the cutouts; they are not runtime assets.\n"
        "- Droplets 01-03: individual region masks and anchors only. Refracted scene pixels cannot be separated into independent RGBA drops without inventing the background behind them.\n"
        "- Vector paths: hand-estimated motion guides for audit only; they are not pixel-accurate leaf skeletons and are disabled for runtime.\n\n"
        "The static recomposition is exact because it renders the original master once as the visible base. This does not constitute a recovered layered reconstruction. The truthful pack status is ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS.\n",
        encoding="utf-8",
    )

    feasibility_rows = [
        ["LAYER_BASE", "Lake Base", "D — BAKED_STATIC", "D — BAKED_STATIC", "whole supplied flattened master remains the visible base", "1.00", "false", "false"],
        ["LAYER_SUNLIGHT", "Sunlight", "D — BAKED_STATIC", "D — BAKED_STATIC", "light and surface refraction share RGB pixels; E-type analysis masks only", "0.98", "false", "false"],
        ["LAYER_CAUSTICS", "Water Caustics", "D — BAKED_STATIC", "D — BAKED_STATIC", "lower texture inseparable from water; E-type region/luma masks only", "0.96", "false", "false"],
        ["LAYER_GRASS_LEFT", "Aquatic Grass Left", "D — BAKED_STATIC", "D — BAKED_STATIC", "diagnostic alpha candidate contaminated at faded tips; not visible", "0.78", "false", "false"],
        ["LAYER_GRASS_RIGHT", "Aquatic Grass Right", "D — BAKED_STATIC", "D — BAKED_STATIC", "diagnostic alpha candidate contaminated at faded tips; not visible", "0.74", "false", "false"],
        ["LAYER_DROPLET_01", "Water Droplet 01", "D — BAKED_STATIC", "D — BAKED_STATIC", "refraction includes underlying scene; region mask only", "0.99", "false", "false"],
        ["LAYER_DROPLET_02", "Water Droplet 02", "D — BAKED_STATIC", "D — BAKED_STATIC", "refraction includes underlying scene; region mask only", "0.99", "false", "false"],
        ["LAYER_DROPLET_03", "Water Droplet 03", "D — BAKED_STATIC", "D — BAKED_STATIC", "refraction includes underlying scene; region mask only", "0.99", "false", "false"],
        ["LAYER_VECTOR_GRASS", "Grass motion skeleton guides", "E — PROCEDURAL_MASK", "DIAGNOSTIC_GUIDE_ONLY", "manually estimated path guides, not native vector recovery", "0.66", "false", "false"],
        ["LAYER_SCENE_MASKS", "Quiet/crop/protection masks", "E — PROCEDURAL_MASK", "ANALYSIS_ONLY", "derived coordinate masks; no scene repaint", "0.91", "false", "false"],
    ]
    with (paths["metadata"] / "layer_feasibility_matrix.csv").open("w", encoding="utf-8", newline="") as f:
        wcsv = csv.writer(f)
        wcsv.writerow(["layerId", "semantic", "sourceClassification", "runtimeDecision", "reason", "confidence", "motionEligible", "cleanPlateRequired"])
        wcsv.writerows(feasibility_rows)
    (paths["metadata"] / "layer_feasibility_review.md").write_text(
        "# Layer feasibility review\n\n"
        "## Decision\n\n"
        "No independent visible feature layer can be recovered from the supplied flattened RGB image with defensible alpha and clean-plate fidelity. Keep the source master intact as the only visible layer. The masks, cutout candidates and SVG guides in this package are diagnostic/procedural references only. Do not enable them in a visible stack until a native layered source or reviewed manual extraction plus clean plate is provided.\n\n"
        "## Candidate limitations\n\n"
        "The grass RGBA candidates use green-channel dominance. Their alpha is an estimate and mixes the edge water tint; black/white/checker previews expose these risks. They must not be used as runtime overlays. The droplet region masks locate the original drops but are not drop cutouts.\n",
        encoding="utf-8",
    )

    # Layer and coordinate manifests.
    layer_pack = {
        "schemaVersion": "2.1",
        "assetId": "Lake_Glass_V2_Master_Candidate_02_Layer_Pack",
        "status": "ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS",
        "master": {"file": f"assets/source_master/{MASTER_NAME}", "sha256": MASTER_SHA, "sizePx": [W, H], "colorSpace": "sRGB RGB8"},
        "coordinateSystem": {"width": W, "height": H, "origin": "top-left", "xDirection": "right", "yDirection": "down"},
        "visibleComposite": ["LAYER_BASE"],
        "layers": [
            {"layerId": "LAYER_BASE", "semantic": "Lake Base + all baked optical details", "status": "VISIBLE_STATIC_MASTER", "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "assetFile": "assets/base/lake_base_static_master.png", "assetSha256": sha256(base_path), "format": "PNG RGB", "canvasSize": [W, H], "pixelBounds": [0, 0, W, H], "anchorPx": [0, 0], "anchorNormalized": [0.0, 0.0], "zIndex": 0, "blendMode": "SRC_OVER", "alpha": 1.0, "scalePolicy": "FIT_CROP_SINGLE_SCENE_TRANSFORM", "motionEligible": False, "motionProfile": None, "reconstruction": "byte-identical copy of supplied flattened master; no feature duplication"},
            {"layerId": "LAYER_SUNLIGHT", "semantic": "Sunlight analysis masks", "status": "BAKED_STATIC_ANALYSIS_ONLY", "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "assetFile": None, "analysisFiles": ["assets/sunlight/sunlight_luma_analysis_mask.png", "assets/sunlight/sunlight_soft_analysis_mask.png"], "pixelBounds": [0, 0, W, 1550], "anchorPx": [1370, 120], "anchorNormalized": [0.951389, 0.038462], "blendMode": "NONE_ANALYSIS_ONLY", "alpha": 0.0, "visible": False, "motionEligible": False},
            {"layerId": "LAYER_CAUSTICS", "semantic": "Lower caustics analysis mask", "status": "BAKED_STATIC_ANALYSIS_ONLY", "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "assetFile": None, "analysisFiles": ["assets/caustics/caustics_luma_analysis_mask.png", "assets/caustics/caustics_region_mask.png"], "pixelBounds": [0, 1980, W, H - 1980], "anchorPx": [W / 2, H - 1], "anchorNormalized": [0.5, 0.999679], "blendMode": "NONE_ANALYSIS_ONLY", "alpha": 0.0, "visible": False, "motionEligible": False},
            {"layerId": "LAYER_GRASS_LEFT", "semantic": "Aquatic grass left diagnostic extraction", "status": "BAKED_STATIC_CANDIDATE_NOT_FOR_RUNTIME", "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "assetFile": None, "diagnosticFile": "assets/grass/grass_left_candidate_diagnostic_only_rgba.png", "format": "PNG RGBA diagnostic", "canvasSize": [660, 1310], "pixelBounds": [0, 1810, 660, 1310], "anchorPx": [90, 3060], "anchorNormalized": [0.0625, 0.980769], "blendMode": "NONE_DIAGNOSTIC_ONLY", "alpha": 0.0, "motionEligible": False},
            {"layerId": "LAYER_GRASS_RIGHT", "semantic": "Aquatic grass right diagnostic extraction", "status": "BAKED_STATIC_CANDIDATE_NOT_FOR_RUNTIME", "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "assetFile": None, "diagnosticFile": "assets/grass/grass_right_candidate_diagnostic_only_rgba.png", "format": "PNG RGBA diagnostic", "canvasSize": [480, 900], "pixelBounds": [960, 2220, 480, 900], "anchorPx": [1370, 3090], "anchorNormalized": [0.951389, 0.990385], "blendMode": "NONE_DIAGNOSTIC_ONLY", "alpha": 0.0, "motionEligible": False},
            *[{"layerId": f"LAYER_{d['id'].upper()}", "semantic": "Water droplet region mask only", "status": "BAKED_STATIC", "sourceFile": MASTER_NAME, "sourceSha256": MASTER_SHA, "assetFile": None, "analysisFile": f"assets/droplets/{d['id']}_region_mask.png", "pixelBounds": d["bbox"], "anchorPx": d["center"], "anchorNormalized": d["centerNormalized"], "blendMode": "NONE_ANALYSIS_ONLY", "alpha": 0.0, "motionEligible": False} for d in d_anchor_list],
        ],
        "compositing": {"workingSpace": "sRGB", "premultiplication": "not applicable to visible RGB base", "visibleLayerOrder": ["LAYER_BASE"], "diagnosticAssetsExcluded": True, "noDoubleCompositing": True},
    }
    write_json(paths["metadata"] / "lake_layer_pack_v2.json", layer_pack)
    anchors = {
        "canvasSize": [W, H], "coordinateSystem": "master pixel; normalized = x/1440,y/3120", "globalTransform": {"fit": "CROP", "alignment": "CENTER_PRELIMINARY", "allLayersShareTransform": True},
        "anchors": [{"layerId": a["layerId"], "pixel": a["anchorPx"], "normalized": a["anchorNormalized"], "status": a["status"]} for a in layer_pack["layers"]],
    }
    write_json(paths["metadata"] / "lake_anchors_v2.json", anchors)
    write_json(paths["metadata"] / "lake_material_tokens_v2.json", {
        "source": "Candidate02 source pixel samples; tokens are reference values, not recoloring instructions",
        "tokens": {"mistWhite": "#EAF7F7", "lakePale": "#C6EAE5", "lakeMid": "#8CCFC4", "lakeDeep": "#65BFB5", "textDeepLake": "#153B50", "morningGold": "#D8B36A"},
        "measuredSamples": samples,
        "recoloringAllowed": False,
    })
    write_json(paths["metadata"] / "lake_glass_motion_profiles_v2.json", {
        "status": "FEASIBILITY_ONLY_DISABLED",
        "allVisibleMotionEnabled": False,
        "reason": "Current source is flattened; independent visible grass, sunlight, caustics and droplet layers are not fidelity-safe.",
        "profiles": {
            "aquatic_grass": {"periodSeconds": [16, 24], "tipMaxOffsetDp": 2, "rootPinned": True, "enabled": False},
            "sunlight": {"periodSeconds": [24, 36], "direction": "upper-right to lower-left/center", "enabled": False},
            "water_caustics": {"periodSeconds": [24, 36], "localOnly": True, "tile": None, "enabled": False},
            "water_droplets": {"positionFixed": True, "highlightOnly": True, "enabled": False},
        },
    })

    # Input and fidelity reports.
    integrity = {
        "taskId": "YUJIAN_LAKE_GLASS_V2_MASTER_TO_LAYERED_ASSET_PACK_P0",
        "expected": expected, "actual": actual, "match": True,
        "masterDimensions": list(master.size), "sourceDimensions": list(source.size),
        "masterMode": Image.open(src_master).mode, "sourceMode": Image.open(src_source).mode,
        "suppliedManifest": supplied_manifest,
        "status": "PASS_INPUTS_VERIFIED",
    }
    write_json(paths["evidence"] / "source_integrity_report.json", integrity)
    m1 = np.asarray(master, dtype=np.uint8)
    m2 = np.asarray(recomposed, dtype=np.uint8)
    same = bool(np.array_equal(m1, m2))
    if same:
        mae = rmse = 0.0
        psnr: float | str = "Infinity"
        global_ssim = 1.0
        changed_ratio = 0.0
    else:
        delta = m1.astype(np.float64) - m2.astype(np.float64)
        mae = float(np.mean(np.abs(delta)))
        mse = float(np.mean(delta * delta))
        rmse = float(math.sqrt(mse))
        psnr = float(20 * math.log10(255 / rmse)) if rmse else "Infinity"
        a, b = m1.astype(np.float64), m2.astype(np.float64)
        mu1, mu2 = a.mean(), b.mean()
        var1, var2 = a.var(), b.var()
        cov = ((a - mu1) * (b - mu2)).mean()
        c1, c2 = (0.01 * 255) ** 2, (0.03 * 255) ** 2
        global_ssim = float(((2 * mu1 * mu2 + c1) * (2 * cov + c2)) / ((mu1**2 + mu2**2 + c1) * (var1 + var2 + c2)))
        changed_ratio = float(np.count_nonzero(np.any(m1 != m2, axis=2)) / (W * H))
    metrics = {
        "sourceSha256": MASTER_SHA,
        "recomposedSha256": sha256(previews_dir / "recomposed_static_1440x3120.png"),
        "sizePx": [W, H], "pixelIdentical": same, "maeRgb": mae, "rmseRgb": rmse,
        "psnrDb": psnr, "ssim": global_ssim, "ssimMethod": "identity shortcut after exact pixel comparison" if same else "global SSIM",
        "changedPixelRatio": changed_ratio,
        "interpretation": "Exact because only the byte-identical master base is visible; this is not evidence that individual optical features were separated.",
        "status": "PASS_STATIC_BASE_RECOMPOSITION_PARTIAL_LAYER_STATE",
    }
    write_json(paths["evidence"] / "master_reconstruction_metrics.json", metrics)

    # Fully machine-readable layer manifest; self-entry is deliberately excluded.
    manifest_entries = []
    for p in sorted(out.rglob("*")):
        if p.is_file() and p.name != "asset_manifest_v2.json":
            try:
                im = Image.open(p)
                dims, mode = list(im.size), im.mode
            except Exception:
                dims, mode = None, None
            manifest_entries.append({
                "path": p.relative_to(root).as_posix(), "sha256": sha256(p), "sizeBytes": p.stat().st_size,
                "format": p.suffix.lstrip(".").upper() or "TEXT", "dimensionsPx": dims, "mode": mode,
                "originPermission": "PENDING_REVIEW_FOR_EXTERNAL_REUSE" if p.name in {MASTER_NAME, SOURCE_NAME} else "DERIVED_FROM_USER_SUPPLIED_MASTER_OR_PROCEDURAL_ANALYSIS",
            })
    asset_manifest = {
        "schemaVersion": "2.1", "assetPackId": "LakeGlassV2Candidate02LayerPack", "status": "ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS",
        "master": {"path": f"design/system/backgrounds/lake_glass_v2/assets/source_master/{MASTER_NAME}", "sha256": MASTER_SHA, "dimensionsPx": [W, H], "format": "PNG RGB8 sRGB", "candidateStatus": "CANDIDATE_VISUAL_NOT_FROZEN"},
        "source": {"path": f"design/system/backgrounds/lake_glass_v2/assets/source_master/{SOURCE_NAME}", "sha256": SOURCE_SHA, "dimensionsPx": [852, 1846]},
        "sourceManifest": {"path": f"design/system/backgrounds/lake_glass_v2/assets/source_master/{SOURCE_MANIFEST_NAME}", "sha256": SOURCE_MANIFEST_SHA},
        "layerSourceType": {"visibleBase": "byte-identical flattened raster", "analysisMasks": "procedural analysis from flattened raster", "grassCandidates": "color-derived diagnostic alpha; not runtime validated", "vectorGuides": "manual estimated SVG paths; not original vector source", "droplets": "baked static with region masks only"},
        "originPermission": "PENDING_REVIEW; this pack makes no independent rights claim beyond user-provided inputs",
        "derivedFiles": manifest_entries,
        "manifestSelfHashExcluded": True,
    }
    write_json(paths["metadata"] / "asset_manifest_v2.json", asset_manifest)

    # Scope record for this PR: files are additions only under the V2 pack and script locations.
    (paths["evidence"] / "task_scope_audit.json").write_text(json.dumps({
        "allowedNewPrefixes": ["design/system/backgrounds/lake_glass_v2/", "scripts/"],
        "protectedPaths": ["design/system/backgrounds/morning_lake_v1/", "app/src/main/java/com/yujian/ai/ui/home/EmptyHomeSceneRenderer.kt", "app/src/main/assets/empty_home_runtime_v2/"],
        "intendedChangeType": "additions_only",
        "statement": "The GitHub PR file list is the authoritative check that no protected path is modified.",
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # Final audit report.
    report = f'''# Lake Glass V2 Layer Pack Audit

Task: YUJIAN_LAKE_GLASS_V2_MASTER_TO_LAYERED_ASSET_PACK_P0  
Status: **ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS**  
Input master SHA-256: `{MASTER_SHA}`

## P0-1 — Inputs

PASS. The three supplied binary inputs match the contract SHA values and dimensions. Original bytes are archived under `assets/source_master/`. The source Manifest truthfully identifies Candidate 02 as an upscale/refinement from 852×1846, not a native layered source.

## P0-2 — Visual inventory

PASS. The annotated master, region map, color samples, inventory and feasibility matrix locate the upper-right light, three drops, both grass groups, lower caustics and central quiet area.

## P0-3 — Layer generation

PARTIAL BY SOURCE LIMITATION. A static master base, sunlight masks, caustics masks, separate droplet region masks, two diagnostic grass alpha candidates and editable estimated SVG guide paths were generated. The alpha candidates and guides are not runtime layers. No transparent droplet RGBA or clean plate is claimed because each droplet refracts underlying water and the flattened source contains no hidden pixels.

## P0-4 — Coordinates / manifest

PASS. Layer pack, anchors, material tokens, motion profiles, crop metadata and per-file SHA/size/dimensions are recorded. Visible stack contains only the exact master base; analysis layers have alpha 0 / visible false.

## P0-5 — Static reconstruction

PASS FOR STATIC BASE ONLY. The recomposition is pixel-identical (MAE 0, RMSE 0, PSNR Infinity, SSIM 1.0). This is because the original Master remains intact as the sole visible layer. It does not prove independent layer reconstruction. Difference image is all black and the overlay is identical.

## P0-6 — Crop preview

PASS FOR SIMULATION. CenterCrop, TopCenter and preliminary Center final candidate are shown at 320×480, 360×640, 390×844 and 430×932 dp. The 320×480 viewport cannot retain both the upper light field and lower grass under crop-only scaling. Center is the balanced preliminary recommendation; Android rendering/device validation remains pending.

## P0-7 — Automated validation

See `evidence/validation_report.json`. The package integrity and required files are checked by `scripts/verify_lake_glass_layer_pack_v2.py`.

## P0-8 — PR handoff

This pack is submitted as **ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS**, not ASSET_PACK_READY_FOR_ANDROID. Before Android runtime use of independent grass, drops, sunlight or caustics, obtain a native layered source or perform human-reviewed extraction with clean plate and edge/fidelity approval. Empty Home and Morning Lake V1 are protected and outside the change set.
'''
    (paths["evidence"] / "layer_pack_audit_report.md").write_text(report, encoding="utf-8")

    # Refresh the inventory after reports have been written. The manifest excludes only itself.
    manifest_entries = []
    for p in sorted(out.rglob("*")):
        if p.is_file() and p.name != "asset_manifest_v2.json":
            try:
                im = Image.open(p)
                dims, mode = list(im.size), im.mode
            except Exception:
                dims, mode = None, None
            manifest_entries.append({
                "path": p.relative_to(root).as_posix(), "sha256": sha256(p), "sizeBytes": p.stat().st_size,
                "format": p.suffix.lstrip(".").upper() or "TEXT", "dimensionsPx": dims, "mode": mode,
                "originPermission": "PENDING_REVIEW_FOR_EXTERNAL_REUSE" if p.name in {MASTER_NAME, SOURCE_NAME} else "DERIVED_FROM_USER_SUPPLIED_MASTER_OR_PROCEDURAL_ANALYSIS",
            })
    asset_manifest["derivedFiles"] = manifest_entries
    write_json(paths["metadata"] / "asset_manifest_v2.json", asset_manifest)

    return {"root": str(root), "output": str(out), "metrics": metrics, "integrity": integrity, "fileCountBeforeValidator": len(manifest_entries) + 1}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--input-dir", default="lake_glass_v2_inputs")
    args = parser.parse_args()
    result = build(args)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
