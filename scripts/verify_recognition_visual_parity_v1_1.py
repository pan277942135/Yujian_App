#!/usr/bin/env python3
"""Frozen Recognition V1.1 visual parity gate.

Runtime photography and Android system bars may differ from Frozen sources, so
this gate compares structure inside UI-dominant normalized ROIs rather than
requiring pixel identity.
"""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageFilter, ImageOps

FILES = [
    ("01_image_recognizing_early.png", "01_Capture_Transition_Frozen.png", "processing_card"),
    ("02_image_recognizing_late.png", "02_AI_Understanding_Frozen.png", "processing_light"),
    ("03_fish_located.png", "03_Fish_Highlight_Frozen.png", "processing_card"),
    ("04_species_recognizing.png", "04_Fish_Identifying_Frozen.png", "processing_card"),
    ("05_result_high.png", "05_Result_High_Frozen.png", "result"),
    ("06_result_medium.png", "06_Result_Medium_Frozen.png", "result"),
    ("07_result_low.png", "07_Result_Low_Frozen.png", "result"),
    ("08_error_no_fish.png", "08_Error_No_Fish_Frozen.png", "error"),
    ("09_error_image_quality.png", "09_Error_Image_Quality_Frozen.png", "error"),
]

ROI_RULES = {
    "processing_card": [
        ("nav", (0.00, 0.03, 0.22, 0.16), 0.36),
        ("status", (0.02, 0.76, 0.98, 0.99), 0.48),
    ],
    "processing_light": [
        ("nav", (0.00, 0.03, 0.22, 0.16), 0.34),
        ("status", (0.02, 0.69, 0.90, 0.95), 0.40),
    ],
    "result": [
        ("header", (0.00, 0.03, 1.00, 0.16), 0.38),
        ("content", (0.02, 0.38, 0.98, 0.96), 0.50),
    ],
    "error": [
        ("header", (0.00, 0.03, 1.00, 0.16), 0.36),
        ("content", (0.03, 0.38, 0.97, 0.94), 0.48),
    ],
}

STATE_THRESHOLDS = {
    "processing_card": 0.48,
    "processing_light": 0.42,
    "result": 0.50,
    "error": 0.48,
}


def crop_norm(image: Image.Image, box: tuple[float, float, float, float]) -> Image.Image:
    width, height = image.size
    left, top, right, bottom = box
    return image.crop((
        int(round(left * width)),
        int(round(top * height)),
        int(round(right * width)),
        int(round(bottom * height)),
    ))


def cosine(left: Iterable[float], right: Iterable[float]) -> float:
    a, b = list(left), list(right)
    if len(a) != len(b) or not a:
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    la = math.sqrt(sum(x * x for x in a))
    lb = math.sqrt(sum(y * y for y in b))
    if la <= 1e-9 or lb <= 1e-9:
        return 1.0 if la <= 1e-9 and lb <= 1e-9 else 0.0
    return max(0.0, min(1.0, dot / (la * lb)))


def centered_similarity(left: Iterable[float], right: Iterable[float]) -> float:
    a, b = list(left), list(right)
    if len(a) != len(b) or not a:
        return 0.0
    ma, mb = sum(a) / len(a), sum(b) / len(b)
    ca, cb = [x - ma for x in a], [y - mb for y in b]
    denom = math.sqrt(sum(x * x for x in ca) * sum(y * y for y in cb))
    if denom <= 1e-9:
        return 1.0
    corr = sum(x * y for x, y in zip(ca, cb)) / denom
    return max(0.0, min(1.0, (corr + 1.0) / 2.0))


def projections(image: Image.Image, size: int = 64) -> tuple[list[float], list[float]]:
    edge = ImageOps.grayscale(image).resize((size, size), Image.Resampling.LANCZOS)
    edge = edge.filter(ImageFilter.GaussianBlur(1.2)).filter(ImageFilter.FIND_EDGES)
    pixels = list(edge.getdata())
    rows = [sum(pixels[y * size:(y + 1) * size]) / (255.0 * size) for y in range(size)]
    cols = [sum(pixels[x + y * size] for y in range(size)) / (255.0 * size) for x in range(size)]
    return rows, cols


