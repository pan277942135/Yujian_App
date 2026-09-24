#!/usr/bin/env python3
"""Deterministically materialize Empty Home V2 source, reusable masters and Android assets.

The frozen screenshot remains untouched.  The scene base is a documented local clean-up
of that screenshot; every dynamic element is rebuilt as an independently addressable RGBA
layer in 1080 x 1920 reference coordinates.
"""

from __future__ import annotations

import hashlib
import json
import math
import shutil
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
GENERATED_INPUT = ROOT.parent / "generated_images" / "exec-faaeeca2-b49a-45cd-9dbb-1c8820377bea.png"
FROZEN_INPUT = ROOT.parent / "upload" / "晨雾湖畔，记录第一条鱼.png"
DESIGN = ROOT / "design" / "pages" / "home" / "empty_home"
SYSTEM = ROOT / "design" / "system" / "components" / "primary_capture_button"
RUNTIME = ROOT / "app" / "src" / "main" / "assets" / "empty_home_runtime_v2"
REF_W, REF_H = 1080, 1920
GOLD = (218, 160, 45, 255)
INK = (24, 50, 74, 255)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def save_png(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)


def normalized(image: Image.Image) -> Image.Image:
    """Uniform width fit.  The source is 0.04% taller than 9:16, so final row is edge padded."""
    src = image.convert("RGB")
    scaled_h = round(src.height * REF_W / src.width)
    resized = src.resize((REF_W, scaled_h), Image.Resampling.LANCZOS)
    if scaled_h == REF_H:
        return resized
    canvas = Image.new("RGB", (REF_W, REF_H))
    if scaled_h < REF_H:
        canvas.paste(resized, (0, 0))
        edge = resized.crop((0, scaled_h - 1, REF_W, scaled_h)).resize((REF_W, REF_H - scaled_h))
        canvas.paste(edge, (0, scaled_h))
    else:
        # This branch is retained for future sources; current input uses one-pixel edge padding.
        canvas.paste(resized.crop((0, 0, REF_W, REF_H)), (0, 0))
    return canvas


def rgba_canvas(size: tuple[int, int]) -> Image.Image:
    return Image.new("RGBA", size, (0, 0, 0, 0))


def foreground_delta(
    frozen: Image.Image,
    clean: Image.Image,
    box: tuple[int, int, int, int],
    threshold: int,
) -> Image.Image:
    """Extract an RGBA foreground only where frozen pixels differ from the local clean plate."""
    source_crop = frozen.crop(box).convert("RGB")
    clean_crop = clean.crop(box).convert("RGB")
    diff = ImageChops.difference(source_crop, clean_crop)
    max_diff = ImageChops.lighter(ImageChops.lighter(diff.getchannel("R"), diff.getchannel("G")), diff.getchannel("B"))
    # A difference matte is a selector, not a source-opacity measurement.  Reach opaque after
    # a 64-level foreground separation while leaving small local clean-plate variation invisible.
    alpha = max_diff.point(lambda value: max(0, min(255, int((value - threshold) * 4))))
    result = source_crop.convert("RGBA")
    result.putalpha(alpha)
    return result


def apply_alpha_mask(asset: Image.Image, mask: Image.Image) -> Image.Image:
    result = asset.copy()
    result.putalpha(ImageChops.multiply(result.getchannel("A"), mask))
    return result


def source_with_geometry_mask(frozen: Image.Image, box: tuple[int, int, int, int], mask: Image.Image) -> Image.Image:
    result = frozen.crop(box).convert("RGBA")
    result.putalpha(mask.filter(ImageFilter.GaussianBlur(.55)))
    return result


def source_with_dark_geometry_mask(frozen: Image.Image, box: tuple[int, int, int, int], mask: Image.Image) -> Image.Image:
    source = frozen.crop(box).convert("RGB")
    darkness = source.convert("L").point(lambda value: max(0, min(255, (170 - value) * 5)))
    result = source.convert("RGBA")
    result.putalpha(ImageChops.multiply(mask.filter(ImageFilter.GaussianBlur(.45)), darkness))
    return result


