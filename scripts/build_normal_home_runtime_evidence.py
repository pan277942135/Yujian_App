#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageStat

def active_surface(image: Image.Image) -> Image.Image:
    rgb = image.convert("RGB")
    pixels = rgb.load()
    xs, ys = [], []
    step = max(1, min(rgb.width, rgb.height) // 320)
    for y in range(0, rgb.height, step):
        for x in range(0, rgb.width, step):
            r, g, b = pixels[x, y]
            if max(r, g, b) > 12:
                xs.append(x)
                ys.append(y)
    if not xs:
        raise SystemExit("NORMAL_HOME_ACTIVE_SURFACE_EMPTY")
    left, top = max(0, min(xs) - step), max(0, min(ys) - step)
    right, bottom = min(rgb.width, max(xs) + step + 1), min(rgb.height, max(ys) + step + 1)
    if rgb.width >= 2 * right - 12 and rgb.height >= 2 * bottom - 12:
        left, top, right, bottom = 0, 0, rgb.width // 2, rgb.height // 2
    surface = rgb.crop((left, top, right, bottom))
    if surface.width < 200 or surface.height < 350:
        raise SystemExit(f"NORMAL_HOME_ACTIVE_SURFACE_INVALID:{surface.size}")
    return surface

def edge_density(image: Image.Image) -> float:
    gray = image.convert("L").filter(ImageFilter.FIND_EDGES)
    hist = gray.histogram()
    total = max(1, gray.width * gray.height)
    return sum(hist[96:]) / total

def crop_norm(image: Image.Image, box):
    x, y, w, h = box
    return image.crop((
        int(image.width * x), int(image.height * y),
        int(image.width * (x + w)), int(image.height * (y + h)),
    ))

parser = argparse.ArgumentParser()
parser.add_argument("--reference", required=True)
parser.add_argument("--runtime", required=True)
parser.add_argument("--out-dir", required=True)
parser.add_argument("--build-sha", default="unknown")
args = parser.parse_args()
out = Path(args.out_dir)
out.mkdir(parents=True, exist_ok=True)
reference = Image.open(args.reference).convert("RGB")
runtime_raw = Image.open(args.runtime).convert("RGB")
runtime_surface = active_surface(runtime_raw)
runtime = runtime_surface.resize(reference.size, Image.Resampling.LANCZOS)
runtime.save(out / "runtime_normalized.png")

regions = {
    "header": (0.04, 0.02, 0.92, 0.15),
    "hero": (0.07, 0.15, 0.86, 0.50),
    "stats": (0.08, 0.63, 0.84, 0.13),
    "capture": (0.18, 0.75, 0.64, 0.23),
}
metrics = {}
for name, box in regions.items():
    ref_roi = crop_norm(reference, box)
    run_roi = crop_norm(runtime, box)
    ref_luma = ImageStat.Stat(ref_roi.convert("L")).mean[0]
    run_luma = ImageStat.Stat(run_roi.convert("L")).mean[0]
    ref_edge = edge_density(ref_roi)
    run_edge = edge_density(run_roi)
    metrics[name] = {
        "luma_delta": round(abs(ref_luma - run_luma), 4),
        "edge_density_delta": round(abs(ref_edge - run_edge), 6),
        "runtime_edge_density": round(run_edge, 6),
    }

low_ref = reference.resize((54, 96), Image.Resampling.LANCZOS).convert("L")
low_run = runtime.resize((54, 96), Image.Resampling.LANCZOS).convert("L")
low_frequency_mae = ImageStat.Stat(ImageChops.difference(low_ref, low_run)).mean[0]
hero_present = metrics["hero"]["runtime_edge_density"] > 0.025
structure_ok = all(value["edge_density_delta"] <= 0.20 for value in metrics.values())
brightness_ok = metrics["header"]["luma_delta"] <= 90 and metrics["capture"]["luma_delta"] <= 90
status = "PASS" if hero_present and structure_ok and brightness_ok and low_frequency_mae <= 95 else "FAIL"
report = {
    "status": status,
    "build_sha": args.build_sha,
    "authority": str(args.reference),
    "runtime_raw_size": list(runtime_raw.size),
    "runtime_surface_size": list(runtime_surface.size),
    "normalized_size": list(runtime.size),
    "low_frequency_mae": round(low_frequency_mae, 4),
    "metrics": metrics,
    "thresholds": {
        "edge_density_delta_max": 0.20,
        "header_capture_luma_delta_max": 90,
        "low_frequency_mae_max": 95,
        "hero_runtime_edge_density_min": 0.025,
    },
}
(out / "visual_parity_report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8"
)

side = Image.new("RGB", (reference.width * 2, reference.height), "white")
side.paste(reference, (0, 0))
side.paste(runtime, (reference.width, 0))
draw = ImageDraw.Draw(side)
draw.text((16, 16), "Frozen normal_home_v1", fill="black")
draw.text((reference.width + 16, 16), "Runtime", fill="black")
side.save(out / "frozen_runtime_side_by_side.png")
Image.blend(reference, runtime, 0.5).save(out / "frozen_runtime_blend.png")
print("NORMAL_HOME_VISUAL_PARITY", json.dumps(report, ensure_ascii=False))
if status != "PASS":
    raise SystemExit("NORMAL_HOME_VISUAL_PARITY_FAILED")
