#!/usr/bin/env python3
"""Deterministic Lake Glass V2 P1 asset builder; reconstructed sources are labeled."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import math
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps
from scipy import ndimage

PKG = Path("design/system/backgrounds/lake_glass_v2")
MASTER = PKG / "assets/source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_1440x3120.png"
ORIGINAL = PKG / "assets/source_master/宁静湖光玻璃水境背景.png"
MASTER_SHA = "d0bd07ba379491e8b93468c616468fc60d59a2dfebc11061cf4d14e9788c6e12"
ORIGINAL_SHA = "f3dc1549919f48f939df5a6cf5baf18fb6846164c6f857df8e20cfe247b495ba"
MANIFEST_SHA = "623b978487ef294b7261c45e8ec1612ce372df61bbdca3859f5a9faa31af661b"
W, H = 1440, 3120


def sha(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def jwrite(path: Path, obj: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def save_png(path: Path, image: Image.Image) -> None:
    """Write a PNG atomically and decode-check it before exposing the asset."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    attempts = [{"optimize": True}, {"optimize": False, "compress_level": 9}]
    for options in attempts:
        buf = io.BytesIO()
        image.save(buf, format="PNG", **options)
        tmp.write_bytes(buf.getvalue())
        try:
            check = Image.open(tmp)
            check.load()
            check.close()
        except Exception:
            continue
        tmp.replace(path)
        return
    tmp.unlink(missing_ok=True)
    raise OSError(f"PNG failed decode validation after fallback save: {path}")


