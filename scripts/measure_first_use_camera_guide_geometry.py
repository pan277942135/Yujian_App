#!/usr/bin/env python3
"""Measure visible text/control bounds from the frozen P01/P02/P03 raster sources.

Bounding boxes use half-open native PNG pixel coordinates. Text bounds are derived
from core foreground pixels inside the named semantic regions. The example-card
bounds are derived from source row/column gradient edges. Photo/typography/alpha
separation is not inferred from these flattened references.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Callable

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
GUIDE = ROOT / "design/pages/recognition/first_use_camera_guide"
IMAGE_PATHS = {
    "p01": "frozen/P01_First_Use_Camera_Guide_Frozen.png",
    "p02": "frozen/P02_First_Use_Camera_Guide_Frozen.png",
    "p03": "frozen/P03_First_Use_Camera_Guide_Frozen.png",
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def bbox_obj(box: tuple[int, int, int, int] | None) -> dict | None:
    if box is None:
        return None
    x0, y0, x1, y1 = box
    return {"x": x0, "y": y0, "width": x1 - x0, "height": y1 - y0}


def rect_from_union(items: list[dict | None]) -> dict | None:
    boxes = [x for x in items if x]
    if not boxes:
        return None
    x0 = min(x["x"] for x in boxes)
    y0 = min(x["y"] for x in boxes)
    x1 = max(x["x"] + x["width"] for x in boxes)
    y1 = max(x["y"] + x["height"] for x in boxes)
    return bbox_obj((x0, y0, x1, y1))


def mask_box(image: Image.Image, roi: tuple[int, int, int, int], predicate: Callable[[int, int, int], bool]) -> dict | None:
    x0, y0, x1, y1 = roi
    pixels = image.load()
    found_x: list[int] = []
    found_y: list[int] = []
    for y in range(y0, y1):
        for x in range(x0, x1):
            r, g, b = pixels[x, y]
            if predicate(r, g, b):
                found_x.append(x)
                found_y.append(y)
    if not found_x:
        return None
    return bbox_obj((min(found_x), min(found_y), max(found_x) + 1, max(found_y) + 1))


def dark(r: int, g: int, b: int) -> bool:
    return (r + g + b) / 3 < 125 and max(r, g, b) - min(r, g, b) < 115


def white_core(r: int, g: int, b: int) -> bool:
    return min(r, g, b) > 215 and max(r, g, b) - min(r, g, b) < 28


def chromatic_overlay(r: int, g: int, b: int) -> bool:
    return (r + g + b) / 3 > 155 and max(r, g, b) - min(r, g, b) > 22


def detect_card_boxes(image: Image.Image) -> tuple[list[dict], dict]:
    pixels = image.load()
    width, height = image.size

    def luminance(x: int, y: int) -> float:
        r, g, b = pixels[x, y]
        return (r + g + b) / 3

    def row_edge(y: int) -> float:
        return sum(abs(luminance(x, y + 1) - luminance(x, y)) for x in range(40, min(905, width))) / 865

    top = max(range(1060, 1090), key=row_edge)
    bottom = max(range(1235, 1260), key=row_edge)
    y0, y1 = top + 20, bottom - 20
    spans = [(45, 100), (315, 345), (345, 375), (590, 625), (620, 650), (870, 900)]
    edges = []
    for start, end in spans:
        scores = []
        for x in range(start, end):
            score = sum(abs(luminance(x + 1, y) - luminance(x, y)) for y in range(y0, y1)) / (y1 - y0)
            scores.append((score, x))
        edges.append(max(scores)[1])
    boxes = []
    for i in range(0, 6, 2):
        left, right = edges[i], edges[i + 1]
        if right <= left:
            raise ValueError(f"invalid card edge measurements: {edges}")
        boxes.append(bbox_obj((left, top, right + 2, bottom + 1)))
    return boxes, {"row_edge_peak_top": top, "row_edge_peak_bottom": bottom, "column_edge_peaks": edges}


def normalized(box: dict | None, width: int, height: int) -> dict | None:
    if box is None:
        return None
    return {
        "x": round(box["x"] / width, 6),
        "y": round(box["y"] / height, 6),
        "width": round(box["width"] / width, 6),
        "height": round(box["height"] / height, 6),
    }


def measure_page(page_id: str) -> dict:
    relative = IMAGE_PATHS[page_id]
    path = GUIDE / relative
    raw = path.read_bytes()
    image = Image.open(path).convert("RGB")
    width, height = image.size
    if (width, height) not in ((941, 1671), (941, 1672)):
        raise ValueError(f"unexpected frozen page dimensions for {page_id}: {width}x{height}")

    dark_roi = lambda roi: mask_box(image, roi, dark)
    white_roi = lambda roi: mask_box(image, roi, white_core)
    color_roi = lambda roi: mask_box(image, roi, chromatic_overlay)

    cards, edge_evidence = detect_card_boxes(image)
    caption_boxes = []
    for card in cards:
        roi = (max(40, card["x"] - 4), 1250, min(910, card["x"] + card["width"] + 4), 1340)
        caption_boxes.append(white_roi(roi))

    components: dict[str, dict | None] = {
        "guide.background": bbox_obj((0, 0, width, height)),
        "guide.overlay": bbox_obj((0, 0, width, height)),
        "guide.back_action": dark_roi((35, 40, 105, 115)),
        "guide.masthead_title": dark_roi((300, 40, 650, 112)),
        "guide.masthead_subtitle": dark_roi((200, 105, 760, 165)),
        # The tiny page number sits over a bright hand on P01/P03; its box was
        # bounded by direct native-pixel review and is intentionally not OCR-derived.
        "guide.step_indicator": bbox_obj((70, 835, 178, 862)),
        "guide.title": white_roi((58, 880, 555, 965)),
        "guide.support_copy": white_roi((58, 985, 850, 1045)),
        "guide.error.head_out": cards[0],
        "guide.error.too_small": cards[1],
        "guide.error.occlusion": cards[2],
        "guide.error.head_out.caption": caption_boxes[0],
        "guide.error.too_small.caption": caption_boxes[1],
        "guide.error.occlusion.caption": caption_boxes[2],
        "guide.completion_hint": color_roi((230, 1370, 710, 1440)),
        "guide.pagination": color_roi((380, 1470, 560, 1510)),
        "guide.skip_action": white_roi((60, 1520, 155, 1605)),
        "guide.previous_action": white_roi((185, 1520, 340, 1605)) if page_id != "p01" else None,
        "guide.primary_action": white_roi((540, 1485, 900, 1620)),
    }
    components["guide.header"] = rect_from_union([
        components["guide.back_action"], components["guide.masthead_title"], components["guide.masthead_subtitle"]
    ])
    components["guide.hero"] = bbox_obj((58, 198, 884, 768))
    components["guide.error_examples"] = rect_from_union([
        components["guide.error.head_out"], components["guide.error.too_small"], components["guide.error.occlusion"],
        components["guide.error.head_out.caption"], components["guide.error.too_small.caption"], components["guide.error.occlusion.caption"],
    ])
    components["guide.intro"] = rect_from_union([
        components["guide.step_indicator"], components["guide.title"], components["guide.support_copy"]
    ])
    components["guide.actions"] = rect_from_union([
        components["guide.skip_action"], components["guide.previous_action"], components["guide.primary_action"]
    ])

    methods = {
        "guide.background": "exact_png_canvas_bounds",
        "guide.overlay": "full_page_teaching_overlay_reference_extent",
        "guide.back_action": "foreground_core_threshold_rgb_luma_lt_125_in_roi",
        "guide.masthead_title": "foreground_core_threshold_rgb_luma_lt_125_in_roi",
        "guide.masthead_subtitle": "foreground_core_threshold_rgb_luma_lt_125_in_roi",
        "guide.step_indicator": "manual_native_pixel_boundary_review; OCR_not_used",
        "guide.title": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.support_copy": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.error.head_out": "mean_luminance_row_column_gradient_edges",
        "guide.error.too_small": "mean_luminance_row_column_gradient_edges",
        "guide.error.occlusion": "mean_luminance_row_column_gradient_edges",
        "guide.error.head_out.caption": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.error.too_small.caption": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.error.occlusion.caption": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.completion_hint": "chromatic_foreground_core_threshold_in_roi",
        "guide.pagination": "chromatic_foreground_core_threshold_in_roi",
        "guide.skip_action": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.previous_action": "white_core_threshold_min_channel_gt_215_in_roi",
        "guide.primary_action": "white_core_threshold_min_channel_gt_215_in_roi; visible_button_fill_not_touch_target",
        "guide.header": "union_of_measured_visible_content_bounds",
        "guide.hero": "manual_native_pixel_boundary_review_of_bracket_markers; inner_photo_is_composited",
        "guide.error_examples": "union_of_measured_card_and_caption_bounds",
        "guide.intro": "union_of_measured_visible_text_bounds",
        "guide.actions": "union_of_visible_control_bounds",
    }
    frozen_manifest = json.loads((GUIDE / "manifest.json").read_text(encoding="utf-8"))
    manifest_asset = next(item for item in frozen_manifest["assets"] if item["id"] == page_id)
    expected_sha = manifest_asset["sha256"]
    if digest(raw) != expected_sha:
        raise ValueError(f"frozen PNG bytes changed for {page_id}")

    measured = {}
    for component_id, box in components.items():
        measured[component_id] = {
            "bbox_px": box,
            "bbox_normalized": normalized(box, width, height),
            "measurement_status": "MEASURED_FROM_FROZEN_RASTER" if box else "NOT_VISIBLE_ON_THIS_PAGE",
            "measurement_method": methods[component_id],
            "coordinate_convention": "top_left_origin_half_open_xywh",
        }

    # Reversibility assertions: all measured visible boxes stay on the source canvas;
    # only background may touch every edge.
    for component_id, value in measured.items():
        box = value["bbox_px"]
        if not box:
            continue
        if box["x"] < 0 or box["y"] < 0 or box["x"] + box["width"] > width or box["y"] + box["height"] > height:
            raise ValueError(f"out-of-canvas measured geometry {page_id}:{component_id}={box}")

    return {
        "page_id": page_id,
        "source_path": f"design/pages/recognition/first_use_camera_guide/{relative}",
        "source_sha256": expected_sha,
        "source_dimensions_px": {"width": width, "height": height},
        "coordinate_system": "native_frozen_png_pixels; origin top-left; x right; y down; bbox half-open",
        "outer_page_orientation": "portrait_reference_artboard; runtime_viewport_orientation_is_responsive",
        "camera_capture_orientation": "landscape" if page_id == "p03" else "portrait",
        "fish_orientation": "vertical" if page_id == "p02" else "horizontal",
        "card_edge_evidence": edge_evidence,
        "components": measured,
        "non_recoverable_from_flattened_png": [
            "editable source layers and original photo crops",
            "source alpha before compositing",
            "font family, declared sp size and line-height",
            "invisible hit target extents",
            "original DOM/CSS z-index values",
            "independent background and hero source-photo files",
        ],
    }


def build_report() -> dict:
    pages = {page_id: measure_page(page_id) for page_id in ("p01", "p02", "p03")}
    return {
        "schema_version": "1.0",
        "task_id": "YUJIAN_FIRST_USE_GUIDE_PREDEV_CONTRACT_ASSETS_RESPONSIVE_FREEZE_V1",
        "measurement_status": "VISIBLE_GEOMETRY_MEASURED; FLATTENED_STYLE_AND_LAYER_SEPARATION_NOT_RECOVERABLE",
        "source_rule": "Only the registered Frozen PNGs are geometry inputs; no screenshots were rescaled, cropped into runtime assets or overwritten.",
        "measurement_tool": "scripts/measure_first_use_camera_guide_geometry.py",
        "pages": pages,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="compare recomputed measurements with committed JSON")
    parser.add_argument("--output", type=Path, default=GUIDE / "geometry_measurements.json")
    args = parser.parse_args()
    generated = build_report()
    rendered = json.dumps(generated, ensure_ascii=False, indent=2) + "\n"
    if args.check:
        current = args.output.read_text(encoding="utf-8")
        if current != rendered:
            raise SystemExit("FAIL: geometry_measurements.json does not match source PNG pixels; regenerate intentionally")
        print("FIRST_USE_GUIDE_GEOMETRY=PASS (measured boxes match frozen source PNGs)")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8")
    print(f"FIRST_USE_GUIDE_GEOMETRY=WRITTEN {args.output.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
