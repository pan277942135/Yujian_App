#!/usr/bin/env python3
"""Create diagnostic image comparisons for captured evidence checkpoints.

This does not certify visual acceptance. It records the image provenance and keeps
photo-content/layout differences visible instead of silently normalizing them away.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter, ImageOps


CANVAS = (512, 1136)
BACKGROUND = (8, 20, 25)
ROIS = {
    "orb_and_energy": (0.25, 0.68, 0.75, 0.96),
    "lower_scene": (0.05, 0.65, 0.95, 0.98),
    "photo": (0.06, 0.08, 0.94, 0.73),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def fit(image: Image.Image) -> Image.Image:
    image = image.convert("RGB")
    contained = ImageOps.contain(image, CANVAS, method=Image.Resampling.LANCZOS)
    result = Image.new("RGB", CANVAS, BACKGROUND)
    result.paste(contained, ((CANVAS[0] - contained.width) // 2, (CANVAS[1] - contained.height) // 2))
    return result


def roi_box(image: Image.Image, fractions: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
    left, top, right, bottom = fractions
    return (
        round(left * image.width),
        round(top * image.height),
        round(right * image.width),
        round(bottom * image.height),
    )


def mean_abs_error(left: Image.Image, right: Image.Image) -> float:
    diff = ImageChops.difference(left.convert("RGB"), right.convert("RGB"))
    histogram = diff.histogram()
    total = sum(histogram)
    weighted = sum(index * count for index, count in enumerate(histogram))
    return weighted / max(1, total) / 255.0


def edge_error(left: Image.Image, right: Image.Image) -> float:
    left_edges = left.convert("L").filter(ImageFilter.FIND_EDGES)
    right_edges = right.convert("L").filter(ImageFilter.FIND_EDGES)
    left_binary = left_edges.point(lambda value: 255 if value >= 48 else 0)
    right_binary = right_edges.point(lambda value: 255 if value >= 48 else 0)
    return mean_abs_error(left_binary, right_binary)


def compare_pair(checkpoint: dict, root: Path, output: Path) -> dict | None:
    if checkpoint["runtimeStatus"] != "CAPTURED" or not checkpoint.get("capturePath"):
        return None
    actual_path = root / checkpoint["capturePath"]
    reference_path = root / checkpoint["referencePath"]
    if not actual_path.is_file() or not reference_path.is_file():
        return {
            "checkpointId": checkpoint["checkpointId"],
            "status": "INPUT_MISSING",
            "actualPath": checkpoint.get("capturePath"),
            "referencePath": checkpoint.get("referencePath"),
        }

    actual_source = Image.open(actual_path).convert("RGB")
    reference_source = Image.open(reference_path).convert("RGB")
    actual = fit(actual_source)
    reference = fit(reference_source)
    diff = ImageChops.difference(actual, reference)
    overlay = Image.blend(reference, actual, 0.5)
    heat_gray = ImageOps.grayscale(diff).point(lambda value: min(255, value * 3))
    heat = ImageOps.colorize(heat_gray, black="#07151B", mid="#22C9D0", white="#FFF28A")
    actual_edges = actual.convert("L").filter(ImageFilter.FIND_EDGES).point(lambda value: 255 if value >= 48 else 0)
    reference_edges = reference.convert("L").filter(ImageFilter.FIND_EDGES).point(lambda value: 255 if value >= 48 else 0)
    edges = Image.merge("RGB", (actual_edges, reference_edges, ImageChops.darker(actual_edges, reference_edges)))

    target = output / checkpoint["checkpointId"]
    target.mkdir(parents=True, exist_ok=True)
    files = {
        "reference": (reference, "reference.png"),
        "actual": (actual, "actual.png"),
        "overlay": (overlay, "overlay.png"),
        "difference": (diff, "difference.png"),
        "heatmap": (heat, "heatmap.png"),
        "edgeComparison": (edges, "edge_comparison.png"),
    }
    saved = {}
    for key, (image, name) in files.items():
        path = target / name
        image.save(path, optimize=True)
        saved[key] = str(path.relative_to(root))

    roi_metrics = {}
    for name, fractions in ROIS.items():
        box = roi_box(actual, fractions)
        actual_roi = actual.crop(box)
        reference_roi = reference.crop(box)
        roi_metrics[name] = {
            "normalizedBounds": list(fractions),
            "meanAbsolutePixelError01": round(mean_abs_error(actual_roi, reference_roi), 6),
            "binaryEdgeError01": round(edge_error(actual_roi, reference_roi), 6),
        }
        if name == "orb_and_energy":
            comparison = Image.new("RGB", (actual_roi.width * 2, actual_roi.height), BACKGROUND)
            comparison.paste(reference_roi, (0, 0))
            comparison.paste(actual_roi, (actual_roi.width, 0))
            roi_path = target / "orb_energy_roi_reference_actual.png"
            comparison.save(roi_path, optimize=True)
            saved["orbEnergyRoiComparison"] = str(roi_path.relative_to(root))

    return {
        "checkpointId": checkpoint["checkpointId"],
        "frozenStage": checkpoint["frozenStage"],
        "mappingStatus": checkpoint["mappingStatus"],
        "status": "DIAGNOSTIC_ONLY_NOT_ACCEPTANCE",
        "actualSource": checkpoint["runtimeEvidenceSource"],
        "actualPath": checkpoint["capturePath"],
        "actualSha256": sha256(actual_path),
        "actualDimensions": list(actual_source.size),
        "referencePath": checkpoint["referencePath"],
        "referenceSha256": sha256(reference_path),
        "referenceDimensions": list(reference_source.size),
        "alignedCanvas": list(CANVAS),
        "fullFrameMeanAbsolutePixelError01": round(mean_abs_error(actual, reference), 6),
        "fullFrameBinaryEdgeError01": round(edge_error(actual, reference), 6),
        "regions": roi_metrics,
        "outputs": saved,
        "caveat": "The Huawei capture and frozen frame use different source fish photos and aspect ratios. Scores are diagnostic only; they do not measure product acceptance or a post-fix build.",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("artifacts/26kf_visual_reference_v1"))
    args = parser.parse_args()
    root = args.root.resolve()
    status = json.loads((root / "26kf_runtime_capture_status.json").read_text(encoding="utf-8"))
    output = root / "derived/visual_comparison_eac685c9"
    output.mkdir(parents=True, exist_ok=True)
    comparisons = [
        result for row in status["checkpoints"]
        if (result := compare_pair(row, root, output)) is not None
    ]
    result = {
        "schemaVersion": 1,
        "fixtureId": status["fixtureId"],
        "inputSource": status["sourceType"],
        "currentHeadAcceptance": "NOT_RUN",
        "comparisonCount": len(comparisons),
        "comparisons": comparisons,
        "overallStatus": "DIAGNOSTIC_ONLY_NOT_ACCEPTANCE",
        "reason": "The evidence is from source SHA 67f17f0 and contains only six captured KF checkpoints. It cannot establish visual acceptance for the changed source or a simulator run.",
    }
    (output / "comparison_report.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    rows = [
        "# 26KF evidence comparison — EAC685C9",
        "",
        "These side-by-side, overlay, pixel-difference, heatmap, edge, and ROI images compare only the six archived Huawei captures with their mapped frozen references.",
        "",
        "The device evidence predates this source change (source SHA `67f17f0eb6a80233438cb8b00111a55d6767a6d8`). The captured fish photos and aspect ratios differ from the frozen reference. Scores are diagnostic and are not acceptance metrics.",
        "",
        "Current exact-HEAD API36 visual acceptance: `NOT_RUN`.",
        "",
    ]
    for row in comparisons:
        if row.get("status") != "DIAGNOSTIC_ONLY_NOT_ACCEPTANCE":
            rows.extend([f"## {row['checkpointId']}", "", f"Comparison unavailable: `{row.get('status')}`.", ""])
            continue
        rows.extend([
            f"## {row['checkpointId']} — {row['frozenStage']}",
            "",
            f"Mapping: `{row['mappingStatus']}`; actual SHA: `{row['actualSha256']}`; reference SHA: `{row['referenceSha256']}`.",
            f"Full-frame MAE: `{row['fullFrameMeanAbsolutePixelError01']}`; binary edge error: `{row['fullFrameBinaryEdgeError01']}`.",
            f"Orb/energy ROI MAE: `{row['regions']['orb_and_energy']['meanAbsolutePixelError01']}`; edge error: `{row['regions']['orb_and_energy']['binaryEdgeError01']}`.",
            f"Overlay: `{row['outputs']['overlay']}`; difference: `{row['outputs']['difference']}`; heatmap: `{row['outputs']['heatmap']}`; edge comparison: `{row['outputs']['edgeComparison']}`; Orb ROI: `{row['outputs']['orbEnergyRoiComparison']}`.",
            "",
        ])
    (output / "comparison_report.md").write_text("\n".join(rows), encoding="utf-8")
    print(json.dumps({"comparisonCount": len(comparisons), "output": str(output), "status": result["overallStatus"]}))


if __name__ == "__main__":
    main()