def tone_profiles(image: Image.Image, size: int = 32) -> tuple[list[float], list[float]]:
    gray = ImageOps.grayscale(image).resize((size, size), Image.Resampling.LANCZOS)
    gray = gray.filter(ImageFilter.GaussianBlur(3.0))
    pixels = list(gray.getdata())
    rows = [sum(pixels[y * size:(y + 1) * size]) / (255.0 * size) for y in range(size)]
    cols = [sum(pixels[x + y * size] for y in range(size)) / (255.0 * size) for x in range(size)]
    return rows, cols


def categories(image: Image.Image) -> dict[str, float]:
    sample = image.convert("RGB").resize((48, 48), Image.Resampling.LANCZOS)
    counts = {"dark_teal": 0, "bright_neutral": 0, "gold": 0, "teal": 0}
    total = 48 * 48
    for red, green, blue in sample.getdata():
        chroma = max(red, green, blue) - min(red, green, blue)
        if red < 80 and green < 105 and blue < 120:
            counts["dark_teal"] += 1
        if min(red, green, blue) >= 210 and chroma <= 45:
            counts["bright_neutral"] += 1
        if red >= 190 and green >= 145 and blue <= 205 and red >= green:
            counts["gold"] += 1
        if green >= 105 and blue >= 85 and red <= 125:
            counts["teal"] += 1
    return {key: value / total for key, value in counts.items()}


def coverage_similarity(runtime: dict[str, float], frozen: dict[str, float]) -> float:
    scores = []
    for key, ref_value in frozen.items():
        if ref_value < 0.012:
            continue
        ratio = (runtime[key] + 0.008) / (ref_value + 0.008)
        scores.append(math.exp(-abs(math.log(ratio))))
    return sum(scores) / len(scores) if scores else 1.0