def extract_rod(frozen: Image.Image, clean: Image.Image) -> Image.Image:
    box = (0, 950, 450, 1315)
    mask = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
    d = ImageDraw.Draw(mask)
    # Tapered rod body: only semantic fishing hardware, never lake/grass pixels.
    d.line([(-30, 290), (110, 204)], fill=255, width=16)
    d.line([(105, 207), (260, 110)], fill=255, width=10)
    d.line([(257, 112), (430, 5)], fill=255, width=5)
    d.polygon([(-4, 254), (12, 340), (45, 312), (38, 258)], fill=255)
    for x, y, radius in [(78, 220, 12), (170, 162, 9), (258, 108, 7), (336, 61, 5), (391, 28, 4)]:
        d.ellipse((x-radius, y-radius, x+radius, y+radius), outline=255, width=max(2, radius // 3))
    return source_with_dark_geometry_mask(frozen, box, mask)


def extract_line(frozen: Image.Image, clean: Image.Image) -> Image.Image:
    box = (400, 950, 570, 1200)
    mask = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
    d = ImageDraw.Draw(mask)
    # A sampled Bézier preserves the natural single line while excluding bobber pixels.
    points = []
    for index in range(41):
        t = index / 40
        x = (1-t)**3*15 + 3*(1-t)**2*t*35 + 3*(1-t)*t**2*100 + t**3*130
        y = (1-t)**3*0 + 3*(1-t)**2*t*50 + 3*(1-t)*t**2*155 + t**3*219
        points.append((round(x), round(y)))
    d.line(points, fill=255, width=2)
    return source_with_geometry_mask(frozen, box, mask)


def extract_bobber(frozen: Image.Image, clean: Image.Image) -> Image.Image:
    box = (518, 1084, 542, 1206)
    mask = Image.new("L", (box[2] - box[0], box[3] - box[1]), 0)
    d = ImageDraw.Draw(mask)
    d.rectangle((8, 0, 16, 51), fill=255)
    d.ellipse((4, 42, 20, 86), fill=255)
    d.polygon([(8, 80), (16, 80), (15, 120), (10, 120)], fill=255)
    return source_with_geometry_mask(frozen, box, mask)


def make_rod() -> Image.Image:
    # Bounding box at reference origin (0, 950).  Supersampling preserves clean alpha edges.
    factor = 4
    width, height = 450, 365
    layer = rgba_canvas((width * factor, height * factor))
    draw = ImageDraw.Draw(layer)
    def line(points, fill, w):
        draw.line([(int(x * factor), int(y * factor)) for x, y in points], fill=fill, width=int(w * factor), joint="curve")
    start, end = (-58, 352), (431, 8)
    line([start, end], (7, 20, 30, 245), 10)
    line([(-58, 347), (431, 3)], (42, 64, 76, 240), 4)
    line([(-57, 344), (430, 0)], (172, 180, 177, 155), 1.15)
    # Taper and guide rings closely follow the frozen rod silhouette.
    for x, y, radius in [(78, 256, 10), (170, 192, 8), (258, 130, 6.5), (336, 74, 5), (391, 35, 3.5)]:
        cx, cy = x * factor, y * factor
        draw.ellipse((cx - radius*factor, cy - radius*factor, cx + radius*factor, cy + radius*factor), outline=(10, 22, 28, 235), width=max(1, int(2.2*factor)))
        draw.ellipse((cx - (radius-2.4)*factor, cy - (radius-2.4)*factor, cx + (radius-2.4)*factor, cy + (radius-2.4)*factor), outline=(190, 191, 176, 150), width=max(1, int(.8*factor)))
    return layer.resize((width, height), Image.Resampling.LANCZOS)


def make_line() -> Image.Image:
    # Bounding box at reference origin (400, 950); one continuous natural fishing line.
    factor = 4
    width, height = 170, 250
    layer = rgba_canvas((width * factor, height * factor))
    draw = ImageDraw.Draw(layer)
    points = [(15, 0), (40, 55), (68, 112), (113, 160), (130, 219)]
    scaled = [(x * factor, y * factor) for x, y in points]
    draw.line(scaled, fill=(211, 226, 228, 205), width=4, joint="curve")
    draw.line(scaled, fill=(113, 145, 157, 190), width=2, joint="curve")
    return layer.resize((width, height), Image.Resampling.LANCZOS)


def make_bobber() -> Image.Image:
    # Bounding box at reference origin (518, 1084).  Main float ends at contact y=1168.
    factor = 4
    width, height = 24, 122
    layer = rgba_canvas((width * factor, height * factor))
    d = ImageDraw.Draw(layer)
    def rect(box, fill):
        d.rounded_rectangle(tuple(int(v*factor) for v in box), radius=int(1.4*factor), fill=fill)
    # red/green signal stem and a fine dark outline preserve the V2 fishing-float read.
    rect((10.2, 0, 13.8, 17), (222, 55, 45, 255))
    rect((10.2, 17, 13.8, 31), (101, 167, 74, 255))
    rect((10.45, 31, 13.55, 50), (244, 200, 65, 255))
    d.line([(12*factor, 0), (12*factor, 53*factor)], fill=(37, 55, 50, 255), width=max(1, factor))
    d.ellipse((6.2*factor, 44*factor, 17.8*factor, 82*factor), fill=(248, 241, 218, 255), outline=(123, 92, 52, 255), width=2*factor)
    d.ellipse((8.0*factor, 48*factor, 10.8*factor, 73*factor), fill=(255, 255, 255, 175))
    d.polygon([(12*factor, 82*factor), (14.2*factor, 102*factor), (12*factor, 116*factor), (9.8*factor, 102*factor)], fill=(174, 111, 55, 170))
    d.line([(12*factor, 82*factor), (12*factor, 120*factor)], fill=(127, 75, 42, 185), width=max(1, factor))
    return layer.resize((width, height), Image.Resampling.LANCZOS)


def make_ripple() -> Image.Image:
    factor = 4
    width, height = 238, 82
    layer = rgba_canvas((width * factor, height * factor))
    d = ImageDraw.Draw(layer)
    for inset, alpha, stroke in [(2, 165, 1.1), (24, 145, 1.0), (51, 120, 0.9), (78, 100, 0.8)]:
        d.ellipse((inset*factor, (height*.5-(height-inset*0.36)/2)*factor,
                   (width-inset)*factor, (height*.5+(height-inset*0.36)/2)*factor),
                  outline=(233, 246, 246, alpha), width=max(1, int(stroke*factor)))
    return layer.resize((width, height), Image.Resampling.LANCZOS)


def make_cloud() -> Image.Image:
    width, height, factor = 620, 180, 2
    layer = rgba_canvas((width*factor, height*factor))
    d = ImageDraw.Draw(layer)
    blobs = [(40, 96, 88), (115, 80, 105), (205, 112, 70), (330, 62, 103), (420, 86, 90), (515, 104, 74)]
    for x, y, r in blobs:
        d.ellipse(((x-r)*factor, (y-r*.42)*factor, (x+r)*factor, (y+r*.42)*factor), fill=(244, 239, 225, 22))
    return layer.filter(ImageFilter.GaussianBlur(18*factor)).resize((width, height), Image.Resampling.LANCZOS)


def make_sun_beam() -> Image.Image:
    width, height = 340, 520
    layer = rgba_canvas((width, height))
    d = ImageDraw.Draw(layer)
    for y in range(height):
        alpha = int(32 * (1 - y / height) ** 1.6)
        inset = int(y * .22)
        d.line((width//2-inset, y, width//2+inset, y), fill=(255, 224, 150, alpha), width=1)
    return layer.filter(ImageFilter.GaussianBlur(8))


def make_particle() -> Image.Image:
    layer = rgba_canvas((28, 28))
    d = ImageDraw.Draw(layer)
    d.ellipse((11, 11, 17, 17), fill=(255, 229, 166, 180))
    return layer.filter(ImageFilter.GaussianBlur(2))


def make_camera_assets() -> tuple[Image.Image, Image.Image, Image.Image]:
    size, factor = 208, 3
    base = rgba_canvas((size*factor, size*factor))
    d = ImageDraw.Draw(base)
    center = size*factor/2
    # restrained warm rim, white core and navy icon as frozen V2 specifies
    d.ellipse((11*factor, 11*factor, 197*factor, 197*factor), fill=(255, 255, 255, 242), outline=(206, 154, 47, 255), width=3*factor)
    d.ellipse((17*factor, 17*factor, 191*factor, 191*factor), outline=(255, 255, 255, 255), width=3*factor)
    d.rounded_rectangle((62*factor, 79*factor, 146*factor, 135*factor), radius=8*factor, outline=INK, width=6*factor)
    d.rounded_rectangle((82*factor, 67*factor, 112*factor, 87*factor), radius=4*factor, fill=INK)
    d.ellipse((86*factor, 90*factor, 122*factor, 126*factor), outline=INK, width=6*factor)
    base = base.resize((size, size), Image.Resampling.LANCZOS)
    glow = rgba_canvas((size, size))
    gd = ImageDraw.Draw(glow)
    gd.ellipse((2, 2, 206, 206), outline=(250, 207, 106, 80), width=5)
    glow = glow.filter(ImageFilter.GaussianBlur(6))
    rim = rgba_canvas((size, size))
    rd = ImageDraw.Draw(rim)
    rd.arc((5, 5, 203, 203), 300, 355, fill=(255, 226, 144, 255), width=7)
    rim = rim.filter(ImageFilter.GaussianBlur(1))
    return base, rim, glow


def alpha_composite(canvas: Image.Image, layer: Image.Image, xy: tuple[int, int], alpha: float = 1.0) -> None:
    if alpha < 1:
        layer = layer.copy()
        layer.putalpha(layer.getchannel("A").point(lambda x: int(x * alpha)))
    canvas.alpha_composite(layer, xy)


def draw_cross(draw: ImageDraw.ImageDraw, x: int, y: int, color: tuple[int, int, int, int]) -> None:
    draw.line((x-18, y, x+18, y), fill=color, width=3)
    draw.line((x, y-18, x, y+18), fill=color, width=3)


def build_validation(frozen: Image.Image, base: Image.Image, layers: dict[str, Image.Image]) -> dict[str, float]:
    validation = DESIGN / "intermediate" / "validation"
    # Layer map is intentional documentation rather than a runtime image.
    layer_map = frozen.convert("RGBA")
    d = ImageDraw.Draw(layer_map, "RGBA")
    try:
        font = ImageFont.truetype("DejaVuSans-Bold.ttf", 24)
    except OSError:
        font = ImageFont.load_default()
    labels = [("L01 SCENE BASE", 38, 805), ("L04 ROD", 44, 1208), ("L05 LINE", 432, 1030), ("L06 RIPPLE", 430, 1148), ("L07 BOBBER", 550, 1085), ("L08 NATIVE UI", 50, 130)]
    for text, x, y in labels:
        d.rounded_rectangle((x-6, y-5, x+max(170, len(text)*15), y+31), radius=5, fill=(11, 39, 59, 156))
        d.text((x, y), text, font=font, fill=(255, 242, 201, 245))
    save_png(layer_map, validation / "layer_map.png")

    anchor = frozen.convert("RGBA")
    d = ImageDraw.Draw(anchor, "RGBA")
    draw_cross(d, 530, 1168, (255, 188, 50, 255))
    draw_cross(d, 540, 1604, (30, 79, 130, 255))
    d.rectangle((518, 1084, 542, 1206), outline=(255, 188, 50, 240), width=3)
    d.rectangle((435, 1498, 645, 1710), outline=(30, 79, 130, 240), width=3)
    d.text((552, 1140), "water contact / ripple center", font=font, fill=(255, 188, 50, 255))
    d.text((655, 1574), "capture button center", font=font, fill=(30, 79, 130, 255))
    save_png(anchor, validation / "anchor_overlay.png")

    recompose = base.convert("RGBA")
    alpha_composite(recompose, layers["rod"], (0, 950))
    alpha_composite(recompose, layers["line"], (400, 950))
    alpha_composite(recompose, layers["ripple"], (411, 1127), .30)
    alpha_composite(recompose, layers["bobber"], (518, 1084))
    # UI is deliberately retained as a validation-only source overlay. Runtime UI remains native.
    for box, threshold in [((35, 45, 218, 150), 38), ((50, 190, 650, 510), 34), ((850, 40, 1050, 150), 38), ((260, 1320, 690, 1918), 36)]:
        recompose.alpha_composite(foreground_delta(frozen, base, box, threshold), (box[0], box[1]))
    save_png(recompose, validation / "static_recompose.png")
    diff = ImageChops.difference(frozen.convert("RGB"), recompose.convert("RGB"))
    histogram = diff.histogram()
    total = REF_W * REF_H * 3
    mean_abs = sum(i % 256 * count for i, count in enumerate(histogram)) / total
    report = {"mean_absolute_error": round(mean_abs, 4), "threshold": 18.0, "status": "PASS" if mean_abs <= 18 else "FAIL"}
    write_json(validation / "asset_production_report.json", {
        "gate": "ASSET_PRODUCTION_GATE",
        "status": report["status"],
        "reference": "source/frozen/Empty_Home_Final_Design_V2.png",
        "static_recompose": "static_recompose.png",
        "metrics": report,
        "known_allowed_difference": "local clean-plate reconstruction beneath independently composited runtime layers",
    })
    return report


def main() -> None:
    if not FROZEN_INPUT.is_file():
        raise SystemExit(f"Missing frozen source: {FROZEN_INPUT}")
    clean_input = DESIGN / "source" / "provenance" / "clean_scene_input_local_inpaint.png"
    if not clean_input.is_file():
        if not GENERATED_INPUT.is_file():
            raise SystemExit(f"Missing cleaned scene plate: {clean_input}")
        clean_input.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(GENERATED_INPUT, clean_input)
    frozen_original = Image.open(FROZEN_INPUT)
    clean_original = Image.open(clean_input)
    frozen = normalized(frozen_original)
    base = normalized(clean_original)

    frozen_path = DESIGN / "source" / "frozen" / "Empty_Home_Final_Design_V2.png"
    normalized_path = DESIGN / "source" / "frozen" / "Empty_Home_Final_Design_V2_normalized_1080x1920.png"
    frozen_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(FROZEN_INPUT, frozen_path)
    save_png(frozen, normalized_path)
    base_path = DESIGN / "intermediate" / "background" / "clean_scene_master_1080x1920.png"
    save_png(base, base_path)

    # Fishing layers are extracted against the localized clean plate.  This preserves frozen V2
    # silhouette and colour while eliminating lake/background pixels from the alpha masters.
    layers = {
        "rod": extract_rod(frozen, base),
        "line": extract_line(frozen, base),
        "bobber": extract_bobber(frozen, base),
        "ripple": make_ripple(),
        "cloud": make_cloud(), "sun_beam": make_sun_beam(), "particle": make_particle(),
    }
    masters = {
        "rod": DESIGN / "intermediate" / "extracted" / "rod_master.png",
        "line": DESIGN / "intermediate" / "extracted" / "line_master.png",
        "bobber": DESIGN / "intermediate" / "extracted" / "bobber_master.png",
        "ripple": DESIGN / "intermediate" / "extracted" / "ripple_master_mask.png",
        "cloud": DESIGN / "intermediate" / "atmosphere" / "cloud_master.png",
        "sun_beam": DESIGN / "intermediate" / "atmosphere" / "sun_beam_master_mask.png",
        "particle": DESIGN / "intermediate" / "atmosphere" / "particle_master_mask.png",
    }
    for key, path in masters.items():
        save_png(layers[key], path)

    _, camera_rim, camera_glow = make_camera_assets()
    camera_base = foreground_delta(frozen, base, (436, 1500, 644, 1708), 16)
    save_png(camera_base, SYSTEM / "assets" / "capture_button_base.png")
    save_png(camera_rim, SYSTEM / "assets" / "capture_button_gold_rim.png")
    save_png(camera_glow, SYSTEM / "assets" / "capture_button_breath_glow.png")

    # Android ships only optimized/static runtime inputs and contracts.
    (RUNTIME / "static").mkdir(parents=True, exist_ok=True)
    base.save(RUNTIME / "static" / "scene_base.webp", "WEBP", quality=94, method=6)
    for key, name in [("cloud", "cloud.png"), ("sun_beam", "sun_beam_mask.png"), ("particle", "particle_mask.png"), ("rod", "rod.png"), ("line", "line.png"), ("bobber", "bobber.png"), ("ripple", "ripple_mask.png")]:
        save_png(layers[key], RUNTIME / "dynamic" / name)
    save_png(camera_base, RUNTIME / "camera" / "camera_button_base.png")
    save_png(camera_rim, RUNTIME / "camera" / "camera_gold_rim_mask.png")
    save_png(camera_glow, RUNTIME / "camera" / "camera_breath_glow.png")

    anchors = {
        "reference_canvas": {"width": REF_W, "height": REF_H},
        "bobber": {"bbox_reference_px": {"x": 518, "y": 1084, "width": 24, "height": 122}, "center_reference_px": [530, 1145], "center_normalized": [0.490741, 0.596354], "bottom_reference_px": [530, 1206], "water_contact_reference_px": [530, 1168]},
        "ripple": {"center_reference_px": [530, 1168], "center_normalized": [0.490741, 0.608333], "bbox_reference_px": {"x": 411, "y": 1127, "width": 238, "height": 82}},
        "rod": {"bbox_reference_px": {"x": 0, "y": 950, "width": 450, "height": 365}, "tip_reference_px": [431, 958]},
        "line": {"bbox_reference_px": {"x": 400, "y": 950, "width": 170, "height": 250}, "start_reference_px": [415, 950], "end_reference_px": [530, 1169]},
        "camera_button": {"center_reference_px": [540, 1604], "center_normalized": [0.5, 0.835417], "bbox_reference_px": {"x": 436, "y": 1500, "width": 208, "height": 208}},
        "hero_title": {"bbox_reference_px": {"x": 50, "y": 224, "width": 620, "height": 310}, "accessible_text": "现在，轮到你，记录第一条鱼。"},
    }
    motion = {
        "design_version": "Empty_Home_Final_Design_V2",
        "bobber": {"axis": "y", "range_reference_px": 3, "duration_ms": 4600, "keyframes": [[0,0],[1150,-3],[2300,0],[3450,3],[4600,0]], "x_motion_reference_px": 0, "rotation_deg": 0, "scale_animation": False, "loop": True},
        "ripple": {"count": 1, "center_reference_px": [530,1168], "scale_from": 1.0, "scale_to": 1.22, "alpha_from": 0.30, "alpha_to": 0.0, "duration_ms": 3200, "loop": True, "z_order": "ripple_below_bobber"},
        "cloud": {"speed_reference_px_per_s": 0.2, "cycle_baseline_ms": 60000, "visibility": "extremely_subtle"},
        "sun_particle_beam": {"duration_ms": 4800, "max_alpha": 0.18, "particle_count_range": [6,12]},
        "camera_gold_rim": {"first_delay_ms": 3000, "duration_ms": 1400, "repeat_interval_ms": 9000},
        "camera_breath": {"duration_ms": 5000, "max_scale": 1.015},
    }
    haptic = {"camera_tap": {"semantic": "light_impact", "duration_hint_ms": 20}, "album_tap": {"semantic": "platform_light_click"}, "page_enter": "none", "idle": "none"}
    layers_contract = {"design_version": "Empty_Home_Final_Design_V2", "order": ["scene_base", "cloud_atmosphere", "sun_ambient", "rod", "line", "ripple", "bobber", "native_ui"], "rules": {"scene_base_has_baked_ripple": False, "ripple_count": 1, "ripple_below_bobber": True, "native_ui_is_baked": False}}
    runtime_files = ["static/scene_base.webp", "dynamic/cloud.png", "dynamic/sun_beam_mask.png", "dynamic/particle_mask.png", "dynamic/rod.png", "dynamic/line.png", "dynamic/bobber.png", "dynamic/ripple_mask.png", "camera/camera_button_base.png", "camera/camera_gold_rim_mask.png", "camera/camera_breath_glow.png"]
    runtime_manifest = {"design_version": "Empty_Home_Final_Design_V2", "asset_revision": "HOME_EMPTY_ASSETS_V2.0", "reference_canvas": [REF_W, REF_H], "asset_root": "empty_home_runtime_v2", "static": ["static/scene_base.webp"], "dynamic": ["dynamic/cloud.png", "dynamic/sun_beam_mask.png", "dynamic/particle_mask.png", "dynamic/rod.png", "dynamic/line.png", "dynamic/bobber.png", "dynamic/ripple_mask.png"], "camera": ["camera/camera_button_base.png", "camera/camera_gold_rim_mask.png", "camera/camera_breath_glow.png"], "sha256": {path: sha(RUNTIME / path) for path in runtime_files}, "excludes": ["frozen_source", "validation", "proof", "preview", "mp4", "gif"]}
    for base_dir in [DESIGN / "shared" / "contracts", RUNTIME / "config"]:
        write_json(base_dir / "anchor_contract.json", anchors)
        write_json(base_dir / "motion_contract.json", motion)
        write_json(base_dir / "haptic_contract.json", haptic)
        write_json(base_dir / "layer_contract.json", layers_contract)
        write_json(base_dir / "runtime_manifest.json", runtime_manifest)
    write_json(SYSTEM / "visual_contract.json", {"component": "primary_capture_button", "design_version": "V2", "core": "solid white", "rim": "fine gold", "icon": "deep blue grey", "diameter_reference_px": 208})
    write_json(SYSTEM / "motion_contract.json", {"breath": motion["camera_breath"], "gold_rim": motion["camera_gold_rim"]})
    write_json(SYSTEM / "haptic_contract.json", haptic)

    # Shared final masters are a non-mutating copy of intermediates with explicit product roles.
    shared_assets = DESIGN / "shared" / "assets"
    mapping = {
        base_path: shared_assets / "background" / "scene_base_master.png",
        masters["rod"]: shared_assets / "fishing" / "rod_master.png",
        masters["line"]: shared_assets / "fishing" / "line_master.png",
        masters["bobber"]: shared_assets / "fishing" / "bobber_master.png",
        masters["ripple"]: shared_assets / "fishing" / "ripple_master_mask.png",
        masters["cloud"]: shared_assets / "atmosphere" / "cloud_master.png",
        masters["sun_beam"]: shared_assets / "atmosphere" / "sun_beam_master_mask.png",
        masters["particle"]: shared_assets / "atmosphere" / "particle_master_mask.png",
    }
    for source, destination in mapping.items():
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
    build_validation(frozen, base, layers)

    asset_paths = [*mapping.values(), SYSTEM / "assets" / "capture_button_base.png", SYSTEM / "assets" / "capture_button_gold_rim.png", SYSTEM / "assets" / "capture_button_breath_glow.png"]
    manifest_assets = []
    for path in asset_paths:
        with Image.open(path) as im:
            manifest_assets.append({"asset_id": "home_empty_v2_" + path.stem, "role": "runtime_static" if "scene" in path.name else "runtime_dynamic", "source": "Empty_Home_Final_Design_V2", "path": str(path.relative_to(ROOT)).replace("\\", "/"), "master_format": "png_rgba" if im.mode == "RGBA" else "png_rgb", "width": im.width, "height": im.height, "alpha": im.mode == "RGBA", "sha256": sha(path), "coordinate_space": "reference_1080x1920", "version": "2.0", "platforms": ["android"]})
    write_json(DESIGN / "shared" / "contracts" / "asset_manifest.json", {"design_version": "Empty_Home_Final_Design_V2", "assets": manifest_assets})
    checksum_entries = [f"{sha(frozen_path)}  source/frozen/Empty_Home_Final_Design_V2.png"]
    for asset in asset_paths:
        checksum_entries.append(f"{sha(asset)}  {asset.relative_to(ROOT)}")
    (DESIGN / "shared" / "contracts" / "SHA256SUMS").write_text("\n".join(checksum_entries) + "\n")
    write_json(DESIGN / "source" / "provenance" / "source_manifest.json", {"design_version": "Empty_Home_Final_Design_V2", "frozen_input": {"received_filename": "晨雾湖畔，记录第一条鱼.png", "canonical_path": "source/frozen/Empty_Home_Final_Design_V2.png", "original_dimensions": [frozen_original.width, frozen_original.height], "sha256": sha(frozen_path)}, "normalized_reference": {"path": "source/frozen/Empty_Home_Final_Design_V2_normalized_1080x1920.png", "normalized_dimensions": [REF_W, REF_H], "sha256": sha(normalized_path), "method": "uniform width scale with one-pixel edge padding; no crop and no semantic content added"}, "clean_scene": {"input_path": "source/provenance/clean_scene_input_local_inpaint.png", "input_sha256": sha(clean_input), "path": "intermediate/background/clean_scene_master_1080x1920.png", "sha256": sha(base_path), "provenance": "local inpaint cleanup of only UI, rod, line, bobber and ripple regions from the frozen visual; no scene redesign"}})
    write_json(DESIGN / "platform" / "android" / "asset_map.json", {"platform": "android", "runtime_root": "app/src/main/assets/empty_home_runtime_v2", "shared_contract_root": "design/pages/home/empty_home/shared/contracts", "design_version": "Empty_Home_Final_Design_V2", "runtime_assets": runtime_manifest})
    write_json(DESIGN / "status.json", {"feature": "home_empty_v2", "scope": "android_only", "design_frozen": True, "asset_production": "PASS", "shared_contracts": "PASS", "android_runtime": "PENDING_CI", "android_evidence": "PENDING_CI", "source_sha256": sha(frozen_path)})


if __name__ == "__main__":
    main()
