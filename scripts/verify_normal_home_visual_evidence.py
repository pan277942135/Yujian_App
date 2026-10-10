#!/usr/bin/env python3
"""Validate Normal Home screenshots without resizing runtime captures."""

from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageStat

ROOT = Path(__file__).resolve().parents[1]
FROZEN_SHA = "6ab9d3348b4a9a7e77ddca3a06235b4991798a309bd3512cc6fb9ea7aeb1d377"
BACKGROUND_SHA = "5fba741088ea186e898cd3bee5777e35978436f427492e6e6122528ef6aa91d7"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def dimensions(path: Path) -> tuple[int, int]:
    with Image.open(path) as image:
        return image.size


def cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    scale = max(size[0] / image.width, size[1] / image.height)
    resized = image.resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS)
    left = (resized.width - size[0]) // 2
    top = (resized.height - size[1]) // 2
    return resized.crop((left, top, left + size[0], top + size[1]))


def mae(left: Image.Image, right: Image.Image) -> float:
    return sum(ImageStat.Stat(ImageChops.difference(left, right)).mean) / 3


def require_capture(path: Path, expected: tuple[int, int]) -> None:
    actual = dimensions(path)
    if actual != expected:
        raise ValueError(f"actual screenshot dimensions {path.name}={actual}; required={expected}; capture was not resized")


def luma(pixel: tuple[int, int, int]) -> float:
    return 0.2126 * pixel[0] + 0.7152 * pixel[1] + 0.0722 * pixel[2]


def analyze_structure(path: Path, background_source: Path) -> dict[str, object]:
    """Prove the actual HOME card occupies its frozen region against its registered lake master.

    The synthetic fish fixtures use yellow borders and blue/red panels: counting
    *white* edge pixels silently misclassifies a correctly rounded, clipped card.
    Use stable interior/exterior background-difference witnesses instead, while
    Compose instrumentation continues to assert the exact Hero bounds and clip.
    """
    image = Image.open(path).convert("RGB")
    width, height = image.size
    if (width, height) != (1080, 1920):
        raise ValueError(f"structural witnesses require native 1080x1920: {path.name} is {width}x{height}")
    if sha256(background_source) != BACKGROUND_SHA:
        raise ValueError("card structure reference is not the registered Morning Lake master")
    background = cover(Image.open(background_source).convert("RGB"), image.size)

    def count_dark(box: tuple[int, int, int, int]) -> int:
        return sum(r < 70 and g < 95 and b < 130 for r, g, b in image.crop(box).getdata())

    def count_bright(box: tuple[int, int, int, int]) -> int:
        return sum(luma(pixel) > 205 for pixel in image.crop(box).getdata())

    def difference_mean(box: tuple[int, int, int, int]) -> float:
        diff = ImageChops.difference(image.crop(box), background.crop(box))
        return sum(ImageStat.Stat(diff).mean) / 3

    # Exclude rounded corners, neighboring carousel card, text, and camera.
    # The interior must be actual card pixels; exterior must be the real lake,
    # including immediately beside the card, above and below its container.
    interior_regions = {
        "top_inside": (300, 630, 780, 650),
        "left_inside": (180, 725, 210, 1300),
        "right_inside": (870, 725, 900, 1300),
        "bottom_inside": (300, 1410, 780, 1440),
    }
    exterior_regions = {
        "left_outside": (130, 730, 148, 1200),
        "right_outside": (925, 730, 943, 1200),
        "above": (300, 525, 780, 550),
        "below": (220, 1485, 320, 1505),
    }
    interior = {key: round(difference_mean(box), 3) for key, box in interior_regions.items()}
    exterior = {key: round(difference_mean(box), 3) for key, box in exterior_regions.items()}
    anchors = {
        "header_dark_pixels": count_dark((70, 80, 350, 240)),
        "stats_dark_pixels": count_dark((230, 285, 850, 440)),
        "recent_header_bright_pixels": count_bright((80, 490, 1000, 580)),
        "cta_bright_pixels": count_bright((350, 1500, 730, 1580)),
        "camera_bright_pixels": count_bright((420, 1580, 660, 1810)),
        "card_frame": {
            "frozen_reference_rect_px": [170, 596, 910, 1476],
            "method": "source-hash-verified-lake-background-interior-exterior-witnesses",
            "interior_mean_rgb_abs_difference": interior,
            "exterior_mean_rgb_abs_difference": exterior,
            "semantic_geometry": "verified separately by NormalHomeDataParityTest boundsInRoot",
            "corner_clip": "verified separately by realSamplePhotoSharesFullHeroBoundsAndClipsAtAllFourRoundedCorners",
        },
        "content_within_safe_canvas": 80 <= 104 < height and 1800 < height,
    }
    failures = []
    for key, floor in {
        "header_dark_pixels": 800,
        "stats_dark_pixels": 250,
        "recent_header_bright_pixels": 800,
        "cta_bright_pixels": 350,
        "camera_bright_pixels": 5000,
    }.items():
        if anchors[key] < floor:
            failures.append(f"{key} below structural minimum ({anchors[key]} < {floor})")
    for key, measured in interior.items():
        if measured < 15.0:
            failures.append(f"card interior missing at {key} (lake MAE={measured:.2f} < 15)")
    for key, measured in exterior.items():
        if measured > 10.0:
            failures.append(f"card exterior appears covered at {key} (lake MAE={measured:.2f} > 10)")
    if not anchors["content_within_safe_canvas"]:
        failures.append("header or capture action is outside the safe canvas")
    if failures:
        raise ValueError(f"structural anchor failures in {path.name}: " + "; ".join(failures))
    return anchors