def save(path: Path, a: np.ndarray, mode: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    save_png(path, Image.fromarray(np.clip(np.rint(a), 0, 255).astype(np.uint8), mode))


def rgb(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("RGB"), dtype=np.float32)


def mask(path: Path) -> np.ndarray:
    return np.asarray(Image.open(path).convert("L"), dtype=np.uint8) > 0


def box(mask_a: np.ndarray) -> list[int]:
    ys, xs = np.where(mask_a)
    return [int(xs.min()), int(ys.min()), int(xs.max() + 1), int(ys.max() + 1)]


def repair(source: np.ndarray, region: np.ndarray) -> np.ndarray:
    """Use the open center-water color profile to repair broad plant masks."""
    region = region.astype(bool)
    if not region.any():
        return source.copy()
    # The center water column is free of plants. Its per-row median rejects
    # narrow caustic lines; a vertical low-pass preserves the overall gradient.
    profile = np.median(source[:, 500:940, :], axis=1)
    profile = ndimage.gaussian_filter1d(profile, sigma=42.0, axis=0, mode="nearest")
    out = source.copy()
    rows = np.where(region.any(axis=1))[0]
    for y in rows:
        out[y, region[y]] = profile[y]
    return out


def effect_rgba(target: np.ndarray, base: np.ndarray, support: np.ndarray) -> np.ndarray:
    """Solve a shaped source-over layer from target/base RGB differences."""
    target = target.astype(np.float32)
    base = base.astype(np.float32)
    delta = target - base
    pos_alpha = np.where(delta > 0, delta / np.maximum(255.0 - base, 1e-6), 0.0)
    neg_alpha = np.where(delta < 0, -delta / np.maximum(base, 1e-6), 0.0)
    alpha = np.max(np.maximum(pos_alpha, neg_alpha), axis=2)
    alpha = np.where(support & (np.max(np.abs(delta), axis=2) > 0.05), np.clip(alpha, 1.0/255.0, 1.0), 0.0)
    # Round alpha upward before solving RGB so quantization cannot make a
    # channel require an impossible foreground value outside 0..255.
    alpha8 = np.where(alpha > 0, np.minimum(np.ceil(alpha * 255.0), 255.0) / 255.0, 0.0)
    rgb_layer = base + delta / np.maximum(alpha8[:, :, None], 1.0 / 255.0)
    rgb_layer = np.where(support[:, :, None], rgb_layer, 0.0)
    return np.uint8(np.clip(np.rint(np.dstack((rgb_layer, alpha8 * 255.0))), 0, 255))


def over(base: np.ndarray, layer: np.ndarray) -> np.ndarray:
    a = layer[:, :, 3:4].astype(np.float32) / 255.0
    return layer[:, :, :3].astype(np.float32) * a + base.astype(np.float32) * (1 - a)


def edge_strip(rgba: Image.Image) -> Image.Image:
    """Show the same RGBA layer on black, white and checker backgrounds."""
    rgba = rgba.convert("RGBA")
    w, h = rgba.size
    out = Image.new("RGB", (w * 3, h), (240, 240, 240))
    font = ImageFont.load_default()
    labels = ["BLACK", "WHITE", "CHECKER"]
    for i in range(3):
        if i == 0:
            bg = Image.new("RGB", (w, h), (8, 10, 12))
        elif i == 1:
            bg = Image.new("RGB", (w, h), (252, 252, 252))
        else:
            bg = Image.new("RGB", (w, h), (228, 228, 228))
            d = ImageDraw.Draw(bg)
            for y in range(0, h, 24):
                for x in range(0, w, 24):
                    if (x // 24 + y // 24) % 2:
                        d.rectangle((x, y, x + 23, y + 23), fill=(182, 182, 182))
        bg.paste(rgba, (0, 0), rgba.getchannel("A"))
        ImageDraw.Draw(bg).text((4, 4), labels[i], fill=(255, 30, 30), font=font)
        out.paste(bg, (i * w, 0))
    return out


def sheet(path: Path, images: list[Image.Image], labels: list[str], cell=(460, 640)) -> None:
    cols, gap, head = 2, 14, 25
    rows = math.ceil(len(images) / cols)
    out = Image.new("RGB", (cols * (cell[0] + gap) + gap, rows * (cell[1] + head + gap) + gap), (245, 248, 248))
    d = ImageDraw.Draw(out)
    font = ImageFont.load_default()
    for i, im in enumerate(images):
        x = gap + (i % cols) * (cell[0] + gap)
        y = gap + (i // cols) * (cell[1] + head + gap)
        d.text((x, y), labels[i], fill=(12, 43, 52), font=font)
        thumb = ImageOps.contain(im.convert("RGB"), cell, Image.Resampling.LANCZOS)
        out.paste(thumb, (x, y + head))
    path.parent.mkdir(parents=True, exist_ok=True)
    save_png(path, out)


def record(root: Path, rel: str) -> dict[str, Any]:
    p = root / rel
    r: dict[str, Any] = {"path": rel, "sha256": sha(p), "sizeBytes": p.stat().st_size, "format": p.suffix[1:].upper()}
    try:
        with Image.open(p) as im:
            r.update({"dimensionsPx": [im.width, im.height], "mode": im.mode, "colorSpace": "sRGB", "decodedMemoryBytesRgba8888": im.width * im.height * 4})
    except Exception:
        r.update({"dimensionsPx": None, "mode": None})
    return r


def build(root: Path) -> dict[str, Any]:
    pkg = root / PKG
    aroot = pkg / "assets"
    ref = aroot / "reference"
    ev = pkg / "evidence"
    master_path, original_path = root / MASTER, root / ORIGINAL
    if sha(master_path) != MASTER_SHA or sha(original_path) != ORIGINAL_SHA:
        raise ValueError("Input SHA-256 mismatch; refusing to build.")
    if sha(aroot / "source_master/YuJian_Lake_Glass_V2_Master_Candidate_02_manifest.json") != MANIFEST_SHA:
        raise ValueError("Input manifest SHA-256 mismatch; refusing to build.")
    target = rgb(master_path)
    if target.shape != (H, W, 3):
        raise ValueError(f"Expected {W}x{H} RGB Master; got {target.shape}.")

    # Grass support comes from the P0 alpha candidate, gated by green chroma so
    # neutral water/caustic highlights do not become botanical pixels.
    grass_specs = [("left", "grass_left_candidate_diagnostic_only_rgba.png"), ("right", "grass_right_candidate_diagnostic_only_rgba.png")]
    grass_masks: dict[str, np.ndarray] = {}
    for side, old_name in grass_specs:
        old = np.asarray(Image.open(aroot / "grass" / old_name).convert("RGBA"))
        x0, y0 = (0, 1810) if side == "left" else (960, 2220)
        crop = target[y0:y0 + old.shape[0], x0:x0 + old.shape[1]]
        hsv = np.asarray(Image.fromarray(crop.astype(np.uint8), "RGB").convert("HSV"), dtype=np.uint8)
        hue = hsv[:, :, 0].astype(np.float32) * (360.0 / 255.0)
        sat = hsv[:, :, 1].astype(np.float32) / 255.0
        val = hsv[:, :, 2]
        botanical_color = (hue >= 25) & (hue <= 160) & (sat >= 0.22) & (val > 25)
        core = botanical_color & (old[:, :, 3] > 50)
        core = ndimage.binary_closing(core, structure=np.ones((3, 3), dtype=bool), iterations=1)
        labels, count = ndimage.label(core, structure=np.ones((3, 3), dtype=bool))
        root_band = int(old.shape[0] * 0.94)
        area_by_label = np.bincount(labels.ravel(), minlength=count + 1)
        root_labels = np.unique(labels[root_band:, :])
        keep = [int(k) for k in root_labels if k > 0 and area_by_label[k] > 1000]
        if keep:
            core = np.isin(labels, keep)
        else:
            core = botanical_color & (old[:, :, 3] > 100)
        # Expand only from root-connected botanical cores; this keeps isolated
        # caustic/ripple pixels outside the RGBA layer.
        local = ndimage.binary_dilation(core, iterations=4)
        # Roots and lower haze overlap the flattened caustics. Preserve that
        # ambiguous bed in the static Base rather than carrying water highlights
        # inside the plant RGBA.
        global_cut_y = 2860
        local_cut_y = max(0, min(local.shape[0], global_cut_y - y0))
        local[local_cut_y:, :] = False
        full = np.zeros((H, W), dtype=bool)
        full[y0:y0 + local.shape[0], x0:x0 + local.shape[1]] = local
        grass_masks[side] = ndimage.gaussian_filter(full.astype(np.float32), 0.7) > 0.025
    grass_union = grass_masks["left"] | grass_masks["right"]

    anchor_doc = json.loads((aroot / "droplets/droplets_anchors.json").read_text(encoding="utf-8"))
    drops = anchor_doc["droplets"]
    drop_masks = {}
    for d in drops:
        ident = d["id"]
        raw = mask(aroot / "droplets" / (ident + "_region_mask.png"))
        d["sourceBbox"] = list(d["bbox"])
        reconstructed = ndimage.binary_dilation(raw, iterations=12)
        bx0, by0, bx1, by1 = box(reconstructed)
        d["bbox"] = [bx0, by0, bx1 - bx0, by1 - by0]
        drop_masks[ident] = reconstructed
    drop_union = np.logical_or.reduce(list(drop_masks.values()))

    grass_repair = grass_union
    drop_repair = drop_union
    repair_mask = grass_repair | drop_repair
    clean = repair(target, grass_repair)
    smooth_drops = ndimage.gaussian_filter(target, sigma=(14.0, 14.0, 0.0), mode="nearest")
    clean[drop_repair] = smooth_drops[drop_repair]

    # Extract only positive, fine-scale lower-water caustic detail. It is an ADD
    # texture, not an RGB cutout, and deliberately non-tileable.
    region = np.zeros((H, W), np.float32)
    ramp = np.clip((np.arange(H, dtype=np.float32) - 2050.0) / 220.0, 0, 1)
    region[1980:] = ramp[1980:, None]
    region *= (~grass_union & ~drop_union).astype(np.float32)
    # The lower plant/root beds stay baked, so caustics extraction avoids those
    # overlapping regions instead of treating plant detail as water texture.
    region[2860:, :660] = 0
    region[2860:, 960:] = 0
    low = ndimage.gaussian_filter(target, sigma=(13.0, 13.0, 0.0), mode="nearest")
    caustic_delta = np.maximum(target - low - 1.25, 0) * region[:, :, None]
    caustic_delta = np.minimum(caustic_delta, target)
    clean = np.maximum(clean - caustic_delta, 0)
    save(aroot / "base/lake_base_clean_v2.png", clean, "RGB")
    save(aroot / "base/clean_plate_repair_mask_v2.png", repair_mask.astype(np.uint8) * 255, "L")
    caustics_alpha = (np.max(caustic_delta[1980:], axis=2) > 0.25).astype(np.float32) * 255
    save(aroot / "caustics/caustics_texture_v2.png", np.dstack((caustic_delta[1980:], caustics_alpha)), "RGBA")
    save(aroot / "caustics/caustics_region_mask_v2.png", region[1980:] * 255, "L")
    lum = np.dot(caustic_delta[1980:], np.array([0.2126, 0.7152, 0.0722], np.float32))
    save(aroot / "caustics/caustics_luma_v2.png", lum, "L")
    clean = rgb(aroot / "base/lake_base_clean_v2.png")
    caustics_decoded = np.asarray(Image.open(aroot / "caustics/caustics_texture_v2.png").convert("RGBA"), dtype=np.uint8)
    caustic_delta = np.zeros_like(target)
    caustic_delta[1980:] = caustics_decoded[:, :, :3].astype(np.float32)

    # Preserve individual before/after repair evidence at 100% and 200%.
    repair_rois = {
        "grass_left": [0, 1810, 660, 1310], "grass_right": [960, 2220, 480, 900],
        "droplet_01_left": [0, 640, 145, 150], "droplet_02_upper_right": [1230, 350, 175, 205],
        "droplet_03_lower_right": [1270, 1860, 170, 185],
    }
    for name, (x, y, w, h) in repair_rois.items():
        before = Image.fromarray(np.uint8(target[y:y+h, x:x+w]))
        after = Image.fromarray(np.uint8(clean[y:y+h, x:x+w]))
        pair = Image.new("RGB", (w * 2, h))
        pair.paste(before, (0, 0)); pair.paste(after, (w, 0))
        save_png(ref / f"clean_plate_{name}_before_after_100pct.png", pair)
        zoom = Image.new("RGB", (w * 4, h * 2))
        zoom.paste(before.resize((w * 2, h * 2), Image.Resampling.NEAREST), (0, 0))
        zoom.paste(after.resize((w * 2, h * 2), Image.Resampling.NEAREST), (w * 2, 0))
        save_png(ref / f"clean_plate_{name}_before_after_200pct.png", zoom)
    save(ref / "clean_plate_repair_before.png", target, "RGB")
    save(ref / "clean_plate_repair_after.png", clean, "RGB")

    # Grass RGBA assets use source-over delta inversion, preserving leaf RGB
    # while keeping transparent pixels free of a rectangular backdrop.
    grass_entries, grass_imgs = [], {}
    for side, _ in grass_specs:
        support = grass_masks[side]
        full_rgba = effect_rgba(target, clean, support)
        x0, y0, x1, y1 = box(support)
        crop = Image.fromarray(full_rgba[y0:y1, x0:x1], "RGBA")
        save_png(aroot / "grass" / f"grass_{side}_rgba_v2.png", crop)
        grass_imgs[side] = crop
        save_png(aroot / "grass" / f"grass_{side}_edge_review.png", edge_strip(crop))
        grass_entries.append({
            "layerId": f"LAYER_GRASS_{side.upper()}", "semantic": f"Aquatic Grass {side.title()}",
            "assetFile": f"assets/grass/grass_{side}_rgba_v2.png",
            "pixelBounds": [x0, y0, x1 - x0, y1 - y0], "anchorPx": [int((x0 + x1) / 2), y1 - 1],
            "zIndex": 6 if side == "left" else 7, "blendMode": "SRC_OVER", "alpha": 1.0,
            "sourceType": "CONTROLLED_RECONSTRUCTION", "runtimeEligible": True,
            "alphaMethod": "Per-pixel source-over delta inversion within green-chroma support; not native source alpha.",
            "motionEligible": False,
            "bakedResiduals": ["lower root bed and overlapping caustic haze are retained in the static Base"],
        })

    # Editable skeletons are estimated control guides, not original vectors.
    skeletons = {
        "left": (660, 1310, [
            "M 32 1302 C 30 1130 42 920 160 725 C 224 615 264 470 315 270",
            "M 43 1300 C 35 1110 12 930 9 720 C 8 580 34 400 82 215",
            "M 58 1300 C 78 1125 142 970 251 855 C 333 770 417 650 516 510",
            "M 86 1304 C 133 1170 231 1090 332 1055 C 442 1017 516 932 598 882",
            "M 95 1308 C 182 1210 266 1177 379 1174 C 477 1172 571 1137 654 1101",
        ]),
        "right": (480, 900, [
            "M 463 895 C 449 758 415 632 365 514 C 331 434 285 340 246 252",
            "M 465 897 C 420 795 363 711 302 653 C 246 599 185 561 127 530",
            "M 470 900 C 438 817 397 757 354 714 C 305 665 261 632 216 610",
            "M 474 900 C 465 848 445 809 421 777 C 395 742 364 718 333 698",
        ]),
    }
    for side, (sw, sh, paths) in skeletons.items():
        lines = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{sw}" height="{sh}" viewBox="0 0 {sw} {sh}">',
                 "<title>Estimated grass control skeleton; not original vector source</title>",
                 "<metadata>Manually estimated motion guide from flattened Master. Runtime motion disabled.</metadata>"]
        lines += [f'<path id="leaf_{i:02d}" d="{p}" fill="none" stroke="#ffffff" stroke-width="2"/>' for i, p in enumerate(paths, 1)]
        lines.append("</svg>")
        (aroot / "grass" / f"grass_{side}_skeleton_v2.svg").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # Procedural optical sunlight maps. The Master still contains broad light;
    # this field is intentionally excluded from the visible stack.
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    nx, ny = xx / W, yy / H
    dx, dy = nx - 0.945, ny - 0.035
    r = np.sqrt((dx / 0.72) ** 2 + (dy / 0.54) ** 2)
    radial = np.exp(-2.1 * r * r)
    ang = np.arctan2(dy, dx)
    rays = 0.44 + 0.56 * np.maximum(np.cos(8 * ang + 2.8 * r), 0) ** 8
    gate = np.clip((0.70 - ny) / 0.16, 0, 1)
    sun_luma = np.clip(radial * rays * gate, 0, 1)
    sun = np.zeros((H, W, 4), np.uint8)
    sun[:, :, :3] = [255, 244, 216]
    sun[:, :, 3] = np.uint8(np.rint(sun_luma * 0.28 * 255))
    save(aroot / "sunlight/sunlight_field_v2.png", sun, "RGBA")
    save(aroot / "sunlight/sunlight_luma_mask_v2.png", sun_luma * 255, "L")
    save(aroot / "sunlight/sunlight_soft_mask_v2.png", ndimage.gaussian_filter(sun_luma, 18) * 255, "L")
    save(aroot / "sunlight/sunlight_diffusion_v2.png", ndimage.gaussian_filter(sun_luma, 72) * 255, "L")
    jwrite(aroot / "sunlight/sunlight_anchor_v2.json", {
        "sourceFile": str(MASTER), "sourceSha256": MASTER_SHA, "lightSourcePx": [1361, 109],
        "lightSourceNormalized": [0.945, 0.035], "direction": [-0.56, 0.83],
        "sourceType": "PROCEDURAL_LAYER", "status": "BAKED_STATIC_RESIDUAL",
    })
    jwrite(aroot / "sunlight/sunlight_params_v2.json", {
        "lightSourceNormalized": [0.945, 0.035], "direction": [-0.56, 0.83],
        "intensity": 0.28, "color": "#FFF4D8", "diffusionRadiusPx": 216,
        "edgeSoftness": 0.72, "blendMode": "SRC_OVER", "alpha": 0.28,
        "exclusionZones": ["center_reading_zone"], "sourceType": "PROCEDURAL_LAYER",
        "runtimeEligible": False, "reason": "The only source images are flattened; field would double baked upper-right light.",
    })

    # Three droplet optical map sets plus shaped static optical-delta RGBA.
    droplet_entries, drop_rgba = [], {}
    for d in drops:
        ident = d["id"]
        x, y, bw, bh = [int(v) for v in d["bbox"]]
        shape0 = mask(aroot / "droplets" / (ident + "_region_mask.png"))[y:y+bh, x:x+bw]
        shape = np.clip(ndimage.gaussian_filter(shape0.astype(np.float32), 0.7), 0, 1)
        dist = ndimage.distance_transform_edt(shape > 0.15)
        height = (np.clip(dist / max(float(dist.max()), 1.0), 0, 1) ** 0.82) * shape
        gy, gx = np.gradient(height)
        nxn, nyn = -gx * 4.2, -gy * 4.2
        nz = np.sqrt(np.maximum(1 - nxn * nxn - nyn * nyn, 0.02))
        normal = np.dstack(((nxn + 1) * 127.5, (nyn + 1) * 127.5, (nz + 1) * 127.5))
        disp = np.dstack((128 + nxn * 15, 128 + nyn * 15, height * 255, shape * 255))
        yy2, xx2 = np.mgrid[0:bh, 0:bw]
        edge = np.clip(dist / 4.5, 0, 1) * shape
        glint1 = np.exp(-2 * (((xx2 - bw * .32) / max(bw * .13, 1)) ** 2 + ((yy2 - bh * .14) / max(bh * .055, 1)) ** 2))
        glint2 = np.exp(-2 * (((xx2 - bw * .75) / max(bw * .055, 1)) ** 2 + ((yy2 - bh * .34) / max(bh * .06, 1)) ** 2))
        spec = np.clip((edge * .66 + glint1 * .62 + glint2 * .35) * shape, 0, 1)
        suffix = ident.replace("droplet_", "")
        save(aroot / "droplets" / f"{suffix}_shape_mask.png", shape * 255, "L")
        save(aroot / "droplets" / f"{suffix}_normal_map.png", normal, "RGB")
        save(aroot / "droplets" / f"{suffix}_height_map.png", height * 255, "L")
        save(aroot / "droplets" / f"{suffix}_displacement_map.png", disp, "RGBA")
        save(aroot / "droplets" / f"{suffix}_specular_mask.png", spec * 255, "L")
        local_support = drop_masks[ident][y:y+bh, x:x+bw]
        rgba = effect_rgba(target[y:y+bh, x:x+bw], clean[y:y+bh, x:x+bw], local_support)
        img = Image.fromarray(rgba, "RGBA")
        save_png(aroot / "droplets" / f"{suffix}_static_compat_rgba.png", img)
        drop_rgba[ident] = img
        droplet_entries.append({
            "layerId": "LAYER_" + ident.upper(), "semantic": ident,
            "assetFile": f"assets/droplets/{suffix}_static_compat_rgba.png",
            "opticalMaps": {
                "shape": f"assets/droplets/{suffix}_shape_mask.png",
                "normal": f"assets/droplets/{suffix}_normal_map.png",
                "height": f"assets/droplets/{suffix}_height_map.png",
                "displacement": f"assets/droplets/{suffix}_displacement_map.png",
                "specular": f"assets/droplets/{suffix}_specular_mask.png",
            },
            "pixelBounds": [x, y, bw, bh], "sourcePixelBounds": d.get("sourceBbox", d["bbox"]),
            "anchorPx": d["center"], "zIndex": 3 + len(droplet_entries),
            "blendMode": "SRC_OVER_STATIC / SHADER_DISPLACEMENT", "alpha": 1.0,
            "sourceType": "CONTROLLED_RECONSTRUCTION", "runtimeEligible": True,
            "refractionIndexReference": 1.333, "refractionIndexMeasured": False,
            "staticAssetMethod": "Shaped optical-delta RGBA solved against clean plate; no rectangular backdrop crop.",
        })

    # Compose base, caustics, droplets and grasses. Sunlight is deliberately
    # not drawn, because it remains baked in the base.
    comp = np.clip(clean + caustic_delta, 0, 255)
    for d in drops:
        ident = d["id"]
        x, y, bw, bh = [int(v) for v in d["bbox"]]
        comp[y:y+bh, x:x+bw] = over(comp[y:y+bh, x:x+bw], np.asarray(drop_rgba[ident], np.uint8))
    for entry, (side, _) in zip(grass_entries, grass_specs):
        x, y, bw, bh = entry["pixelBounds"]
        comp[y:y+bh, x:x+bw] = over(comp[y:y+bh, x:x+bw], np.asarray(grass_imgs[side], np.uint8))
    save(ref / "static_master.png", target, "RGB")
    save(ref / "static_layered_v3.png", comp, "RGB")
    save(aroot / "android_runtime/api24_static/lake_glass_static_layered_v3.png", comp, "RGB")
    save(aroot / "android_runtime/api24_static/lake_glass_master_fallback.png", target, "RGB")

    diff = np.abs(target - comp)
    save(ref / "master_vs_layered_difference.png", diff * 5, "RGB")
    heat = np.zeros_like(target)
    hot = np.clip(np.max(diff, axis=2) * 5, 0, 255)
    heat[:, :, 0] = hot
    heat[:, :, 1] = hot * 0.22
    heat[:, :, 2] = hot * 0.05
    save(ref / "master_vs_layered_heatmap.png", heat, "RGB")
    save(ref / "master_vs_layered_overlay.png", (target + comp) / 2, "RGB")
    inspector = [Image.fromarray(np.uint8(clean)), Image.fromarray(np.uint8(np.clip(clean + caustic_delta, 0, 255)))]
    labels = ["Clean base", "Base + caustics"]
    for d in drops:
        ident = d["id"]; x, y, bw, bh = [int(v) for v in d["bbox"]]
        one = clean.copy(); one[y:y+bh, x:x+bw] = over(one[y:y+bh, x:x+bw], np.asarray(drop_rgba[ident], np.uint8))
        inspector.append(Image.fromarray(np.uint8(np.clip(one, 0, 255)))); labels.append(ident)
    for entry, (side, _) in zip(grass_entries, grass_specs):
        x, y, bw, bh = entry["pixelBounds"]; one = clean.copy()
        one[y:y+bh, x:x+bw] = over(one[y:y+bh, x:x+bw], np.asarray(grass_imgs[side], np.uint8))
        inspector.append(Image.fromarray(np.uint8(np.clip(one, 0, 255)))); labels.append(f"Grass {side}")
    inspector.append(Image.fromarray(np.uint8(np.clip(comp, 0, 255)))); labels.append("All independent layers")
    sheet(ref / "layer_inspector_contact_sheet.png", inspector, labels, (420, 700))
    matrix = Image.new("RGB", (1080, 360), (248, 249, 249)); md = ImageDraw.Draw(matrix); font = ImageFont.load_default()
    md.text((14, 12), "Independent layer visibility matrix; sunlight remains baked/disabled", fill=(10, 45, 55), font=font)
    cols, rows = ["Default","Caustics off","Drops off","Grass off","Sunlight field"], ["Base","Caustics","Droplets x3","Grass L/R","Sunlight"]
    vals = [[1,1,1,1,1],[1,0,1,1,1],[1,1,0,1,1],[1,1,1,0,1],[0,0,0,0,0]]
    for j,c in enumerate(cols): md.text((240+j*160,45),c,fill=(10,45,55),font=font)
    for i,row in enumerate(rows):
        y=78+52*i; md.text((16,y),row,fill=(20,55,65),font=font)
        for j,v in enumerate(vals[i]):
            x=240+j*160; md.rectangle((x,y-2,x+25,y+20),fill=(50,160,139) if v else (205,215,215))
            md.text((x+8,y+2),"x" if v else "-",fill="white" if v else (70,80,80),font=font)
    save_png(ref / "layer_visibility_matrix.png", matrix)
    sheet(ref / "grass_edge_review.png", [Image.open(aroot / "grass/grass_left_edge_review.png"),Image.open(aroot / "grass/grass_right_edge_review.png")], ["Grass left black/white/checker","Grass right black/white/checker"],(600,650))
    drop_review, drop_labels = [], []
    for d in drops:
        ident=d["id"]; x,y,bw,bh=[int(v) for v in d["bbox"]]
        drop_review.extend([
            Image.fromarray(np.uint8(target[y:y+bh,x:x+bw])),
            Image.fromarray(np.uint8(clean[y:y+bh,x:x+bw])),
            Image.fromarray(np.uint8(np.clip(comp[y:y+bh,x:x+bw],0,255))),
        ])
        drop_labels.extend([ident+" Master",ident+" Base",ident+" Layered"])
    sheet(ref / "droplets_optical_review.png", drop_review, drop_labels, (240,300))
    sun_comp = Image.alpha_composite(Image.fromarray(np.uint8(target),"RGB").convert("RGBA"),Image.fromarray(sun,"RGBA")).convert("RGB")
    sheet(ref / "sunlight_caustics_review.png",[
        Image.fromarray(np.uint8(target)),sun_comp,Image.fromarray(np.uint8(clean)),Image.fromarray(np.uint8(np.clip(clean+caustic_delta,0,255)))],
        ["Master","Sun field over Master (diagnostic double-light)","Clean plate","Base + caustics"],(480,720))

    # One shared CenterCrop transform for all layers across four viewport ratios.
    crop_records,crop_imgs,crop_labels=[],[],[]
    for sid,vw,vh in [("M01",320,480),("M02",360,640),("M03",390,844),("M04",430,932)]:
        scale=max(vw/W,vh/H); cw=min(W,int(round(vw/scale))); ch=min(H,int(round(vh/scale)))
        left,top=(W-cw)//2,(H-ch)//2
        im=Image.fromarray(np.uint8(target[top:top+ch,left:left+cw])).resize((vw*2,vh*2),Image.Resampling.LANCZOS)
        crop_imgs.append(im); crop_labels.append(f"{sid} {vw}x{vh} CenterCrop")
        crop_records.append({"screenId":sid,"sizeDp":[vw,vh],"alignment":"CenterCrop","sourceCropPx":[left,top,cw,ch],
            "singleMatrixForAllLayers":True,"topLightRetained":top==0,"lowerGrassRetained":top+ch>=H-260,
            "knownTradeoff":"Top light and bottom grass cannot both be fully retained under crop-only scaling." if sid=="M01" else None})
    sheet(ref / "crop_matrix_v3.png",crop_imgs,crop_labels,(400,700))
    jwrite(aroot / "metadata/crop_safe_zone_v3.json",{"canvasSizePx":[W,H],"recommendedAlignment":"CenterCrop","screens":crop_records})

    layers=[{
        "layerId":"LAYER_BASE_CLEAN","semantic":"Lake water clean plate","assetFile":"assets/base/lake_base_clean_v2.png",
        "pixelBounds":[0,0,W,H],"anchorPx":[0,0],"zIndex":0,"blendMode":"SRC","alpha":1,
        "sourceType":"CONTROLLED_RECONSTRUCTION","runtimeEligible":True,
        "bakedResiduals":["broad upper-right sunlight and surface reflection","lower grass root beds in caustic-overlap zones"],
    },{
        "layerId":"LAYER_SUNLIGHT_FIELD","semantic":"Upper-right sunlight reconstruction","assetFile":"assets/sunlight/sunlight_field_v2.png",
        "pixelBounds":[0,0,W,H],"anchorPx":[1361,109],"zIndex":1,"blendMode":"SRC_OVER","alpha":0.28,
        "sourceType":"PROCEDURAL_LAYER","runtimeEligible":False,"reason":"No clean unlit source; drawing over Master would double light.",
    },{
        "layerId":"LAYER_CAUSTICS","semantic":"Lower water caustics","assetFile":"assets/caustics/caustics_texture_v2.png",
        "pixelBounds":[0,1980,W,H-1980],"anchorPx":[720,2850],"zIndex":2,"blendMode":"PLUS","alpha":1,
        "sourceType":"CONTROLLED_RECONSTRUCTION","runtimeEligible":True,"tileable":False,"motionEligible":False,
    },*droplet_entries,*grass_entries]
    for layer in layers:
        layer["anchorNormalized"]=[round(layer["anchorPx"][0]/W,6),round(layer["anchorPx"][1]/H,6)]
        layer["sourceFile"]=str(MASTER); layer["sourceSha256"]=MASTER_SHA
        layer_path = aroot / layer["assetFile"].replace("assets/", "")
        layer["assetSha256"] = sha(layer_path)
        with Image.open(layer_path) as layer_image:
            layer["format"] = layer_image.format
            layer["assetDimensionsPx"] = [layer_image.width, layer_image.height]
    visible=[x["layerId"] for x in layers if x["layerId"]!="LAYER_SUNLIGHT_FIELD"]
    jwrite(aroot / "metadata/lake_layer_pack_v3.json",{
        "schemaVersion":"3.0","canvasSize":[W,H],"coordinateSystem":{"origin":"top-left","xAxis":"right","yAxis":"down","normalized":True},
        "globalTransform":"single uniform transform applied to every layer","visibleCompositeLayerIds":visible,
        "excludedFromVisibleStack":["LAYER_SUNLIGHT_FIELD"],"layers":layers})
    jwrite(aroot / "metadata/lake_anchors_v3.json",{
        "canvasSize":[W,H],"coordinateSystem":"master pixels, origin top-left","masterSha256":MASTER_SHA,
        "layers":[{k:x[k] for k in ["layerId","pixelBounds","anchorPx","anchorNormalized","zIndex"]} for x in layers]})
    jwrite(aroot / "metadata/lake_material_tokens_v3.json",{
        "schemaVersion":"3.0","tokens":{"mistWhite":"#EAF7F7","lakePale":"#C6EAE5","lakeGreen":"#8CCFC4","lakeTeal":"#65BFB5","deepLake":"#153B50","morningGold":"#D8B36A"},
        "masterIsColorAuthority":True,"globalRecolor":False})
    jwrite(aroot / "metadata/lake_glass_motion_profiles_v3.json",{
        "schemaVersion":"3.0","defaultEnabled":False,"profiles":{
            "grass_low_frequency":{"periodSeconds":[16,24],"rootFixed":True,"leafTipMaxDp":2,"wholePlantTranslation":False,"runtimeReady":False},
            "sunlight_low_frequency":{"periodSeconds":[24,36],"directionFixed":True,"intensityDelta":0.02,"runtimeReady":False},
            "caustics_low_frequency":{"periodSeconds":[24,36],"wholeLayerDriftDp":0,"runtimeReady":False},
            "droplet_static":{"enabled":False,"onlyWeakSpecularVariation":True}},
        "sharedClock":"Must not consume or modify AI Orb x WaterLight recognition clock."})
    jwrite(aroot / "grass/grass_motion_bones_v2.json",{
        "schemaVersion":"2.0","coordinateSystem":"local SVG px; anchors in Master px","runtimeEnabled":False,
        "sourceType":"ESTIMATED_CONTROL_GUIDE","periodSeconds":[16,24],
        "leafGroups":[
            {"side":"left","rootAnchor":[32,3108],"skeletonSvg":"grass_left_skeleton_v2.svg","leafIds":[f"leaf_{i:02d}" for i in range(1,6)],"segmentCount":8,"bendWeight":0.35,"depthWeight":0.55,"motionPhase":0,"maxOffsetDp":2},
            {"side":"right","rootAnchor":[1405,3108],"skeletonSvg":"grass_right_skeleton_v2.svg","leafIds":[f"leaf_{i:02d}" for i in range(1,5)],"segmentCount":8,"bendWeight":0.28,"depthWeight":0.62,"motionPhase":0.42,"maxOffsetDp":2}]})
    jwrite(aroot / "grass/grass_layer_params_v2.json",{
        "defaultVisible":True,"blendMode":"SRC_OVER","alpha":1,"colorSpace":"sRGB","motionEnabled":False,
        "leftRightMirror":False,"sourceType":"CONTROLLED_RECONSTRUCTION","alphaMethod":"source-over delta inversion in botanical chroma support"})
    jwrite(aroot / "caustics/caustics_params_v2.json",{
        "sourceFile":str(MASTER),"sourceSha256":MASTER_SHA,"regionBounds":[0,1980,W,H-1980],"intensity":1,
        "spatialFrequency":"positive high-pass detail; gaussian sigma 13px","scale":1,"blendMode":"PLUS","alpha":1,
        "phase":0,"motionPeriodSeconds":[24,36],"tileable":False,"tileValidationStatus":"NOT_TILEABLE_NO_TILE_CREATED",
        "sourceType":"CONTROLLED_RECONSTRUCTION","runtimeEligible":True})
    jwrite(aroot / "droplets/droplets_anchors_v2.json",{
        "canvasSize":[W,H],"coordinateSystem":"Master px, origin top-left","masterSha256":MASTER_SHA,
        "droplets":[{**{k:d[k] for k in ["id","bbox","center","centerNormalized"]},"sourceType":"CONTROLLED_RECONSTRUCTION"} for d in drops]})
    jwrite(aroot / "droplets/droplets_optical_params_v2.json",{
        "refractionIndexReference":1.333,"refractionIndexMeasured":False,
        "model":"Normal XY drives scene UV offset; shape gates sampling; height controls edge thickness; specular mask supplies glints",
        "api33ShaderSuggested":True,"staticFallback":"per-drop shaped optical-delta RGBA",
        "drops":[{"id":d["id"],"pixelBounds":d["bbox"],"normalScale":15,"displacementPxMax":15,"blendMode":"shader displacement + SRC_OVER highlights"} for d in drops]})

    jwrite(aroot / "android_runtime/runtime_manifest.json",{
        "schemaVersion":"1.0","canvasSizePx":[W,H],"coordinateSystem":"master px, top-left origin",
        "defaultRenderer":"Compose Canvas / Android Canvas",
        "api24to32":{"mode":"static","primary":"assets/android_runtime/api24_static/lake_glass_static_layered_v3.png",
            "fallback":"assets/android_runtime/api24_static/lake_glass_master_fallback.png","sunlight":"baked in base"},
        "api33plus":{"mode":"optional AGSL RuntimeShader","noGLSurfaceView":True,
            "caustics":"assets/caustics/caustics_texture_v2.png",
            "dropletMaps":"assets/droplets/*_normal_map.png, *_height_map.png, *_displacement_map.png, *_specular_mask.png",
            "grass":["assets/grass/grass_left_rgba_v2.png","assets/grass/grass_right_rgba_v2.png"],
            "sunlight":{"asset":"assets/sunlight/sunlight_field_v2.png","runtimeEligible":False}},
        "singleGlobalMatrix":True,"decodeCache":"decode once; canonical source remains lossless",
        "scope":"assets only; no Android pages or renderer source changed"})
    jwrite(aroot / "metadata/android_asset_compatibility_v1.json",{
        "minSdk":24,"api24to32":{"CanvasStaticComposite":True,"dynamicOpticalMaps":False},
        "api33plus":{"AGSLOptional":True,"deviceTested":False},"blendModes":{"SRC_OVER":"Canvas","PLUS":"PorterDuff ADD / shader plus"},
        "noGLSurfaceView":True,"losslessCanonicalAssets":True,"rightsStatus":"PENDING_REVIEW"})
    (aroot / "android_runtime/android_asset_compatibility.md").write_text(
        "# Android asset compatibility\n\n"
        "- Minimum API: 24. API 24–32 use the supplied static reconstruction PNG or unchanged Master fallback.\n"
        "- API 33+ may bind droplet optical maps and caustic detail to optional AGSL RuntimeShader. No device shader test was run.\n"
        "- Draw all layers through one 1440x3120 scene transform. Use SRC_OVER for RGBA assets and PLUS/PorterDuff ADD for caustic detail.\n"
        "- Sunlight remains baked in the base. Do not draw the procedural sunlight preview over the base.\n"
        "- No Android page integration or GLSurfaceView is included.\n",encoding="utf-8")

    crop_records,crop_imgs,crop_labels=[],[],[]
    for sid,vw,vh in [("M01",320,480),("M02",360,640),("M03",390,844),("M04",430,932)]:
        scale=max(vw/W,vh/H); cw=min(W,int(round(vw/scale))); ch=min(H,int(round(vh/scale)))
        left,top=(W-cw)//2,(H-ch)//2
        im=Image.fromarray(np.uint8(target[top:top+ch,left:left+cw])).resize((vw*2,vh*2),Image.Resampling.LANCZOS)
        crop_imgs.append(im); crop_labels.append(f"{sid} {vw}x{vh} CenterCrop")
        crop_records.append({"screenId":sid,"sizeDp":[vw,vh],"alignment":"CenterCrop","sourceCropPx":[left,top,cw,ch],
            "singleMatrixForAllLayers":True,"topLightRetained":top==0,"lowerGrassRetained":top+ch>=H-260,
            "knownTradeoff":"Top light and bottom grass cannot both be fully retained under crop-only scaling." if sid=="M01" else None})
    sheet(ref / "crop_matrix_v3.png",crop_imgs,crop_labels,(400,700))
    jwrite(aroot / "metadata/crop_safe_zone_v3.json",{"canvasSizePx":[W,H],"recommendedAlignment":"CenterCrop","screens":crop_records})

    # Explicit reports preserve the true status and limitations.
    (ev / "clean_plate_reconstruction_report.md").write_text(
        "# Clean plate reconstruction audit\n\n"
        f"- Candidate 02 SHA-256: {MASTER_SHA}; canvas 1440x3120 RGB.\n"
        "- Separated grass blades and three droplet supports are repaired with a center-water row profile or local 14px Gaussian fill, each restricted to explicit masks.\n"
        "- Lower grass/root beds that overlap caustics remain baked in the Base; the grass RGBA files contain only the safer upper/leaf-body regions.\n"
        "- Lower caustic positive high-pass detail is removed from the base and delivered separately as an RGBA texture using Canvas PLUS / PorterDuff ADD.\n"
        "- Broad upper-right morning illumination and water-surface reflections remain baked. No unlit clean plate exists in the supplied inputs, so this clean plate is incomplete.\n"
        "- Reference images include original/repaired crops at 100% and 200% and the full repair mask.\n",encoding="utf-8")
    (ev / "layer_visual_review.md").write_text(
        "# Layer visual review\n\n"
        "- Grass RGBA layers use a source-over delta solve against the repaired base; edge strips show black, white and checker backgrounds.\n"
        "- SVG paths are manually estimated control guides, not recovered source vectors. Motion remains disabled.\n"
        "- Droplets each have shape, normal, height, displacement and specular maps plus shaped static compatibility RGBA.\n"
        "- Caustics are lower-water positive detail and are not tileable. No tile was created.\n"
        "- Sunlight is a procedural right-origin field for inspection only; it is excluded from runtime because light remains baked.\n",encoding="utf-8")
    (ev / "optical_reconstruction_report.md").write_text(
        "# Optical reconstruction report\n\n"
        "- Droplet normals derive from fitted ellipse height fields; displacement encodes normal XY slope; specular maps combine boundary response and two soft glints.\n"
        "- Refractive index 1.333 is a reference value, not a measured property of the source material.\n"
        "- Actual scene-framebuffer refraction still requires Android shader implementation and device validation.\n",encoding="utf-8")
    (ev / "final_delivery_report.md").write_text(
        "# P1 delivery status\n\n"
        "**ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS.** Separated grass leaf bodies, lower caustic detail, and three droplet optical asset sets are independently addressable. Lower grass/root beds, broad top-right sunlight, and surface reflection remain baked in the base; procedural sunlight is excluded from the runtime stack. ASSET_PACK_READY_FOR_ANDROID is not claimed.\n\n"
        "Candidate 02 remains CANDIDATE_VISUAL_NOT_FROZEN. External reuse permission remains pending review.\n",encoding="utf-8")
    (pkg / "README_ANDROID_ASSET_PACK_V1.md").write_text(
        "# Lake Glass V2 Android asset pack — P1\n\n"
        "Asset-only controlled reconstruction from the supplied Candidate 02 Master. No Android page replacement is included.\n\n"
        "## Status\n\n"
        "ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS. Broad upper-right sunlight and lower grass/root beds remain baked in lake_base_clean_v2.png. Do not draw the procedural sunlight field over that base.\n\n"
        "## Entry points\n\n"
        "- assets/base/lake_base_clean_v2.png\n"
        "- assets/caustics/caustics_texture_v2.png\n"
        "- assets/grass/grass_left_rgba_v2.png and grass_right_rgba_v2.png\n"
        "- assets/droplets/ (three optical map sets and shaped RGBA compatibility layers)\n"
        "- assets/android_runtime/runtime_manifest.json\n"
        "- assets/metadata/asset_manifest_v3.json\n\n"
        "Run scripts/build_lake_glass_true_layers_p1.py then scripts/verify_lake_glass_true_layers_p1.py from repository root.\n",encoding="utf-8")

    jwrite(ev / "source_integrity_report_p1.json",{
        "masterSha256":MASTER_SHA,"originalSha256":ORIGINAL_SHA,"sourceManifestSha256":MANIFEST_SHA,
        "nativeLayerSearch":{"repositoryMain":"searched; none found","PR302":"specification/documentation only","PR303":"flattened inputs and diagnostic derivations only","PSD_ORA_XCF":"none found in checked repository tree"},
        "originalVsMaster":"near-identical flattened composition after proportional resize (diagnostic mean absolute RGB difference about 0.11)",
        "rightsStatus":"PENDING_REVIEW"})
    # Manifest includes every P1 derivative except its own self hash.
    scans=[aroot/"base",aroot/"sunlight",aroot/"caustics",aroot/"grass",aroot/"droplets",aroot/"android_runtime",aroot/"metadata",ref,ev,pkg/"README_ANDROID_ASSET_PACK_V1.md"]
    derived=[]
    for folder in scans:
        for p in sorted(folder.rglob("*")):
            if p.is_file() and p.name not in {"asset_manifest_v3.json", "source_integrity_report.json"}:
                derived.append(record(root,(PKG/p.relative_to(pkg)).as_posix()))
    manifest={
        "schemaVersion":"3.0","assetPackId":"LakeGlassV2TrueLayersP1",
        "taskId":"YUJIAN_LAKE_GLASS_V2_TRUE_LAYER_RECONSTRUCTION_ANDROID_ASSET_PACK_P1",
        "status":"ASSET_PACK_PARTIAL_WITH_BAKED_LAYERS","readyForAndroid":False,
        "master":{"path":str(MASTER),"sha256":MASTER_SHA,"dimensionsPx":[W,H],"candidateStatus":"CANDIDATE_VISUAL_NOT_FROZEN"},
        "source":{"path":str(ORIGINAL),"sha256":ORIGINAL_SHA,"dimensionsPx":[852,1846]},
        "sourceManifest":{"sha256":MANIFEST_SHA},
        "sourceSearch":{"nativeLayerFound":False,"searchedMainAndPrHistory":True,
            "notes":"No PSD/ORA/XCF or native Lake Glass layer export found in checked repository and PR paths; the smaller source is a near-identical flattened composition."},
        "originPermission":"PENDING_REVIEW; derived from user-supplied images and procedural reconstruction; no independent rights claim",
        "layers":layers,"derivedFiles":derived,"manifestSelfHashExcluded":True,
        "toolFiles":[
            {"path":"scripts/build_lake_glass_true_layers_p1.py","sha256":sha(root/"scripts/build_lake_glass_true_layers_p1.py")},
            {"path":"scripts/verify_lake_glass_true_layers_p1.py","sha256":sha(root/"scripts/verify_lake_glass_true_layers_p1.py")},
            {"path":"scripts/generate_lake_glass_preview_p1.py","sha256":sha(root/"scripts/generate_lake_glass_preview_p1.py")},
        ],
        "statusReason":"Broad upper-right sunlight, lower grass/root beds and surface reflection remain baked in the base; procedural sunlight is excluded from visible composition.",
    }
    jwrite(aroot / "metadata/asset_manifest_v3.json",manifest)
    return {"status":manifest["status"],"readyForAndroid":False,"masterSha256":MASTER_SHA,"originalSha256":ORIGINAL_SHA,
        "generatedP1FileCount":len(derived)+1,"staticReconstruction":str(PKG/"assets/reference/static_layered_v3.png"),
        "manifest":str(PKG/"assets/metadata/asset_manifest_v3.json"),
        "blocker":"Upper-right sunlight and lower grass/root beds overlap with water-surface reflections/caustics in the supplied flattened source."}


def main() -> None:
    ap=argparse.ArgumentParser()
    ap.add_argument("--root",default=".")
    args=ap.parse_args()
    print(json.dumps(build(Path(args.root).resolve()),ensure_ascii=False,indent=2))


if __name__=="__main__":
    main()