def ui_centroid(image: Image.Image) -> tuple[float, float] | None:
    sample = image.convert("RGB").resize((64, 64), Image.Resampling.LANCZOS)
    points = []
    for index, (red, green, blue) in enumerate(sample.getdata()):
        chroma = max(red, green, blue) - min(red, green, blue)
        ui_like = (
            (red < 80 and green < 105 and blue < 120)
            or (min(red, green, blue) >= 210 and chroma <= 45)
            or (red >= 190 and green >= 145 and blue <= 205 and red >= green)
            or (green >= 105 and blue >= 85 and red <= 125)
        )
        if ui_like:
            points.append((index % 64, index // 64))
    if len(points) < 10:
        return None
    return (
        sum(x for x, _ in points) / len(points) / 63.0,
        sum(y for _, y in points) / len(points) / 63.0,
    )


def centroid_similarity(runtime: Image.Image, frozen: Image.Image) -> float:
    run, ref = ui_centroid(runtime), ui_centroid(frozen)
    if run is None or ref is None:
        return 1.0
    distance = math.hypot(run[0] - ref[0], run[1] - ref[1])
    return max(0.0, 1.0 - distance / 0.42)


def roi_score(runtime: Image.Image, frozen: Image.Image) -> dict[str, float]:
    rr, rc = projections(runtime)
    fr, fc = projections(frozen)
    structure = (cosine(rr, fr) + cosine(rc, fc)) / 2.0
    tr, tc = tone_profiles(runtime)
    ftr, ftc = tone_profiles(frozen)
    tone = (centered_similarity(tr, ftr) + centered_similarity(tc, ftc)) / 2.0
    coverage = coverage_similarity(categories(runtime), categories(frozen))
    centroid = centroid_similarity(runtime, frozen)
    combined = structure * 0.45 + tone * 0.20 + coverage * 0.20 + centroid * 0.15
    return {
        "structure": round(structure, 4),
        "tone": round(tone, 4),
        "coverage": round(coverage, 4),
        "centroid": round(centroid, 4),
        "combined": round(combined, 4),
    }


def annotate(image: Image.Image, rois) -> Image.Image:
    copy = image.convert("RGB").copy()
    draw = ImageDraw.Draw(copy)
    for name, box, _ in rois:
        width, height = copy.size
        coords = (
            int(box[0] * width), int(box[1] * height),
            int(box[2] * width), int(box[3] * height),
        )
        draw.rectangle(coords, outline=(255, 255, 255), width=max(1, width // 220))
        draw.text((coords[0] + 4, coords[1] + 4), name, fill=(255, 255, 255))
    return copy


def make_contact(rows, output_path: Path) -> None:
    panel_w, panel_h, label_h = 240, 426, 26
    canvas = Image.new("RGB", (panel_w * 3, (panel_h + label_h) * len(rows)), (16, 16, 16))
    draw = ImageDraw.Draw(canvas)
    for index, row in enumerate(rows):
        y = index * (panel_h + label_h)
        frozen = row["frozen"].resize((panel_w, panel_h), Image.Resampling.LANCZOS)
        runtime = row["runtime"].resize((panel_w, panel_h), Image.Resampling.LANCZOS)
        blend = Image.blend(frozen, runtime, 0.5)
        canvas.paste(frozen, (0, y + label_h))
        canvas.paste(runtime, (panel_w, y + label_h))
        canvas.paste(blend, (panel_w * 2, y + label_h))
        draw.text((4, y + 6), "FROZEN " + row["name"], fill=(240, 240, 240))
        draw.text((panel_w + 4, y + 6), "RUNTIME", fill=(240, 240, 240))
        draw.text((panel_w * 2 + 4, y + 6), "BLEND", fill=(240, 240, 240))
    canvas.save(output_path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runtime-dir", type=Path, required=True)
    parser.add_argument("--reference-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    result = {"version": "RECOGNITION_VISUAL_PARITY_v1_2", "states": [], "classification": "PASS"}
    contact_rows = []

    for runtime_name, frozen_name, kind in FILES:
        runtime_path = args.runtime_dir / runtime_name
        frozen_path = args.reference_dir / frozen_name
        if not runtime_path.is_file() or not frozen_path.is_file():
            raise SystemExit("missing parity input: %s / %s" % (runtime_path, frozen_path))

        runtime = Image.open(runtime_path).convert("RGB")
        frozen = Image.open(frozen_path).convert("RGB").resize(runtime.size, Image.Resampling.LANCZOS)
        rois = ROI_RULES[kind]
        roi_results = []
        for name, box, minimum in rois:
            scores = roi_score(crop_norm(runtime, box), crop_norm(frozen, box))
            roi_results.append({"name": name, "minimum": minimum, "pass": scores["combined"] >= minimum, **scores})

        state_score = sum(item["combined"] for item in roi_results) / len(roi_results)
        state_minimum = STATE_THRESHOLDS[kind]
        # Every ROI has its own frozen minimum. A strong header must never hide
        # a weak status/content overlay behind the state average.
        passed = state_score >= state_minimum and all(item["pass"] for item in roi_results)

        focus = None
        if runtime_name in {"03_fish_located.png", "04_species_recognizing.png"}:
            focus_box = (0.08, 0.16, 0.92, 0.74)
            run_gold = categories(crop_norm(runtime, focus_box))["gold"]
            ref_gold = categories(crop_norm(frozen, focus_box))["gold"]
            focus_pass = run_gold >= max(0.0008, ref_gold * 0.10) and run_gold <= max(0.08, ref_gold * 8.0)
            focus = {
                "runtime_gold_fraction": round(run_gold, 5),
                "frozen_gold_fraction": round(ref_gold, 5),
                "pass": focus_pass,
            }
            passed = passed and focus_pass

        state = {
            "runtime": runtime_name,
            "frozen": frozen_name,
            "kind": kind,
            "score": round(state_score, 4),
            "minimum": state_minimum,
            "rois": roi_results,
            "focus": focus,
            "pass": passed,
        }
        result["states"].append(state)
        if not passed:
            result["classification"] = "FAIL"

        contact_rows.append({
            "name": runtime_name,
            "frozen": annotate(frozen, rois),
            "runtime": annotate(runtime, rois),
        })

    (args.output_dir / "recognition_visual_parity_v1_2.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    make_contact(contact_rows, args.output_dir / "recognition_visual_parity_contact_sheet_v1_2.png")

    print("RECOGNITION_VISUAL_PARITY classification=%s" % result["classification"])
    for state in result["states"]:
        print("PARITY_STATE %s score=%.4f min=%.2f pass=%s" % (
            state["runtime"], state["score"], state["minimum"], state["pass"],
        ))
    if result["classification"] != "PASS":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