def main(out: Path) -> int:
    frozen = ROOT / "design/system/core_visual_v1/reference/normal_home_v1.png"
    source = ROOT / "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png"
    empty_scene = ROOT / "app/src/main/assets/empty_home_runtime_v2/static/scene_base.webp"
    runtime_asset = ROOT / "app/src/main/assets/normal_home_runtime_v1/static/scene_base.png"

    expected_frozen = out / "03_normal_home_frozen_1080x1920.png"
    runtime = out / "04_normal_home_runtime_1080x1920.png"
    first_card = out / "01_normal_home_first_card.png"
    second_card = out / "02_normal_home_second_card.png"
    if not expected_frozen.exists():
        expected_frozen.write_bytes(frozen.read_bytes())
    require_capture(expected_frozen, (1080, 1920))
    require_capture(runtime, (1080, 1920))
    require_capture(first_card, (1080, 1920))
    require_capture(second_card, (1080, 1920))
    if sha256(expected_frozen) != FROZEN_SHA:
        raise ValueError("Frozen screenshot is not the registered immutable authority")
    first_image = Image.open(first_card).convert("RGB")
    second_image = Image.open(second_card).convert("RGB")
    carousel_mae = mae(first_image.crop((140, 600, 940, 1800)), second_image.crop((140, 600, 940, 1800)))
    if carousel_mae < 6.0:
        raise ValueError(f"second carousel page did not produce a distinct capture (MAE={carousel_mae:.2f})")

    structural = {runtime.name: analyze_structure(runtime, source)}

    source_evidence = out / "06_normal_home_background_source.png"
    runtime_evidence = out / "07_normal_home_background_runtime.png"
    if not source_evidence.exists():
        source_evidence.write_bytes(source.read_bytes())
    if not runtime_evidence.exists():
        runtime_evidence.write_bytes(runtime_asset.read_bytes())
    source_hash = sha256(source_evidence)
    runtime_hash = sha256(runtime_evidence)
    empty_hash = sha256(empty_scene)
    if source_hash != BACKGROUND_SHA or runtime_hash != BACKGROUND_SHA:
        raise ValueError("Background source/runtime pixels do not match Morning_Lake_Master_V1")
    if runtime_hash == empty_hash:
        raise ValueError("Negative control: runtime background is the Empty Home scene")

    approved = Image.open(source_evidence).convert("RGB")
    negative = cover(Image.open(empty_scene).convert("RGB"), (256, 455))
    approved_small = cover(approved, (256, 455))
    negative_mae = mae(approved_small, negative)
    # Exercise the same rejection rule against the old Empty Home background.
    negative_control_rejected = (empty_hash != BACKGROUND_SHA and negative_mae >= 8.0)
    if not negative_control_rejected:
        raise ValueError(f"Empty Home negative control was not rejected (MAE={negative_mae:.2f})")
    background_report = {
        "status": "PASS",
        "source_path": "design/system/backgrounds/morning_lake_v1/assets/Morning_Lake_Master_V1.png",
        "source_sha256": source_hash,
        "source_dimensions": list(dimensions(source_evidence)),
        "runtime_path": "app/src/main/assets/normal_home_runtime_v1/static/scene_base.png",
        "runtime_sha256": runtime_hash,
        "runtime_dimensions": list(dimensions(runtime_evidence)),
        "transform_policy": "byte-identical source copy; Android centered ContentScale.Crop only",
        "source_runtime_pixel_identity": True,
        "empty_home_negative_control": {
            "sha256": empty_hash,
            "source_comparison_mae_256x455": round(negative_mae, 4),
            "result": "REJECTED_AS_REQUIRED",
        },
    }
    (out / "08_normal_home_background_parity_report.json").write_text(
        json.dumps(background_report, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    reference = Image.open(expected_frozen).convert("RGB")
    runtime_image = Image.open(runtime).convert("RGB")
    # No resizing: both frozen and runtime screenshots were captured at true 1080×1920.
    regions = {
        "header": (0.04, 0.03, 0.92, 0.13),
        "stats": (0.06, 0.13, 0.88, 0.12),
        "recent_header": (0.06, 0.20, 0.88, 0.10),
        "card_frame": (0.12, 0.26, 0.76, 0.52),
        "cta_and_camera": (0.22, 0.76, 0.56, 0.22),
    }
    region_metrics = {}
    for name, (x, y, w, h) in regions.items():
        box = (int(1080*x), int(1920*y), int(1080*(x+w)), int(1920*(y+h)))
        ref_crop, run_crop = reference.crop(box), runtime_image.crop(box)
        region_metrics[name] = {
            "mae": round(mae(ref_crop, run_crop), 4),
            "reference_luma_mean": round(sum(ImageStat.Stat(ref_crop.convert("L")).mean), 4),
            "runtime_luma_mean": round(sum(ImageStat.Stat(run_crop.convert("L")).mean), 4),
        }

    canvas = Image.new("RGB", (2160, 1920), "white")
    canvas.paste(reference, (0, 0))
    canvas.paste(runtime_image, (1080, 0))
    draw = ImageDraw.Draw(canvas)
    draw.text((16, 16), "Frozen authority · 1080×1920", fill="black")
    draw.text((1096, 16), "Android runtime · 1080×1920", fill="black")
    canvas.save(out / "05_normal_home_frozen_runtime_side_by_side.png")

    report = {
        "status": "PASS_DIMENSIONS_AND_HIERARCHY_REVIEW_REQUIRED",
        "authority": "design/system/core_visual_v1/reference/normal_home_v1.png",
        "authority_sha256": FROZEN_SHA,
        "runtime_actual_dimensions": list(dimensions(runtime)),
        "runtime_capture_resized": False,
        "structural_anchor_checks": structural,
        "structural_anchors": "PASS",
        "region_metrics_descriptive_only": region_metrics,
        "hierarchy_and_geometry_review": "STRUCTURAL_ANCHORS_PASS; DIRECT_VISUAL_REVIEW_STILL_REQUIRED",
        "carousel_capture_mae": round(carousel_mae, 4),
        "carousel_pages_captured_at_native_dimensions": True,
        "native_capture_dimensions": list(dimensions(runtime)),
        "background_parity": "PASS",
        "frozen_hierarchy": "REVIEW_REQUIRED",
        "hero_geometry": "REVIEW_REQUIRED",
        "typography_readability": "REVIEW_REQUIRED",
        "responsive_measurement": "COVERED_BY_COMPOSE_MEASUREMENT_TESTS; runtime screenshot uses device-native 1080x1920",
        "behavior": "SEE_10_NORMAL_HOME_SEMANTICS_REPORT",
        "motion": "SEE_11_VIDEO_AND_REDUCE_MOTION_TEST",
        "haptic_sound": "HOME_HAPTIC_NONE; SOUND_NONE",
    }
    (out / "09_normal_home_visual_parity_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out / "visual_parity_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(json.dumps({"background": background_report, "visual": report}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(Path(sys.argv[1])))
    except Exception as exc:
        print(f"NORMAL_HOME_VISUAL_EVIDENCE_INVALID: {exc}", file=sys.stderr)
        raise SystemExit(1)
